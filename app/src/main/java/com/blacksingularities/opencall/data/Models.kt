package com.blacksingularities.opencall.data

/** Kontakt na liście: dane potrzebne do wyświetlenia, wyszukiwania i wybierania T9. */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val numbers: List<PhoneNumber>,
    /** Nazwa w postaci cyfr klawiatury (T9) – liczona raz przy wczytaniu. */
    val t9: String = PhoneText.toT9(name),
    /** Nazwa bez polskich znaków, małymi literami – do wyszukiwania. */
    val searchName: String = PhoneText.fold(name),
) {
    val primaryNumber: PhoneNumber? get() = numbers.firstOrNull { it.primary } ?: numbers.firstOrNull()
}

data class PhoneNumber(
    val number: String,
    val type: Int,
    val label: String?,
    val primary: Boolean = false,
    val normalized: String = PhoneText.digits(number),
)

/** Rodzaj połączenia w rejestrze – odpowiada stałym CallLog.Calls.*_TYPE. */
enum class CallKind { INCOMING, OUTGOING, MISSED, REJECTED, BLOCKED, VOICEMAIL;

    companion object {
        fun of(type: Int): CallKind = when (type) {
            1 -> INCOMING
            2 -> OUTGOING
            3 -> MISSED
            4 -> VOICEMAIL
            5 -> REJECTED
            6 -> BLOCKED
            7 -> INCOMING // ANSWERED_EXTERNALLY
            else -> INCOMING
        }
    }
}

/** Jeden wpis rejestru połączeń. */
data class CallEntry(
    val id: Long,
    val number: String,
    val name: String?,
    val photoUri: String?,
    val kind: CallKind,
    val date: Long,
    val durationSec: Long,
    val accountId: String?,
    val location: String?,
    /** Numer ukryty / zastrzeżony / płatny (NUMBER_PRESENTATION ≠ ALLOWED). */
    val hidden: Boolean,
    val isNew: Boolean,
    val video: Boolean,
    /** Nagranie poczty głosowej (content://…) – gdy operator i system je pobrały. */
    val voicemailUri: String? = null,
)

/** Kolejne połączenia z tym samym numerem tego samego dnia – jeden wiersz na liście „Ostatnie”. */
data class CallGroup(
    val entries: List<CallEntry>,
) {
    val first: CallEntry get() = entries.first()
    val key: String get() = "${first.id}"
    val count: Int get() = entries.size
    val missed: Boolean get() = first.kind == CallKind.MISSED || first.kind == CallKind.REJECTED
    val unseenMissed: Boolean get() = entries.any { it.kind == CallKind.MISSED && it.isNew }
}

/** Szczegóły kontaktu odczytane z tabeli Data. */
data class ContactDetails(
    val id: Long,
    val lookupKey: String,
    val name: String,
    val photoUri: String?,
    val starred: Boolean,
    val phones: List<PhoneNumber>,
    val emails: List<LabeledValue>,
    val addresses: List<LabeledValue>,
    val websites: List<LabeledValue>,
    val organization: String?,
    val title: String?,
    val nickname: String?,
    val note: String?,
    val birthday: String?,
    val events: List<LabeledValue>,
    val rawContactIds: List<Long>,
    val accounts: List<String>,
    val customRingtone: String?,
    val sendToVoicemail: Boolean,
    val department: String? = null,
    val jobDescription: String? = null,
    val phoneticName: String? = null,
    val relations: List<LabeledValue> = emptyList(),
    /** Komunikatory: value = identyfikator, type = protokół (Im.PROTOCOL_*), label = nazwa własnego protokołu. */
    val ims: List<LabeledValue> = emptyList(),
    val sip: List<LabeledValue> = emptyList(),
    val groups: List<String> = emptyList(),
    /** Konta surowych kontaktów – do plakietek w nagłówku. */
    val accountRefs: List<AccountRef> = emptyList(),
)

data class LabeledValue(val value: String, val type: Int, val label: String?, val primary: Boolean = false)

/** Adres w polach (StructuredPostal). */
data class PostalField(
    val street: String = "",
    val postcode: String = "",
    val city: String = "",
    val region: String = "",
    val country: String = "",
    val type: Int = 1,
    val label: String? = null,
) {
    /** Zapis w jednej linii (do mapy i listy): „ul. Długa 1, 00-001 Warszawa, Polska”. */
    val formatted: String
        get() = listOf(
            street.lines().map { it.trim() }.filter { it.isNotEmpty() }.joinToString(", "),
            listOf(postcode.trim(), city.trim()).filter { it.isNotEmpty() }.joinToString(" "),
            region.trim(),
            country.trim(),
        ).filter { it.isNotEmpty() }.joinToString(", ")

    val isBlank: Boolean get() = formatted.isBlank()
}

/** Grupa (etykieta) kontaktów w koncie. */
data class GroupRef(val id: Long, val title: String)

