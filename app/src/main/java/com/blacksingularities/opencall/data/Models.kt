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
    val websites: List<String>,
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
)

data class LabeledValue(val value: String, val type: Int, val label: String?)

/** Dane formularza edycji kontaktu. */
data class ContactDraft(
    val id: Long? = null,
    val rawContactId: Long? = null,
    val prefix: String = "",
    val given: String = "",
    val middle: String = "",
    val family: String = "",
    val suffix: String = "",
    val nickname: String = "",
    val organization: String = "",
    val title: String = "",
    val phones: List<DraftField> = listOf(DraftField("", 2)),
    val emails: List<DraftField> = emptyList(),
    val addresses: List<DraftField> = emptyList(),
    val websites: List<String> = emptyList(),
    val birthday: String = "",
    val note: String = "",
    val photo: ByteArray? = null,
    val photoChanged: Boolean = false,
    val account: AccountRef? = null,
    /** Edytowany kontakt z karty SIM: jego dotychczasowa nazwa i numer (do odszukania rekordu). */
    val simOriginal: Pair<String, String>? = null,
) {
    val displayName: String
        get() = listOf(prefix, given, middle, family, suffix).filter { it.isNotBlank() }.joinToString(" ").trim()

    val isEmpty: Boolean
        get() = displayName.isBlank() && organization.isBlank() && phones.all { it.value.isBlank() } && emails.all { it.value.isBlank() }
}

data class DraftField(val value: String, val type: Int, val label: String? = null)

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
