package com.blacksingularities.opencall.data

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.ContactsContract
import android.provider.SimPhonebookContract.ElementaryFiles
import android.provider.SimPhonebookContract.SimRecords
import androidx.annotation.RequiresApi
import com.blacksingularities.opencall.i18n.tr

/**
 * Książka adresowa na karcie SIM (plik ADN) przez publiczne `SimPhonebookContract` (Android 12+).
 *
 * Karta przechowuje tylko nazwę i jeden numer, a limity zależą od karty: nazwa liczona
 * w bajtach po zakodowaniu (polskie znaki zajmują więcej), numer w cyfrach. Każdy zapis
 * jest potem odczytywany z karty – „zapisano” pokazujemy dopiero, gdy rekord tam jest.
 * Systemowe konto SIM w kontaktach odświeża się samo na podstawie karty.
 */
class SimRepo(private val context: Context) {
    private val cr get() = context.contentResolver

    /** Plik ADN jednej karty: zajętość i limity długości. */
    data class Card(
        val subscriptionId: Int,
        val slot: Int,
        val used: Int,
        val capacity: Int,
        val nameMaxBytes: Int,
        val numberMaxDigits: Int,
        /** Konto systemowe tej karty w ContactsContract (null – system go nie udostępnia). */
        val accountType: String?,
        val accountName: String?,
    ) {
        val full: Boolean get() = capacity in 1..used
    }

    data class Record(val recordNumber: Int, val name: String, val number: String)

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 31

    fun cards(): List<Card> = if (supported) runCatching { cardsApi31() }.getOrDefault(emptyList()) else emptyList()

