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

    /** Kolumny tabeli Data czytane przy szczegółach i formularzu: MIMETYPE, DATA1…DATA10, surowy kontakt, domyślność. */
    private val dataProjection = arrayOf(
        Data.MIMETYPE, Data.DATA1, Data.DATA2, Data.DATA3, Data.DATA4, Data.DATA5, Data.DATA6, Data.DATA7, Data.DATA8, Data.DATA9, Data.DATA10,
        Data.RAW_CONTACT_ID, Data.IS_SUPER_PRIMARY, Data.IS_PRIMARY,
    )

    private fun android.database.Cursor.str(i: Int): String = getString(i)?.trim().orEmpty()

    /** Adres z wiersza StructuredPostal (kolumny jak w [dataProjection]). */
    private fun android.database.Cursor.postal(): PostalField {
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
    private fun accountRef(type: String?, name: String?, cards: List<SimRepo.Card>): AccountRef {
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

    private companion object {
        /** Grupy systemowe pomijane w formularzu: „Moje kontakty” (dodawane automatycznie) i „Oznaczone gwiazdką”. */
        val HIDDEN_SYSTEM_GROUPS = setOf("Contacts", "Starred")
    }

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
        lookupCache.clear()
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
        lookupCache.clear()
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
