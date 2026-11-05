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
import android.provider.ContactsContract.CommonDataKinds.Nickname
import android.provider.ContactsContract.CommonDataKinds.Note
import android.provider.ContactsContract.CommonDataKinds.Organization
import android.provider.ContactsContract.CommonDataKinds.Phone
import android.provider.ContactsContract.CommonDataKinds.Photo
import android.provider.ContactsContract.CommonDataKinds.StructuredName
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website
import android.provider.ContactsContract.Contacts
import android.provider.ContactsContract.Data
import android.provider.ContactsContract.RawContacts
import com.blacksingularities.opencall.i18n.tr
import java.io.ByteArrayOutputStream
import java.util.concurrent.ConcurrentHashMap

/** Kontakty z ContactsContract: lista, szczegóły, edycja, ulubione, duplikaty i scalanie. */
class ContactsRepo(private val context: Context) {
    private val cr get() = context.contentResolver

    /** Pamięć podręczna numer → (nazwa, zdjęcie) dla połączeń przychodzących i rejestru. */
    private val lookupCache = ConcurrentHashMap<String, CallerId>()

    data class CallerId(val name: String?, val photoUri: String?, val contactId: Long?, val lookupKey: String?, val label: String?)

    fun loadAll(order: NameOrder): List<Contact> {
        val numbers = HashMap<Long, MutableList<PhoneNumber>>()
        cr.query(
            Phone.CONTENT_URI,
            arrayOf(Phone.CONTACT_ID, Phone.NUMBER, Phone.TYPE, Phone.LABEL, Phone.IS_SUPER_PRIMARY),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val num = c.getString(1) ?: continue
                val list = numbers.getOrPut(id) { ArrayList() }
                // Ten sam numer zapisany kilka razy (np. w dwóch kontach) – pokazujemy raz.
                if (list.none { PhoneText.sameNumber(it.number, num) }) {
                    list.add(PhoneNumber(num, c.getInt(2), c.getString(3), c.getInt(4) != 0))
                }
            }
        }
        val nameCol = if (order == NameOrder.LAST_FIRST) Contacts.DISPLAY_NAME_ALTERNATIVE else Contacts.DISPLAY_NAME_PRIMARY
        val sortCol = if (order == NameOrder.LAST_FIRST) Contacts.SORT_KEY_ALTERNATIVE else Contacts.SORT_KEY_PRIMARY
        val out = ArrayList<Contact>()
        cr.query(
            Contacts.CONTENT_URI,
            arrayOf(Contacts._ID, Contacts.LOOKUP_KEY, nameCol, Contacts.PHOTO_THUMBNAIL_URI, Contacts.STARRED),
            null, null, "$sortCol COLLATE LOCALIZED ASC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val name = c.getString(2)?.takeIf { it.isNotBlank() } ?: numbers[id]?.firstOrNull()?.number ?: continue
                out.add(Contact(id, c.getString(1) ?: "", name, c.getString(3), c.getInt(4) != 0, numbers[id].orEmpty()))
            }
        }
        lookupCache.clear()
        return out
    }

    /** Kto dzwoni: PhoneLookup (dopasowanie numeru jak w systemie), z pamięcią podręczną. */
    fun lookup(number: String?): CallerId? {
        if (number.isNullOrBlank()) return null
        val key = PhoneText.key(number)
        lookupCache[key]?.let { return it.takeIf { c -> c.contactId != null } }
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        val res = runCatching {
            cr.query(
                uri,
                arrayOf(
                    ContactsContract.PhoneLookup._ID, ContactsContract.PhoneLookup.DISPLAY_NAME,
                    ContactsContract.PhoneLookup.PHOTO_URI, ContactsContract.PhoneLookup.LOOKUP_KEY,
                    ContactsContract.PhoneLookup.TYPE, ContactsContract.PhoneLookup.LABEL,
                ),
                null, null, null,
            )?.use { c ->
                if (c.moveToFirst()) {
                    val label = Phone.getTypeLabel(context.resources, c.getInt(4), c.getString(5)).toString()
                    CallerId(c.getString(1), c.getString(2), c.getLong(0), c.getString(3), label)
                } else {
                    null
                }
            }
        }.getOrNull()
        lookupCache[key] = res ?: CallerId(null, null, null, null, null)
        return res
    }

    fun clearCache() = lookupCache.clear()

    fun idForLookupUri(uri: Uri): Long? = runCatching {
        val resolved = Contacts.lookupContact(cr, uri) ?: uri
        cr.query(resolved, arrayOf(Contacts._ID), null, null, null)?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }.getOrNull()

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
        val websites = ArrayList<String>()
        val events = ArrayList<LabeledValue>()
        var org: String? = null
        var title: String? = null
        var nick: String? = null
        var note: String? = null
        var birthday: String? = null
        val raw = LinkedHashSet<Long>()
        cr.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.RAW_CONTACT_ID, Data.IS_SUPER_PRIMARY),
            "${Data.CONTACT_ID}=?", arrayOf(id.toString()), null,
        )?.use { c ->
            while (c.moveToNext()) {
                raw.add(c.getLong(5))
                val v = c.getString(1)?.takeIf { it.isNotBlank() } ?: continue
                val type = c.getInt(2)
                val label = c.getString(3)
                when (c.getString(0)) {
                    Phone.CONTENT_ITEM_TYPE -> if (phones.none { PhoneText.sameNumber(it.number, v) }) phones.add(PhoneNumber(v, type, label, c.getInt(6) != 0))
                    Email.CONTENT_ITEM_TYPE -> if (emails.none { it.value.equals(v, true) }) emails.add(LabeledValue(v, type, label))
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses.add(LabeledValue(v, type, label))
                    Website.CONTENT_ITEM_TYPE -> websites.add(v)
                    Organization.CONTENT_ITEM_TYPE -> {
                        org = v
                        title = c.getString(4)
                    }
                    Nickname.CONTENT_ITEM_TYPE -> nick = v
                    Note.CONTENT_ITEM_TYPE -> note = v
                    Event.CONTENT_ITEM_TYPE -> if (type == Event.TYPE_BIRTHDAY) birthday = v else events.add(LabeledValue(v, type, label))
                }
            }
        }
        val accounts = LinkedHashSet<String>()
        if (raw.isNotEmpty()) {
            cr.query(
                RawContacts.CONTENT_URI,
                arrayOf(RawContacts.ACCOUNT_NAME, RawContacts.ACCOUNT_TYPE),
                "${RawContacts._ID} IN (${raw.joinToString(",")})", null, null,
            )?.use { c ->
                while (c.moveToNext()) accounts.add(c.getString(0) ?: tr("Telefon"))
            }
        }
        return ContactDetails(
            id, lookup, name, photo, starred, phones, emails, addresses, websites, org, title, nick, note, birthday, events,
            raw.toList(), accounts.toList(), ringtone, voicemail,
        )
    }

    fun setStarred(id: Long, starred: Boolean) {
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), ContentValues().apply { put(Contacts.STARRED, if (starred) 1 else 0) }, null, null)
    }

    fun setSendToVoicemail(id: Long, on: Boolean) {
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), ContentValues().apply { put(Contacts.SEND_TO_VOICEMAIL, if (on) 1 else 0) }, null, null)
    }

    fun setRingtone(id: Long, uri: String?) {
        cr.update(ContentUris.withAppendedId(Contacts.CONTENT_URI, id), ContentValues().apply { put(Contacts.CUSTOM_RINGTONE, uri) }, null, null)
    }

    /** Ustawia numer jako domyślny dla kontaktu (używany przy szybkim dzwonieniu). */
    fun setPrimaryNumber(contactId: Long, number: String) {
        cr.query(Phone.CONTENT_URI, arrayOf(Phone._ID, Phone.NUMBER), "${Phone.CONTACT_ID}=?", arrayOf(contactId.toString()), null)?.use { c ->
            val ops = ArrayList<ContentProviderOperation>()
            while (c.moveToNext()) {
                val primary = PhoneText.sameNumber(c.getString(1) ?: "", number)
                ops.add(
                    ContentProviderOperation.newUpdate(ContentUris.withAppendedId(Data.CONTENT_URI, c.getLong(0)))
                        .withValue(Data.IS_PRIMARY, if (primary) 1 else 0)
                        .withValue(Data.IS_SUPER_PRIMARY, if (primary) 1 else 0)
                        .build(),
                )
            }
            if (ops.isNotEmpty()) cr.applyBatch(ContactsContract.AUTHORITY, ops)
        }
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        // Kontakty z karty SIM usuwamy też z samej karty – inaczej wróciłyby przy następnym odczycie karty.
        runCatching {
            val cards = sim.cards()
            if (cards.isNotEmpty()) {
                ids.forEach { id ->
                    cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID, RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.CONTACT_ID}=?", arrayOf(id.toString()), null)
                        ?.use { c ->
                            while (c.moveToNext()) {
                                val type = c.getString(1)
                                val name = c.getString(2)
                                if (!sim.isSimAccount(type, name, cards)) continue
                                val card = cards.firstOrNull { it.accountType == type && it.accountName == name } ?: cards.singleOrNull() ?: continue
                                val (simName, simNumber) = simKeyOf(c.getLong(0))
                                sim.delete(card, simName, simNumber)
                            }
                        }
                }
            }
        }
        val ops = ids.map { ContentProviderOperation.newDelete(ContentUris.withAppendedId(Contacts.CONTENT_URI, it)).build() }
        cr.applyBatch(ContactsContract.AUTHORITY, ArrayList(ops))
    }

    /** Konta, w których są już kontakty – do wyboru przy tworzeniu nowego (bez uprawnienia GET_ACCOUNTS). */
    fun accounts(): List<AccountRef> {
        val set = LinkedHashSet<AccountRef>()
        set.add(AccountRef(null, null))
        val cards = sim.cards()
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts.DELETED}=0", null, null)?.use { c ->
            while (c.moveToNext()) {
                val type = c.getString(0)
                val name = c.getString(1)
                // Konta tylko do odczytu (np. komunikatory) pomijamy – nie przyjmą nowego kontaktu.
                // Konta SIM też: pełny kontakt (e-maile, adresy, zdjęcie) się na kartę nie zmieści,
                // więc karty dochodzą niżej jako osobne pozycje z zapisem przez SimRepo.
                if (type != null && name != null && !type.contains("whatsapp") && !type.contains("telegram") && !type.contains("signal") &&
                    !sim.isSimAccount(type, name, cards)
                ) {
                    set.add(AccountRef(type, name))
                }
            }
        }
        cards.forEach { set.add(AccountRef(it.accountType, it.accountName, simSubId = it.subscriptionId, simSlot = it.slot)) }
        return set.toList()
    }

    val sim by lazy { SimRepo(context) }

    /** Konto surowego kontaktu (typ, nazwa). */
    private fun accountOf(rawId: Long): Pair<String?, String?>? =
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts._ID}=?", arrayOf(rawId.toString()), null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }

    /** Nazwa i pierwszy numer surowego kontaktu – tak wygląda jego rekord na karcie SIM. */
    private fun simKeyOf(rawId: Long): Pair<String, String> {
        var name = ""
        var number = ""
        cr.query(
            Data.CONTENT_URI, arrayOf(Data.MIMETYPE, Data.DATA1),
            "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)",
            arrayOf(rawId.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE), null,
        )?.use { c ->
            while (c.moveToNext()) {
                val v = c.getString(1) ?: continue
                if (c.getString(0) == StructuredName.CONTENT_ITEM_TYPE) name = v else if (number.isEmpty()) number = v
            }
        }
        return name to number
    }

    /** Zapis kontaktu na kartę SIM: nazwa i pierwszy numer. Null – zapisano i potwierdzono na karcie. */
    fun saveSim(d: ContactDraft): String? {
        val acc = d.account ?: return tr("Nie wybrano karty SIM")
        val card = sim.cards().firstOrNull { it.subscriptionId == acc.simSubId } ?: return tr("Karta SIM jest niedostępna")
        val res = sim.save(card, d.simOriginal, d.displayName, d.phones.firstOrNull { it.value.isNotBlank() }?.value.orEmpty())
        lookupCache.clear()
        return res
    }

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
        var draft = ContactDraft(id = id, rawContactId = rawId)
        cr.query(
            Data.CONTENT_URI,
            arrayOf(Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6),
            "${Data.RAW_CONTACT_ID}=?", arrayOf(rawId.toString()), null,
        )?.use { c ->
            val phones = ArrayList<DraftField>()
            val emails = ArrayList<DraftField>()
            val addresses = ArrayList<DraftField>()
            val sites = ArrayList<String>()
            while (c.moveToNext()) {
                val v = c.getString(1) ?: ""
                when (c.getString(0)) {
                    StructuredName.CONTENT_ITEM_TYPE -> draft = draft.copy(
                        given = c.getString(2) ?: "",
                        family = c.getString(3) ?: "",
                        prefix = c.getString(4) ?: "",
                        middle = c.getString(5) ?: "",
                        suffix = c.getString(6) ?: "",
                    ).let { if (it.displayName.isBlank() && v.isNotBlank()) it.copy(given = v) else it }
                    Phone.CONTENT_ITEM_TYPE -> phones.add(DraftField(v, c.getInt(2), c.getString(3)))
                    Email.CONTENT_ITEM_TYPE -> emails.add(DraftField(v, c.getInt(2), c.getString(3)))
                    StructuredPostal.CONTENT_ITEM_TYPE -> addresses.add(DraftField(v, c.getInt(2), c.getString(3)))
                    Website.CONTENT_ITEM_TYPE -> sites.add(v)
                    Organization.CONTENT_ITEM_TYPE -> draft = draft.copy(organization = v, title = c.getString(4) ?: "")
                    Nickname.CONTENT_ITEM_TYPE -> draft = draft.copy(nickname = v)
                    Note.CONTENT_ITEM_TYPE -> draft = draft.copy(note = v)
                    Event.CONTENT_ITEM_TYPE -> if (c.getInt(2) == Event.TYPE_BIRTHDAY) draft = draft.copy(birthday = v)
                }
            }
            draft = draft.copy(phones = phones.ifEmpty { listOf(DraftField("", Phone.TYPE_MOBILE)) }, emails = emails, addresses = addresses, websites = sites)
        }
        return draft
    }

    /** Zapis kontaktu: nowy surowy kontakt albo podmiana pól zarządzanych przez formularz. Zwraca id kontaktu. */
    fun save(d: ContactDraft): Long? {
        val ops = ArrayList<ContentProviderOperation>()
        val rawId = d.rawContactId
        fun dataInsert(): ContentProviderOperation.Builder = if (rawId == null) {
            ContentProviderOperation.newInsert(Data.CONTENT_URI).withValueBackReference(Data.RAW_CONTACT_ID, 0)
        } else {
            ContentProviderOperation.newInsert(Data.CONTENT_URI).withValue(Data.RAW_CONTACT_ID, rawId)
        }
        if (rawId == null) {
            ops.add(
                ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                    .withValue(RawContacts.ACCOUNT_TYPE, d.account?.type)
                    .withValue(RawContacts.ACCOUNT_NAME, d.account?.name)
                    .build(),
            )
        } else {
            val managed = listOf(
                StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE, Email.CONTENT_ITEM_TYPE, StructuredPostal.CONTENT_ITEM_TYPE,
                Website.CONTENT_ITEM_TYPE, Organization.CONTENT_ITEM_TYPE, Nickname.CONTENT_ITEM_TYPE, Note.CONTENT_ITEM_TYPE,
            ) + (if (d.photoChanged) listOf(Photo.CONTENT_ITEM_TYPE) else emptyList())
            ops.add(
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection(
                        "${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (${managed.joinToString(",") { "?" }})",
                        arrayOf(rawId.toString()) + managed,
                    ).build(),
            )
            // Urodziny osobno – inne wydarzenia (rocznice) zostają.
            ops.add(
                ContentProviderOperation.newDelete(Data.CONTENT_URI)
                    .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE}=? AND ${Event.TYPE}=?", arrayOf(rawId.toString(), Event.CONTENT_ITEM_TYPE, Event.TYPE_BIRTHDAY.toString()))
                    .build(),
            )
        }
        ops.add(
            dataInsert()
                .withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE)
                .withValue(StructuredName.GIVEN_NAME, d.given.trim().ifEmpty { null })
                .withValue(StructuredName.FAMILY_NAME, d.family.trim().ifEmpty { null })
                .withValue(StructuredName.MIDDLE_NAME, d.middle.trim().ifEmpty { null })
                .withValue(StructuredName.PREFIX, d.prefix.trim().ifEmpty { null })
                .withValue(StructuredName.SUFFIX, d.suffix.trim().ifEmpty { null })
                .build(),
        )
        d.phones.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE).withValue(Phone.NUMBER, it.value.trim()).withValue(Phone.TYPE, it.type).withValue(Phone.LABEL, it.label).build())
        }
        d.emails.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Email.CONTENT_ITEM_TYPE).withValue(Email.ADDRESS, it.value.trim()).withValue(Email.TYPE, it.type).withValue(Email.LABEL, it.label).build())
        }
        d.addresses.filter { it.value.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, StructuredPostal.CONTENT_ITEM_TYPE).withValue(StructuredPostal.FORMATTED_ADDRESS, it.value.trim()).withValue(StructuredPostal.TYPE, it.type).build())
        }
        d.websites.filter { it.isNotBlank() }.forEach {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Website.CONTENT_ITEM_TYPE).withValue(Website.URL, it.trim()).withValue(Website.TYPE, Website.TYPE_HOMEPAGE).build())
        }
        if (d.organization.isNotBlank() || d.title.isNotBlank()) {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Organization.CONTENT_ITEM_TYPE).withValue(Organization.COMPANY, d.organization.trim()).withValue(Organization.TITLE, d.title.trim()).build())
        }
        if (d.nickname.isNotBlank()) ops.add(dataInsert().withValue(Data.MIMETYPE, Nickname.CONTENT_ITEM_TYPE).withValue(Nickname.NAME, d.nickname.trim()).build())
        if (d.note.isNotBlank()) ops.add(dataInsert().withValue(Data.MIMETYPE, Note.CONTENT_ITEM_TYPE).withValue(Note.NOTE, d.note.trim()).build())
        if (d.birthday.isNotBlank()) {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Event.CONTENT_ITEM_TYPE).withValue(Event.START_DATE, d.birthday.trim()).withValue(Event.TYPE, Event.TYPE_BIRTHDAY).build())
        }
        if (d.photoChanged && d.photo != null) {
            ops.add(dataInsert().withValue(Data.MIMETYPE, Photo.CONTENT_ITEM_TYPE).withValue(Photo.PHOTO, d.photo).withValue(Data.IS_SUPER_PRIMARY, 1).build())
        }
        val res = cr.applyBatch(ContactsContract.AUTHORITY, ops)
        lookupCache.clear()
        if (d.id != null) return d.id
        val newRaw = res.firstOrNull()?.uri?.let { ContentUris.parseId(it) } ?: return null
        return cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.CONTACT_ID), "${RawContacts._ID}=?", arrayOf(newRaw.toString()), null)
            ?.use { c -> if (c.moveToFirst()) c.getLong(0) else null }
    }

    /** Zdjęcie z galerii – przeskalowane do 720 px i zapisane jako JPEG (limit rozmiaru Data). */
    /** Wczytuje zdjęcie do kadrowania: zmniejszone do ~2048 px i obrócone zgodnie z EXIF. */
    fun loadPhotoForCrop(uri: Uri): Bitmap? = runCatching {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= 2048 || bounds.outHeight / (sample * 2) >= 2048) sample *= 2
        val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
        val rotation = runCatching {
            cr.openInputStream(uri)?.use { when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270
                    else -> 0
                }
            } ?: 0
        }.getOrDefault(0)
        if (rotation == 0) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rotation.toFloat()) }, true)
    }.getOrNull()

    /** Wycina kwadrat (współrzędne w pikselach [bmp]) i zapisuje jako JPEG do 720×720. */
    fun cropPhoto(bmp: Bitmap, left: Int, top: Int, side: Int): ByteArray? = runCatching {
        val s = side.coerceIn(1, minOf(bmp.width, bmp.height))
        val x = left.coerceIn(0, bmp.width - s)
        val y = top.coerceIn(0, bmp.height - s)
        val square = Bitmap.createBitmap(bmp, x, y, s, s)
        val scaled = if (s > 720) Bitmap.createScaledBitmap(square, 720, 720, true) else square
        ByteArrayOutputStream().use { out ->
            scaled.compress(Bitmap.CompressFormat.JPEG, 90, out)
            out.toByteArray()
        }
    }.getOrNull()

    /** Kontakty z tym samym numerem albo identyczną nazwą. */
    fun duplicates(all: List<Contact>): List<DuplicateGroup> {
        val groups = ArrayList<DuplicateGroup>()
        val used = HashSet<Long>()
        val byNumber = HashMap<String, MutableList<Contact>>()
        all.forEach { c -> c.numbers.map { PhoneText.key(it.number) }.filter { it.length >= 6 }.toSet().forEach { byNumber.getOrPut(it) { ArrayList() }.add(c) } }
        byNumber.values.filter { it.size > 1 }.forEach { list ->
            val fresh = list.distinctBy { it.id }.filter { it.id !in used }
            if (fresh.size > 1) {
                groups.add(DuplicateGroup(tr("Ten sam numer"), fresh))
                used.addAll(fresh.map { it.id })
            }
        }
        all.filter { it.id !in used }.groupBy { it.searchName.trim() }.filter { it.key.isNotBlank() && it.value.size > 1 }.forEach { (_, list) ->
            groups.add(DuplicateGroup(tr("Ta sama nazwa"), list))
            used.addAll(list.map { it.id })
        }
        return groups
    }

    /** Scalenie: wszystkie surowe kontakty łączone w jeden (AggregationExceptions – jak w systemie). */
    fun merge(ids: List<Long>) {
        val raws = ids.flatMap { id ->
            cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts._ID), "${RawContacts.CONTACT_ID}=?", arrayOf(id.toString()), null)
                ?.use { c -> buildList { while (c.moveToNext()) add(c.getLong(0)) } }.orEmpty()
        }
        if (raws.size < 2) return
        val ops = ArrayList<ContentProviderOperation>()
        for (i in 1 until raws.size) {
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_TOGETHER)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, raws[0])
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, raws[i])
                    .build(),
            )
        }
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
        lookupCache.clear()
    }

    fun phoneTypeLabel(type: Int, label: String?): String =
        Phone.getTypeLabel(context.resources, type, label).toString()

    fun emailTypeLabel(type: Int, label: String?): String =
        Email.getTypeLabel(context.resources, type, label).toString()

    fun postalTypeLabel(type: Int, label: String?): String =
        StructuredPostal.getTypeLabel(context.resources, type, label).toString()
}