    @RequiresApi(31)
    private fun cardsApi31(): List<Card> {
        val accounts = runCatching { ContactsContract.SimContacts.getSimAccounts(cr) }.getOrDefault(emptyList())
            .filter { it.efType == ContactsContract.SimAccount.ADN_EF_TYPE }
        val out = ArrayList<Card>()
        cr.query(
            ElementaryFiles.CONTENT_URI,
            arrayOf(
                ElementaryFiles.SUBSCRIPTION_ID, ElementaryFiles.SLOT_INDEX, ElementaryFiles.EF_TYPE,
                ElementaryFiles.RECORD_COUNT, ElementaryFiles.MAX_RECORDS,
                ElementaryFiles.NAME_MAX_LENGTH, ElementaryFiles.PHONE_NUMBER_MAX_LENGTH,
            ),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) {
                if (c.getInt(2) != ElementaryFiles.EF_ADN) continue
                val slot = c.getInt(1)
                val acc = accounts.firstOrNull { it.simSlotIndex == slot }
                out += Card(c.getInt(0), slot, c.getInt(3), c.getInt(4), c.getInt(5), c.getInt(6), acc?.accountType, acc?.accountName)
            }
        }
        return out
    }

    /** Czy konto z ContactsContract to karta SIM (wtedy zapis musi iść przez tę klasę). */
    fun isSimAccount(type: String?, name: String?, cards: List<Card> = cards()): Boolean {
        if (type == null) return false
        if (cards.any { it.accountType == type && it.accountName == name }) return true
        // Starsze nakładki producentów mają własne nazwy kont SIM.
        val t = type.lowercase()
        return t.endsWith(".sim") || t.contains("contact.sim") || t.contains("simcontact") || t == "sim"
    }

    fun cardFor(type: String?, name: String?): Card? = cards().firstOrNull { it.accountType == type && it.accountName == name }

    /** Ile bajtów zajmie nazwa na karcie (system wybiera kodowanie GSM 7-bit albo UCS-2). */
    fun nameBytes(name: String): Int {
        if (supported) runCatching { return SimRecords.getEncodedNameLength(cr, name) }
        // Zapas, gdy system nie policzy: każdy znak spoza ASCII wymusza UCS-2.
        return if (name.all { it.code < 128 }) name.length else name.length * 2 + 1
    }

    /** Numer w postaci zapisywanej na karcie: cyfry, „+” na początku, „*”, „#”, „,” i „;”. */
    fun cleanNumber(number: String): String {
        val t = number.trim()
        val body = t.filter { it.isDigit() || it in "*#,;" }
        return if (t.startsWith("+")) "+$body" else body
    }

    /** Sprawdzenie przed zapisem – pusty wynik oznacza, że zmieści się na karcie. */
    fun validate(card: Card, name: String, number: String): String? {
        val n = cleanNumber(number)
        if (name.isBlank() && n.isEmpty()) return tr("Wpisz nazwę albo numer")
        if (n.isEmpty()) return tr("Kontakt na karcie SIM musi mieć numer")
        val digits = n.count { it != '+' }
        if (card.numberMaxDigits > 0 && digits > card.numberMaxDigits) {
            return tr("Numer za długi dla karty SIM ({0} z {1} cyfr)", digits, card.numberMaxDigits)
        }
        val bytes = nameBytes(name.trim())
        if (card.nameMaxBytes > 0 && bytes > card.nameMaxBytes) {
            return tr("Nazwa za długa dla karty SIM ({0} z {1}). Polskie znaki zajmują więcej miejsca.", bytes, card.nameMaxBytes)
        }
        return null
    }

    fun records(subId: Int): List<Record> {
        if (!supported) return emptyList()
        return runCatching { recordsApi31(subId) }.getOrDefault(emptyList())
    }

    @RequiresApi(31)
    private fun recordsApi31(subId: Int): List<Record> {
        val out = ArrayList<Record>()
        cr.query(
            SimRecords.getContentUri(subId, ElementaryFiles.EF_ADN),
            arrayOf(SimRecords.RECORD_NUMBER, SimRecords.NAME, SimRecords.PHONE_NUMBER),
            null, null, null,
        )?.use { c ->
            while (c.moveToNext()) out += Record(c.getInt(0), c.getString(1) ?: "", c.getString(2) ?: "")
        }
        return out
    }

    private fun same(r: Record, name: String, number: String) =
        r.name.trim() == name.trim() && PhoneText.sameNumber(r.number, number)

    /**
     * Zapisuje kontakt na kartę. [original] – nazwa i numer edytowanego rekordu (null = nowy).
     * Zwraca null po potwierdzeniu na karcie, inaczej komunikat.
     */
    fun save(card: Card, original: Pair<String, String>?, name: String, number: String): String? {
        if (!supported) return tr("Zapis na karcie SIM wymaga Androida 12 lub nowszego")
        validate(card, name, number)?.let { return it }
        val n = cleanNumber(number)
        val trimmed = name.trim()
        return runCatching { saveApi31(card, original, trimmed, n) }
            .getOrElse { e -> if (e is SecurityException) tr("Brak uprawnienia do zapisu kontaktów") else tr("Karta SIM odrzuciła zapis") }
    }

    @RequiresApi(31)
    private fun saveApi31(card: Card, original: Pair<String, String>?, name: String, number: String): String? {
        val values = ContentValues().apply {
            put(SimRecords.NAME, name)
            put(SimRecords.PHONE_NUMBER, number)
        }
        val existing = original?.let { (on, onum) -> records(card.subscriptionId).firstOrNull { same(it, on, onum) } }
        if (existing != null) {
            val uri = SimRecords.getItemUri(card.subscriptionId, ElementaryFiles.EF_ADN, existing.recordNumber)
            if (cr.update(uri, values, null, null) <= 0) return tr("Karta SIM odrzuciła zapis")
        } else {
            if (card.full) return tr("Karta SIM jest pełna ({0} z {1})", card.used, card.capacity)
            cr.insert(SimRecords.getContentUri(card.subscriptionId, ElementaryFiles.EF_ADN), values)
                ?: return tr("Karta SIM odrzuciła zapis")
        }
        return if (confirm(card.subscriptionId, name, number)) null else tr("Karta SIM nie potwierdziła zapisu")
    }

    /** Usuwa z karty rekord o tej nazwie i numerze (pierwszy pasujący – nie wszystkie duplikaty). */
    fun delete(card: Card, name: String, number: String): String? {
        if (!supported) return tr("Zapis na karcie SIM wymaga Androida 12 lub nowszego")
        return runCatching { deleteApi31(card, name, number) }.getOrElse { tr("Karta SIM odrzuciła zmianę") }
    }

    @RequiresApi(31)
    private fun deleteApi31(card: Card, name: String, number: String): String? {
        val r = records(card.subscriptionId).firstOrNull { same(it, name, number) } ?: return null
        val uri = SimRecords.getItemUri(card.subscriptionId, ElementaryFiles.EF_ADN, r.recordNumber)
        return if (cr.delete(uri, null, null) > 0) null else tr("Karta SIM odrzuciła zmianę")
    }

    /** Odczyt kontrolny: karta bywa wolna, więc kilka prób w odstępach. */
    private fun confirm(subId: Int, name: String, number: String): Boolean {
        repeat(4) { attempt ->
            if (records(subId).any { same(it, name, number) }) return true
            if (attempt < 3) Thread.sleep(400L * (attempt + 1))
        }
        return false
    }
}
