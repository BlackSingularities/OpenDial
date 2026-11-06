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

/** Grupy systemowe pomijane w formularzu: „Moje kontakty” (dodawane automatycznie) i „Oznaczone gwiazdką”. */
private val HIDDEN_SYSTEM_GROUPS = setOf("Contacts", "Starred")

/** Kolumny tabeli Data czytane przy szczegółach i formularzu: MIMETYPE, DATA1…DATA10, surowy kontakt, domyślność. */
internal val dataProjection = arrayOf(
    Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
    Data.RAW_CONTACT_ID, Data.IS_SUPER_PRIMARY, Data.IS_PRIMARY,
)

internal fun android.database.Cursor.str(i: Int): String = getString(i)?.trim().orEmpty()

/** Adres z wiersza StructuredPostal (kolumny jak w [dataProjection]). */
internal fun android.database.Cursor.postal(): PostalField {
    val p = PostalField(
        street = str(4).let { s -> listOf(s, str(5).let { if (it.isNotEmpty()) tr("skr. poczt. {0}", it) else "" }).filter { it.isNotEmpty() }.joinToString("\n") },
        postcode = str(9),
        city = str(7),
        region = str(8),
        country = str(10),
        type = getInt(2),
        label = getString(3),
    )
    // Adres zapisany tylko jako tekst (np. przez inną aplikację) – trafia do pola ulicy.
    return if (p.isBlank && str(1).isNotEmpty()) p.copy(street = str(1)) else p
}

/** Odczyt kontaktu z ContactsContract: szczegóły do widoku, grupy konta i surowe kontakty. */
internal class ContactReader(private val repo: ContactsRepo) {
    private val cr get() = repo.resolver
    private val sim get() = repo.sim

