@file:Suppress("DEPRECATION") // Im.PROTOCOL_* – nadal jedyny sposób zapisu komunikatorów w ContactsContract.

package com.blacksingularities.opencall.data

import android.content.ContentProviderOperation
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.provider.ContactsContract
import android.provider.ContactsContract.CommonDataKinds.Email
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.GroupMembership
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.Groups
import android.provider.ContactsContract.RawContacts
import com.blacksingularities.opencall.i18n.tr
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Zapis kontaktu: formularz z surowego kontaktu, zapis pól obsługiwanych przez formularz,
 * przenoszenie do innego konta i nowe grupy.
 */
internal class ContactWriter(private val repo: ContactsRepo, private val reader: ContactReader) {
    private val cr get() = repo.resolver
    private val sim get() = repo.sim

    private fun details(id: Long) = reader.details(id)
    private fun groups(account: AccountRef?) = reader.groups(account)
    private fun rawContacts(contactId: Long) = reader.rawContacts(contactId)
    private fun accountOf(rawId: Long) = repo.accountOf(rawId)
    private fun simKeyOf(rawId: Long) = repo.simKeyOf(rawId)

    /** Nowa grupa w koncie; zwraca jej id. */
    fun createGroup(account: AccountRef?, title: String): Long? = runCatching {
        val values = ContentValues().apply {
            put(Groups.TITLE, title.trim())
            put(Groups.GROUP_VISIBLE, 1)
            if (account != null && !account.local && !account.sim) {
                put(Groups.ACCOUNT_TYPE, account.type)
                put(Groups.ACCOUNT_NAME, account.name)
            }
        }
        cr.insert(Groups.CONTENT_URI, values)?.let { ContentUris.parseId(it) }
    }.getOrNull()

