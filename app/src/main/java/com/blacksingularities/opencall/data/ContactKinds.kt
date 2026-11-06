@file:Suppress("DEPRECATION") // Im.PROTOCOL_* – nadal jedyny sposób zapisu komunikatorów w ContactsContract.

package com.blacksingularities.opencall.data

import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.CommonDataKinds.Im
import android.provider.ContactsContract.CommonDataKinds.Relation
import android.provider.ContactsContract.CommonDataKinds.SipAddress
import android.provider.ContactsContract.CommonDataKinds.StructuredPostal
import android.provider.ContactsContract.CommonDataKinds.Website

/** Rodzaje pól kontaktu (stałe ContactsContract) używane przez formularz, widok i vCard. */
object ContactKinds {
    /** BaseTypes.TYPE_CUSTOM – rodzaj z własną etykietą. */
    const val CUSTOM = 0

    /** Im.PROTOCOL_CUSTOM – komunikator spoza listy systemu (Signal, Telegram, WhatsApp…). */
    const val IM_CUSTOM = -1

    val eventTypes = listOf(Event.TYPE_ANNIVERSARY, Event.TYPE_OTHER)

    val relationTypes = listOf(
        Relation.TYPE_SPOUSE, Relation.TYPE_PARTNER, Relation.TYPE_DOMESTIC_PARTNER, Relation.TYPE_CHILD,
        Relation.TYPE_PARENT, Relation.TYPE_MOTHER, Relation.TYPE_FATHER, Relation.TYPE_BROTHER, Relation.TYPE_SISTER,
        Relation.TYPE_RELATIVE, Relation.TYPE_FRIEND, Relation.TYPE_ASSISTANT, Relation.TYPE_MANAGER, Relation.TYPE_REFERRED_BY,
    )

    val postalTypes = listOf(StructuredPostal.TYPE_HOME, StructuredPostal.TYPE_WORK, StructuredPostal.TYPE_OTHER)

    val websiteTypes = listOf(
        Website.TYPE_HOMEPAGE, Website.TYPE_BLOG, Website.TYPE_PROFILE, Website.TYPE_HOME, Website.TYPE_WORK, Website.TYPE_OTHER,
    )

    val sipTypes = listOf(SipAddress.TYPE_HOME, SipAddress.TYPE_WORK, SipAddress.TYPE_OTHER)

    /**
     * Komunikatory do wyboru: (protokół, nazwa własnego protokołu). Signal, Telegram i WhatsApp nie mają
     * stałych w systemie – zapisują się jako PROTOCOL_CUSTOM z nazwą, tak jak robią to ich aplikacje.
     */
    val imChoices: List<Pair<Int, String?>> = listOf(
        IM_CUSTOM to "Signal",
        IM_CUSTOM to "Telegram",
        IM_CUSTOM to "WhatsApp",
        Im.PROTOCOL_SKYPE to null,
        Im.PROTOCOL_JABBER to null,
        Im.PROTOCOL_GOOGLE_TALK to null,
        Im.PROTOCOL_ICQ to null,
        Im.PROTOCOL_QQ to null,
    )

    /** Nazwa komunikatora (bez tłumaczenia – to nazwy własne). */
    fun imName(protocol: Int, custom: String?): String = when (protocol) {
        Im.PROTOCOL_AIM -> "AIM"
        Im.PROTOCOL_MSN -> "MSN"
        Im.PROTOCOL_YAHOO -> "Yahoo"
        Im.PROTOCOL_SKYPE -> "Skype"
        Im.PROTOCOL_QQ -> "QQ"
        Im.PROTOCOL_GOOGLE_TALK -> "Google Talk"
        Im.PROTOCOL_ICQ -> "ICQ"
        Im.PROTOCOL_JABBER -> "XMPP"
        Im.PROTOCOL_NETMEETING -> "NetMeeting"
        else -> custom?.trim().orEmpty()
    }

    /** Protokół z nazwy (np. z vCard): „skype” → PROTOCOL_SKYPE, „signal” → (CUSTOM, „Signal”). */
    fun imOf(name: String): Pair<Int, String?> {
        val n = name.trim().lowercase()
        return when (n) {
            "skype" -> Im.PROTOCOL_SKYPE to null
            "xmpp", "jabber" -> Im.PROTOCOL_JABBER to null
            "gtalk", "googletalk", "google talk", "hangouts" -> Im.PROTOCOL_GOOGLE_TALK to null
            "icq" -> Im.PROTOCOL_ICQ to null
            "qq" -> Im.PROTOCOL_QQ to null
            "aim" -> Im.PROTOCOL_AIM to null
            "msn" -> Im.PROTOCOL_MSN to null
            "yahoo", "ymsgr" -> Im.PROTOCOL_YAHOO to null
            "signal", "sgnl" -> IM_CUSTOM to "Signal"
            "telegram", "tg" -> IM_CUSTOM to "Telegram"
            "whatsapp" -> IM_CUSTOM to "WhatsApp"
            else -> IM_CUSTOM to name.trim().replaceFirstChar { it.uppercase() }
        }
    }

    /**
     * Data wydarzenia z zapisu kontaktu: „1990-05-12”, „--05-12” (bez roku), „19900512”.
     * Zwraca (rok albo null, miesiąc, dzień) albo null, gdy zapis jest nieczytelny.
     */
    fun parseDate(raw: String): Triple<Int?, Int, Int>? {
        val t = raw.trim()
        Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})").find(t)?.let { m ->
            return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        Regex("^--(\\d{2})-?(\\d{2})").find(t)?.let { m -> return valid(null, m.groupValues[1].toInt(), m.groupValues[2].toInt()) }
        Regex("^(\\d{4})(\\d{2})(\\d{2})$").find(t)?.let { m ->
            return valid(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        return null
    }

    private fun valid(y: Int?, m: Int, d: Int): Triple<Int?, Int, Int>? =
        if (m in 1..12 && d in 1..31 && (y == null || y in 1..9999)) Triple(y, m, d) else null

    /** Zapis daty do kontaktu: „1990-05-12” albo „--05-12” bez roku. */
    fun formatDate(year: Int?, month: Int, day: Int): String =
        if (year == null) {
            String.format(java.util.Locale.ROOT, "--%02d-%02d", month, day)
        } else {
            String.format(java.util.Locale.ROOT, "%04d-%02d-%02d", year, month, day)
        }

    private val emailRegex = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]{2,}$")

    fun validEmail(s: String): Boolean = s.isBlank() || emailRegex.matches(s.trim())

    /** Numer telefonu: cyfry z dopuszczalnymi znakami wybierania, 3–20 cyfr. */
    fun validPhone(s: String): Boolean {
        val t = s.trim()
        if (t.isEmpty()) return true
        if (t.any { !(it.isDigit() || it in "+-()./ *#,;pPwW") }) return false
        return t.count { it.isDigit() } in 3..20
    }
}