    fun details(id: Long): ContactDetails? {
        var lookup = ""
        var name = ""
        var photo: String? = null
        var starred = false
        var ringtone: String? = null
        var voicemail = false
        cr.query(
            ContentUris.withAppendedId(Contacts.CONTENT_URI, id),
            arrayOf(Contacts.LOOKUP_KEY, Contacts.DISPLAY_NAME_PRIMARY, Contacts.PHOTO_URI, Contacts.STARRED, Contacts.CUSTOM_RINGTONE, Contacts.SEND_TO_VOICEMAIL),
            null, null, null,
        )?.use { c ->
            if (!c.moveToFirst()) return null
            lookup = c.getString(0) ?: ""
            name = c.getString(1) ?: ""
            photo = c.getString(2)
            starred = c.getInt(3) != 0
            ringtone = c.getString(4)
            voicemail = c.getInt(5) != 0
        } ?: return null

        val phones = ArrayList<PhoneNumber>()
        val emails = ArrayList<LabeledValue>()
        val addresses = ArrayList<LabeledValue>()
        val websites = ArrayList<LabeledValue>()
        val events = ArrayList<LabeledValue>()
        val relations = ArrayList<LabeledValue>()
        val ims = ArrayList<LabeledValue>()
        val sips = ArrayList<LabeledValue>()
        val groupIds = LinkedHashSet<Long>()
        var org: String? = null
        var title: String? = null
        var department: String? = null
        var jobDescription: String? = null
        var phonetic: String? = null
        var nick: String? = null
        var note: String? = null
        var birthday: String? = null
        val raw = LinkedHashSet<Long>()
        cr.query(Data.CONTENT_URI, dataProjection, "${Data.CONTACT_ID}=?", arrayOf(id.toString()), null)?.use { c ->
            while (c.moveToNext()) {
                raw.add(c.getLong(11))
                val mime = c.getString(0)
                if (mime == GroupMembership.CONTENT_ITEM_TYPE) {
                    c.getString(1)?.toLongOrNull()?.let { groupIds.add(it) }
                    continue
                }
                if (mime == StructuredName.CONTENT_ITEM_TYPE) {
                    if (phonetic == null) phonetic = listOf(c.str(7), c.str(8), c.str(9)).filter { it.isNotEmpty() }.joinToString(" ").ifEmpty { null }
                    continue
                }
                if (mime == StructuredPostal.CONTENT_ITEM_TYPE) {
                    val p = c.postal()
                    if (!p.isBlank && addresses.none { it.value.equals(p.formatted, true) }) addresses.add(LabeledValue(p.formatted, p.type, p.label))
                    continue
                }
                val v = c.getString(1)?.trim()?.takeIf { it.isNotBlank() } ?: continue
                val type = c.getInt(2)
                val label = c.getString(3)
                val primary = c.getInt(12) != 0
                when (mime) {
                    Phone.CONTENT_ITEM_TYPE -> if (phones.none { PhoneText.sameNumber(it.number, v) }) phones.add(PhoneNumber(v, type, label, primary))
                    Email.CONTENT_ITEM_TYPE -> if (emails.none { it.value.equals(v, true) }) emails.add(LabeledValue(v, type, label, primary))
                    Website.CONTENT_ITEM_TYPE -> if (websites.none { it.value == v }) websites.add(LabeledValue(v, type, label))
                    Organization.CONTENT_ITEM_TYPE -> if (org == null) {
                        org = v
                        title = c.getString(4)?.takeIf { it.isNotBlank() }
                        department = c.getString(5)?.takeIf { it.isNotBlank() }
                        jobDescription = c.getString(6)?.takeIf { it.isNotBlank() }
                    }
                    Nickname.CONTENT_ITEM_TYPE -> if (nick == null) nick = v
                    Note.CONTENT_ITEM_TYPE -> if (note == null) note = v
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) {
                        if (birthday == null) birthday = v
                    } else if (events.none { it.value == v && it.type == type }) {
                        events.add(LabeledValue(v, type, label))
                    }
                    Relation.CONTENT_ITEM_TYPE -> if (relations.none { it.value == v && it.type == type }) relations.add(LabeledValue(v, type, label))
                    Im.CONTENT_ITEM_TYPE -> if (ims.none { it.value == v }) ims.add(LabeledValue(v, c.getString(5)?.toIntOrNull() ?: ContactKinds.IM_CUSTOM, c.getString(6)))
                    SipAddress.CONTENT_ITEM_TYPE -> if (sips.none { it.value == v }) sips.add(LabeledValue(v, type, label))
                }
            }
        }
        val accounts = LinkedHashSet<String>()
        val accountRefs = LinkedHashSet<AccountRef>()
        if (raw.isNotEmpty()) {
            val cards = runCatching { sim.cards() }.getOrDefault(emptyList())
            cr.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts._ID} IN (${raw.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) {
                    accounts.add(c.getString(0) ?: tr("Telefon"))
                    accountRefs.add(accountRef(c.getString(1), c.getString(0), cards))
                }
            }
        }
        return ContactDetails(
            id, lookup, name, photo, starred, phones, emails, addresses, websites, org, title, nick, note, birthday, events,
            raw.toList(), accounts.toList(), ringtone, voicemail,
            department = department,
            jobDescription = jobDescription,
            phoneticName = phonetic,
            relations = relations,
            ims = ims,
            sip = sips,
            groups = groupTitles(groupIds),
            accountRefs = accountRefs.toList(),
        )
    }

    /** Konto surowego kontaktu jako [AccountRef]; konto karty SIM dostaje numer subskrypcji i gniazda. */
    fun accountRef(type: String?, name: String?, cards: List<SimRepo.Card>): AccountRef {
        if (type != null && name != null && sim.isSimAccount(type, name, cards)) {
            val card = cards.firstOrNull { it.accountType == type && it.accountName == name } ?: cards.singleOrNull()
            return AccountRef(type, name, simSubId = card?.subscriptionId ?: -1, simSlot = card?.slot ?: 0)
        }
        return AccountRef(type, name)
    }

    /** Nazwy grup (bez grup systemowych typu „Moje kontakty” i „Oznaczone gwiazdką”). */
    private fun groupTitles(ids: Collection<Long>): List<String> {
        if (ids.isEmpty()) return emptyList()
        val out = ArrayList<String>()
        cr.query(
            Groups.CONTENT_URI, arrayOf(Groups.TITLE, Groups.SYSTEM_ID, Groups.AUTO_ADD),
            "${Groups._ID} IN (${ids.joinToString(",")}) AND ${Groups.DELETED}=0", null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val system = c.getString(1)
                if (c.getInt(2) != 0 || system in HIDDEN_SYSTEM_GROUPS) continue
                groupTitle(c.getString(0), system)?.let { if (it !in out) out.add(it) }
            }
        }
        return out
    }

    private fun groupTitle(title: String?, systemId: String?): String? = when (systemId) {
        "Friends" -> tr("Przyjaciele")
        "Family" -> tr("Rodzina")
        "Coworkers" -> tr("Współpracownicy")
        else -> title?.removePrefix("System Group: ")?.takeIf { it.isNotBlank() }
    }

    /** Grupy konta, do których można przypisać kontakt. */
    fun groups(account: AccountRef?): List<GroupRef> {
        if (account?.sim == true) return emptyList()
        val (sel, args) = if (account == null || account.local) {
            "${Groups.ACCOUNT_TYPE} IS NULL AND ${Groups.ACCOUNT_NAME} IS NULL" to emptyArray<String>()
        } else {
            "${Groups.ACCOUNT_TYPE}=? AND ${Groups.ACCOUNT_NAME}=?" to arrayOf(account.type!!, account.name!!)
        }
        val out = ArrayList<GroupRef>()
        runCatching {
            cr.query(
                Groups.CONTENT_URI, arrayOf(Groups._ID, Groups.TITLE, Groups.SYSTEM_ID, Groups.AUTO_ADD),
                "$sel AND ${Groups.DELETED}=0", args, "${Groups.TITLE} COLLATE LOCALIZED ASC",
            )?.use { c ->
                while (c.moveToNext()) {
                    val system = c.getString(2)
                    if (c.getInt(3) != 0 || system in HIDDEN_SYSTEM_GROUPS) continue
                    val t = groupTitle(c.getString(1), system) ?: continue
                    out.add(GroupRef(c.getLong(0), t))
                }
            }
        }
        return out
    }

    /** Surowe kontakty składające się na kontakt (np. Google + pamięć telefonu + WhatsApp). */
    fun rawContacts(contactId: Long): List<RawRef> {
        val cards = runCatching { sim.cards() }.getOrDefault(emptyList())
        val out = ArrayList<RawRef>()
        cr.query(
            RawContacts.CONTENT_URI,
            arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME, RawContacts.DISPLAY_NAME_PRIMARY),
            "${RawContacts.CONTACT_ID}=? AND ${RawContacts.DELETED}=0", arrayOf(contactId.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) out.add(RawRef(c.getLong(0), accountRef(c.getString(1), c.getString(2), cards), c.getString(3).orEmpty()))
        }
        return out
    }
}
