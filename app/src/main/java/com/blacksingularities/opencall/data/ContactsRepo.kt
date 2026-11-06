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

/** Kontakty z ContactsContract: lista, szczegóły, edycja, ulubione, duplikaty i scalanie. */
class ContactsRepo(private val context: Context) {
    private val cr get() = context.contentResolver
    internal val resolver get() = context.contentResolver

    private val reader = ContactReader(this)
    private val writer = ContactWriter(this, reader)
    private val photos = ContactPhotos(this)

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

    // ---- Szczegóły, zapis i zdjęcia – w ContactReader, ContactWriter i ContactPhotos ----------

    fun details(id: Long): ContactDetails? = reader.details(id)

    /** Grupy konta, do których można przypisać kontakt. */
    fun groups(account: AccountRef?): List<GroupRef> = reader.groups(account)

    /** Surowe kontakty składające się na kontakt (np. Google + pamięć telefonu + WhatsApp). */
    fun rawContacts(contactId: Long): List<RawRef> = reader.rawContacts(contactId)

    /** Nowa grupa w koncie; zwraca jej id. */
    fun createGroup(account: AccountRef?, title: String): Long? = writer.createGroup(account, title)

    /** Formularz edycji z istniejącego kontaktu. */
    fun draft(id: Long): ContactDraft? = writer.draft(id)

    /** Zapis kontaktu (nowy, zmiana albo przeniesienie do innego konta). Zwraca id kontaktu. */
    fun save(d: ContactDraft): Long? = writer.save(d)

    /** Wczytuje zdjęcie do kadrowania: zmniejszone do ~2048 px i obrócone zgodnie z EXIF. */
    fun loadPhotoForCrop(uri: Uri): Bitmap? = photos.loadPhotoForCrop(uri)

    /** Wycina kwadrat i zapisuje jako JPEG do 720×720. */
    fun cropPhoto(bmp: Bitmap, left: Int, top: Int, side: Int): ByteArray? = photos.cropPhoto(bmp, left, top, side)

    /** Odłącza surowy kontakt od pozostałych – system pokaże go jako osobny kontakt. */
    fun split(contactId: Long, rawId: Long) {
        val others = rawContacts(contactId).map { it.id }.filter { it != rawId }
        if (others.isEmpty()) return
        val ops = ArrayList<ContentProviderOperation>()
        others.forEach { other ->
            ops.add(
                ContentProviderOperation.newUpdate(ContactsContract.AggregationExceptions.CONTENT_URI)
                    .withValue(ContactsContract.AggregationExceptions.TYPE, ContactsContract.AggregationExceptions.TYPE_KEEP_SEPARATE)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID1, rawId)
                    .withValue(ContactsContract.AggregationExceptions.RAW_CONTACT_ID2, other)
                    .build(),
            )
        }
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
        lookupCache.clear()
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
    internal fun accountOf(rawId: Long): Pair<String?, String?>? =
        cr.query(RawContacts.CONTENT_URI, arrayOf(RawContacts.ACCOUNT_TYPE, RawContacts.ACCOUNT_NAME), "${RawContacts._ID}=?", arrayOf(rawId.toString()), null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) to c.getString(1) else null }

    /** Nazwa i pierwszy numer surowego kontaktu – tak wygląda jego rekord na karcie SIM. */
    internal fun simKeyOf(rawId: Long): Pair<String, String> {
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
        val number = d.phones.firstOrNull { it.value.isNotBlank() }?.value.orEmpty()
        val res = sim.save(card, d.simOriginal, d.displayName, number)
        if (res == null) runCatching { mirrorSim(card, d.simOriginal, d.displayName.trim(), sim.cleanNumber(number)) }
        lookupCache.clear()
        return res
    }

    /**
     * Zapis przez SimPhonebookContract trafia tylko na kartę. Inne aplikacje (SMS, rejestr połączeń)
     * czytają kontakty z ContactsContract, gdzie system trzyma lustro karty pod jej kontem – a odświeża
     * je zwykle dopiero przy starcie telefonu. Dlatego od razu poprawiamy to lustro sami: zmieniamy
     * surowy kontakt edytowanego rekordu albo dodajemy nowy. Bez konta SIM w systemie nie ma czego
     * lustrzyć. Gdy system zdążył już sam dodać rekord, niczego nie dublujemy.
     */
    private fun mirrorSim(card: SimRepo.Card, original: Pair<String, String>?, name: String, number: String) {
        val type = card.accountType ?: return
        val accName = card.accountName ?: return
        fun matches(key: Pair<String, String>, n: String, num: String) =
            key.first.trim() == n.trim() && PhoneText.sameNumber(key.second, num)

        val raws = ArrayList<Pair<Long, Pair<String, String>>>()
        cr.query(
            RawContacts.CONTENT_URI, arrayOf(RawContacts._ID),
            "${RawContacts.ACCOUNT_TYPE}=? AND ${RawContacts.ACCOUNT_NAME}=? AND ${RawContacts.DELETED}=0",
            arrayOf(type, accName), null,
        )?.use { c -> while (c.moveToNext()) c.getLong(0).let { raws += it to simKeyOf(it) } }

        if (raws.any { matches(it.second, name, number) }) return
        val target = original?.let { (on, onum) -> raws.firstOrNull { matches(it.second, on, onum) }?.first }

        val ops = ArrayList<ContentProviderOperation>()
        if (target != null) {
            ops += ContentProviderOperation.newDelete(Data.CONTENT_URI)
                .withSelection("${Data.RAW_CONTACT_ID}=? AND ${Data.MIMETYPE} IN (?,?)", arrayOf(target.toString(), StructuredName.CONTENT_ITEM_TYPE, Phone.CONTENT_ITEM_TYPE))
                .build()
        } else {
            ops += ContentProviderOperation.newInsert(RawContacts.CONTENT_URI)
                .withValue(RawContacts.ACCOUNT_TYPE, type)
                .withValue(RawContacts.ACCOUNT_NAME, accName)
                .build()
        }
        fun dataOp() = ContentProviderOperation.newInsert(Data.CONTENT_URI).let {
            if (target != null) it.withValue(Data.RAW_CONTACT_ID, target) else it.withValueBackReference(Data.RAW_CONTACT_ID, 0)
        }
        if (name.isNotEmpty()) {
            ops += dataOp().withValue(Data.MIMETYPE, StructuredName.CONTENT_ITEM_TYPE).withValue(StructuredName.DISPLAY_NAME, name).build()
        }
        ops += dataOp().withValue(Data.MIMETYPE, Phone.CONTENT_ITEM_TYPE)
            .withValue(Phone.NUMBER, number).withValue(Phone.TYPE, Phone.TYPE_MOBILE).build()
        cr.applyBatch(ContactsContract.AUTHORITY, ops)
    }

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