    /** Formularz edycji z istniejącego kontaktu (pierwszy surowy kontakt z zapisywalnego konta). */
    fun draft(id: Long): ContactDraft? {
        val d = details(id) ?: return null
        val rawId = d.rawContactIds.firstOrNull() ?: return null
        // Kontakt z karty SIM: edycja tylko nazwy i numeru, zapis na kartę.
        val cards = sim.cards()
        d.rawContactIds.firstNotNullOfOrNull { r ->
            val (type, name) = accountOf(r) ?: return@firstNotNullOfOrNull null
            if (!sim.isSimAccount(type, name, cards)) return@firstNotNullOfOrNull null
            val card = cards.firstOrNull { it.accountType == type && it.accountName == name } ?: cards.singleOrNull()
                ?: return@firstNotNullOfOrNull null
            val (simName, simNumber) = simKeyOf(r)
            ContactDraft(
                id = id,
                rawContactId = r,
                given = simName,
                phones = listOf(DraftField(simNumber, Phone.TYPE_MOBILE)),
                account = AccountRef(type, name, simSubId = card.subscriptionId, simSlot = card.slot),
                simOriginal = simName to simNumber,
            )
        }?.let { return it }
        val account = accountOf(rawId)?.let { (t, n) -> AccountRef(t, n) } ?: AccountRef(null, null)
        var draft = ContactDraft(
            id = id,
            rawContactId = rawId,
            account = account,
            originalAccount = account,
            starred = d.starred,
            ringtone = d.customRingtone,
            sendToVoicemail = d.sendToVoicemail,
        )
        cr.query(Data.CONTENT_URI, dataProjection, "${Data.RAW_CONTACT_ID}=?", arrayOf(rawId.toString()), null)?.use { c ->
            val phones = ArrayList<DraftField>()
            val emails = ArrayList<DraftField>()
            val addresses = ArrayList<PostalField>()
            val sites = ArrayList<DraftField>()
            val events = ArrayList<DraftField>()
            val relations = ArrayList<DraftField>()
            val ims = ArrayList<DraftField>()
            val sips = ArrayList<DraftField>()
            val memberOf = LinkedHashSet<Long>()
            while (c.moveToNext()) {
                val v = c.getString(1) ?: ""
                val type = c.getInt(2)
                val label = c.getString(3)
                val primary = c.getInt(12) != 0 || c.getInt(13) != 0
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> draft = draft.copy(
                        given = c.str(2),
                        family = c.str(3),
                        prefix = c.str(4),
                        middle = c.str(5),
                        suffix = c.str(6),
                        phoneticGiven = c.str(7),
                        phoneticMiddle = c.str(8),
                        phoneticFamily = c.str(9),
                    ).let { if (it.displayName.isBlank() && v.isNotBlank()) it.copy(given = v) else it }
                    Phone.CONTENT_ITEM_TYPE -> phones.add(DraftField(v, type, label, primary))
                    Email.CONTENT_ITEM_TYPE -> emails.add(DraftField(v, type, label, primary))
                    StructuredPostal.CONTENT_ITEM_TYPE -> c.postal().takeIf { !it.isBlank }?.let { addresses.add(it) }
                    Website.CONTENT_ITEM_TYPE -> sites.add(DraftField(v, type, label))
                    Organization.CONTENT_ITEM_TYPE -> draft = draft.copy(
                        organization = v,
                        title = c.str(4),
                        department = c.str(5),
                        jobDescription = c.str(6),
                    )
                    Nickname.CONTENT_ITEM_TYPE -> draft = draft.copy(nickname = v)
                    Note.CONTENT_ITEM_TYPE -> draft = draft.copy(note = v)
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) draft = draft.copy(birthday = v) else events.add(DraftField(v, type, label))
                    Relation.CONTENT_ITEM_TYPE -> relations.add(DraftField(v, type, label))
                    Im.CONTENT_ITEM_TYPE -> ims.add(DraftField(v, c.getString(5)?.toIntOrNull() ?: ContactKinds.IM_CUSTOM, c.getString(6)))
                    SipAddress.CONTENT_ITEM_TYPE -> sips.add(DraftField(v, type, label))
                    GroupMembership.CONTENT_ITEM_TYPE -> v.toLongOrNull()?.let { memberOf.add(it) }
                }
            }
            // Tylko grupy pokazywane w formularzu – członkostwa w grupach systemowych zostają nietknięte.
            val editable = groups(account).map { it.id }.toSet()
            val shownGroups = memberOf.filter { it in editable }.toSet()
            draft = draft.copy(
                phones = phones.ifEmpty { listOf(DraftField("", Phone.TYPE_MOBILE)) },
                emails = emails,
                addresses = addresses,
                websites = sites,
                events = events,
                relations = relations,
                ims = ims,
                sip = sips,
                groups = shownGroups,
                originalGroups = shownGroups,
            )
        }
        return draft
    }

    /**
     * Typy danych, które formularz odczytuje w całości i zapisuje od nowa. Wszystko inne w surowym
     * kontakcie (np. wpisy komunikatorów dodane przez ich aplikacje, własne typy innych programów)
     * zostaje nietknięte. Grupy są zmieniane różnicowo, a zdjęcie tylko po zmianie.
     */
    private val managedTypes = listOf(
        StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
        Website.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
        Event.CONTENT_ITEM_TYPE, Relation.CONTENT_ITEM_TYPE, Im.CONTENT_ITEM_TYPE, SipAddress.CONTENT_ITEM_TYPE,
    )

    /** Zapis kontaktu: nowy surowy kontakt, podmiana pól zarządzanych przez formularz albo przeniesienie do innego konta. Zwraca id kontaktu. */
    fun save(d: ContactDraft): Long? {
        if (d.moving) return move(d)
        val (_, contactId) = write(d)
        if (contactId != null) applyOptions(contactId, d)
        repo.clearCache()
        return contactId
    }

    /** Ulubiony, dzwonek i poczta głosowa z formularza – to kolumny kontaktu, nie surowego kontaktu. */
    private fun applyOptions(contactId: Long, d: ContactDraft) {
        runCatching {
            cr.update(
                ContentUris.withAppendedId(Contacts.CONTENT_URI, contactId),
                ContentValues().apply {
                    put(Contacts.STARRED, if (d.starred) 1 else 0)
                    put(Contacts.CUSTOM_RINGTONE, d.ringtone)
                    put(Contacts.SEND_TO_VOICEMAIL, if (d.sendToVoicemail) 1 else 0)
                },
                null, null,
            )
        }
    }

    /**
     * Przeniesienie do innego konta: nowy surowy kontakt w docelowym koncie z danymi formularza
     * (i dotychczasowym zdjęciem), potem usunięcie starego. Pozostałe surowe kontakty (np. WhatsApp)
     * zostają połączone z nowym.
     */
    private fun move(d: ContactDraft): Long? {
        val oldRaw = d.rawContactId ?: return null
        val others = d.id?.let { id -> rawContacts(id).map { it.id }.filter { it != oldRaw } }.orEmpty()
        val photo = if (d.photoChanged) d.photo else photoBytes(oldRaw)
        val (newRaw, contactId) = write(
            d.copy(
                id = null, rawContactId = null, originalAccount = d.account,
                originalGroups = emptySet(), photo = photo, photoChanged = photo != null,
            ),
        )
        if (newRaw == null) return null
        cr.delete(ContentUris.withAppendedId(RawContacts.CONTENT_URI, oldRaw), null, null)
        if (others.isNotEmpty()) {
            val ops = ArrayList<ContentProviderOperation>()
            others.forEach { other ->
                ops.add(
                    ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                        .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, newRaw)
                        .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, other)
                        .build(),
                )
            }
            runCatching { cr.applyBatch(ContactsContract.AUTHORITY, ops) }
        }
        // Po połączeniu kontakt mógł dostać nowe id.
        val id = contactOfRaw(newRaw) ?: contactId
        if (id != null) applyOptions(id, d)
        repo.clearCache()
        return id
    }

    private fun contactOfRaw(rawId: Long): Long? =
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(rawId.toString()), null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0).takeIf { it > 0 } else null }

    /** Pełne zdjęcie surowego kontaktu (DisplayPhoto), a gdy go nie ma – miniatura z tabeli Data. */
    private fun photoBytes(rawId: Long): ByteArray? = runCatching {
        val display = Uri.withAppendedPath(ContentUris.withAppendedId(RawContacts.CONTENT_URI, rawId), RawContacts.DisplayPhoto.CONTENT_DIRECTORY)
        runCatching { cr.openAssetFileDescriptor(display, "r")?.use { it.createInputStream().readBytes() } }.getOrNull()
            ?: cr.query(Data.CONTENT_URI, arrayOf(Photo.PHOTO), "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=?", arrayOf(rawId.toString(), Photo.CONTENT_ITEM_TYPE), null)
                ?.use { c -> if (c.moveToFirst()) c.getBlob(0) else null }
    }.getOrNull()

    /** Zapis pól formularza do surowego kontaktu (nowego albo istniejącego). Zwraca (surowy kontakt, kontakt). */
    private fun write(d: ContactDraft): Pair<Long?, Long?> {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = d.rawContactId
        fun dataInsert(): ContentProviderOperation.Builder = if (rawId == null) {
            ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
        } else {
            ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.RAW_CONTACT_ID, rawId)
        }
        fun ContentProviderOperation.Builder.primary(on: Boolean) = if (on) withValue(Data.IS_PRIMARY, 1).withValue(Data.IS_SUPER_PRIMARY, 1) else this
        fun String.orNull() = trim().ifEmpty { null }
        fun labelOf(f: DraftField) = if (f.type == ContactKinds.CUSTOM) f.label?.orNull() else null

        if (rawId == null) {
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, d.account?.type)
                    .withValue(RawContacts.ACCOUNT_NAME, d.account?.name)
                    .build(),
            )
        } else {
            val managed = managedTypes + (if (d.photoChanged) listOf(Photo.CONTENT_ITEM_TYPE) else emptyList())
            ops.add(
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (${managed.joinToString(",") { "?" }})",
                        arrayOf(rawId.toString()) + managed,
                    ).build(),
            )
            val removedGroups = d.originalGroups - d.groups
            if (removedGroups.isNotEmpty()) {
                ops.add(
                    ContentProviderOperation.newDelete(Data.CONTENT_URI)
                        .withSelection(
                            "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${GroupMembership.GROUP_ROW_ID} IN (${removedGroups.joinToString(",")})",
                            arrayOf(rawId.toString(), GroupMembership.CONTENT_ITEM_TYPE),
                        ).build(),
                )
            }
        }
        ops.add(
            dataInsert()
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, d.given.orNull())
                .withValue(StructuredName.FAMILY_NAME, d.family.orNull())
                .withValue(StructuredName.MIDDLE_NAME, d.middle.orNull())
                .withValue(StructuredName.PREFIX, d.prefix.orNull())
                .withValue(StructuredName.SUFFIX, d.suffix.orNull())
                .withValue(StructuredName.PHONETIC_GIVEN_NAME, d.phoneticGiven.orNull())
                .withValue(StructuredName.PHONETIC_MIDDLE_NAME, d.phoneticMiddle.orNull())
                .withValue(StructuredName.PHONETIC_FAMILY_NAME, d.phoneticFamily.orNull())
                .build(),
        )
        d.phones.filter { it.value.isNotBlank() }.forEach {
            ops.add(
                dataInsert().withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it.value.trim())
                    .withValue(Phone.TYPE, it.type).withValue(Phone.LABEL, labelOf(it)).primary(it.primary).build(),
            )
        }
        d.emails.filter { it.value.isNotBlank() }.forEach {
            ops.add(
                dataInsert().withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, it.value.trim())
                    .withValue(Email.TYPE, it.type).withValue(Email.LABEL, labelOf(it)).primary(it.primary).build(),
            )
        }
        d.addresses.filter { !it.isBlank }.forEach {
            ops.add(
                dataInsert().withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE)
                    .withValue(StructuredPostal.FORMATTED_ADDRESS, it.formatted)
                    .withValue(StructuredPostal.STREET, it.street.orNull())
                    .withValue(StructuredPostal.POSTCODE, it.postcode.orNull())
                    .withValue(StructuredPostal.CITY, it.city.orNull())
                    .withValue(StructuredPostal.REGION, it.region.orNull())
                    .withValue(StructuredPostal.COUNTRY, it.country.orNull())
                    .withValue(StructuredPostal.TYPE, it.type)
                    .withValue(StructuredPostal.LABEL, if (it.type == ContactKinds.CUSTOM) it.label?.orNull() else null)
                    .build(),
            )
        }
        d.websites.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE).withValue(Website.URL, it.value.trim()).withValue(Website.TYPE, it.type).withValue(Website.LABEL, labelOf(it)).build())
        }
        if (listOf(d.organization, d.title, d.department, d.jobDescription).any { it.isNotBlank() }) {
            ops.add(
                dataInsert().withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE)
                    .withValue(Organization.COMPANY, d.organization.orNull())
                    .withValue(Organization.TITLE, d.title.orNull())
                    .withValue(Organization.DEPARTMENT, d.department.orNull())
                    .withValue(Organization.JOB_DESCRIPTION, d.jobDescription.orNull())
                    .build(),
            )
        }
        if (d.nickname.isNotBlank()) ops.add(dataInsert().withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE).withValue(Nickname.NAME, d.nickname.trim()).build())
        if (d.note.isNotBlank()) ops.add(dataInsert().withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note.trim()).build())
        if (d.birthday.isNotBlank()) {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE).withValue(Event.START_DATE, d.birthday.trim()).withValue(Event.TYPE, Event.TYPE_BIRTHDAY).build())
        }
        d.events.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE).withValue(Event.START_DATE, it.value.trim()).withValue(Event.TYPE, it.type).withValue(Event.LABEL, labelOf(it)).build())
        }
        d.relations.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Relation.CONTENT_ITEM_TYPE).withValue(Relation.NAME, it.value.trim()).withValue(Relation.TYPE, it.type).withValue(Relation.LABEL, labelOf(it)).build())
        }
        d.ims.filter { it.value.isNotBlank() }.forEach {
            ops.add(
                dataInsert().withValue(Data.MIMETYPE, Im.CONTENT_ITEM_TYPE).withValue(Im.DATA, it.value.trim())
                    .withValue(Im.TYPE, Im.TYPE_OTHER)
                    .withValue(Im.PROTOCOL, it.type)
                    .withValue(Im.CUSTOM_PROTOCOL, if (it.type == ContactKinds.IM_CUSTOM) it.label?.orNull() else null)
                    .build(),
            )
        }
        d.sip.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, SipAddress.CONTENT_ITEM_TYPE).withValue(SipAddress.SIP_ADDRESS, it.value.trim().removePrefix("sip:")).withValue(SipAddress.TYPE, it.type).withValue(SipAddress.LABEL, labelOf(it)).build())
        }
        (d.groups - d.originalGroups).forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, GroupMembership.CONTENT_ITEM_TYPE).withValue(GroupMembership.GROUP_ROW_ID, it).build())
        }
        if (d.photoChanged && d.photo != null) {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE).withValue(Photo.PHOTO, d.photo).withValue(Data.IS_SUPER_PRIMARY, 1).build())
        }
        val res = cr.applyBatch(ContactsContract.AUTHORITY, ops)
        if (rawId != null) return rawId to d.id
        val newRaw = res.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null to null
        return newRaw to contactOfRaw(newRaw)
    }
}