/** Surowy kontakt składający się na widok kontaktu – do listy „Połączone kontakty”. */
data class RawRef(val id: Long, val account: AccountRef, val name: String)

/** Dane formularza edycji kontaktu. */
data class ContactDraft(
    val id: Long? = null,
    val rawContactId: Long? = null,
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val phoneticGiven: String = "",
    val phoneticMiddle: String = "",
    val phoneticFamily: String = "",
    val nickname: String = "",
    val organization: String = "",
    val title: String = "",
    val department: String = "",
    val jobDescription: String = "",
    val phones: List<DraftField> = listOf(DraftField("", 2)),
    val emails: List<DraftField> = emptyList(),
    val addresses: List<PostalField> = emptyList(),
    val websites: List<DraftField> = emptyList(),
    val birthday: String = "",
    /** Wydarzenia poza urodzinami: value = data (RRRR-MM-DD albo --MM-DD), type = Event.TYPE_*. */
    val events: List<DraftField> = emptyList(),
    /** Relacje: value = imię i nazwisko osoby, type = Relation.TYPE_*. */
    val relations: List<DraftField> = emptyList(),
    /** Komunikatory: value = identyfikator, type = Im.PROTOCOL_*, label = nazwa własnego protokołu. */
    val ims: List<DraftField> = emptyList(),
    val sip: List<DraftField> = emptyList(),
    val note: String = "",
    val photo: ByteArray? = null,
    val photoChanged: Boolean = false,
    val account: AccountRef? = null,
    /** Konto, w którym kontakt był przy otwarciu formularza – zmiana oznacza przeniesienie. */
    val originalAccount: AccountRef? = null,
    /** Zaznaczone grupy (Groups._ID) i te, które były przy otwarciu formularza. */
    val groups: Set<Long> = emptySet(),
    val originalGroups: Set<Long> = emptySet(),
    val starred: Boolean = false,
    val ringtone: String? = null,
    val sendToVoicemail: Boolean = false,
    /** Edytowany kontakt z karty SIM: jego dotychczasowa nazwa i numer (do odszukania rekordu). */
    val simOriginal: Pair<String, String>? = null,
) {
    val displayName: String
        get() = listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ").trim()

    val phoneticName: String
        get() = listOf(phoneticGiven, phoneticMiddle, phoneticFamily).filter { it.isNotBlank() }.joinToString(" ").trim()

    val isEmpty: Boolean
        get() = displayName.isBlank() && organization.isBlank() && phones.all { it.value.isBlank() } && emails.all { it.value.isBlank() }

    /** Kontakt zmienia konto (np. z pamięci telefonu do Google). */
    val moving: Boolean
        get() = id != null && simOriginal == null && account?.sim != true && account != originalAccount

    // ByteArray porównuje się po referencji – formularz traktuje zmianę zdjęcia przez [photoChanged].
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is ContactDraft) return false
        return toList() == other.toList() && (photo === other.photo || photo.contentEquals(other.photo))
    }

    private fun toList(): List<Any?> = listOf(
        id, rawContactId, prefix, given, middle, family, suffix, phoneticGiven, phoneticMiddle, phoneticFamily, nickname,
        organization, title, department, jobDescription, phones, emails, addresses, websites, birthday, events, relations,
        ims, sip, note, photoChanged, account, originalAccount, groups, originalGroups, starred, ringtone, sendToVoicemail, simOriginal,
    )

    override fun hashCode(): Int = toList().hashCode()
}

data class DraftField(val value: String, val type: Int, val label: String? = null, val primary: Boolean = false)

/** Konto, do którego trafia nowy kontakt (null = pamięć telefonu). */
data class AccountRef(
    val type: String?,
    val name: String?,
    /** Karta SIM (identyfikator subskrypcji) – zapis idzie wtedy przez [SimRepo], nie przez konto. */
    val simSubId: Int? = null,
    /** Numer gniazda karty (0, 1) – do etykiety „SIM 1”. */
    val simSlot: Int = 0,
) {
    val local: Boolean get() = simSubId == null && (type == null || name == null)
    val sim: Boolean get() = simSubId != null
}

/** Karta SIM / konto telefoniczne do wybierania numeru. */
data class SimAccount(val id: String, val label: String, val number: String?, val slot: Int, val color: Int)

/** Para kontaktów prawdopodobnie zdublowanych. */
data class DuplicateGroup(val reason: String, val contacts: List<Contact>)

/** Statystyki połączeń z wybranego okresu. */
data class CallStats(
    val total: Int,
    val incoming: Int,
    val outgoing: Int,
    val missed: Int,
    val talkSec: Long,
    val inSec: Long,
    val outSec: Long,
    val longest: CallEntry?,
    val top: List<Pair<String, Int>>,
    val topTime: List<Pair<String, Long>>,
    val byWeekday: IntArray,
    val byHour: IntArray,
)
