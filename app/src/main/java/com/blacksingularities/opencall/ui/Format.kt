package com.blacksingularities.opencall.ui

import android.provider.ContactsContract
import com.blacksingularities.opencall.data.CallKind
import com.blacksingularities.opencall.data.ContactKinds
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.i18n.tr
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

/** Daty i etykiety w języku interfejsu. */
object Format {
    private fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun time(t: Long): String = SimpleDateFormat("HH:mm", I18n.locale).format(Date(t))

    /** „14:32”, „Wczoraj”, „poniedziałek”, „12 mar”, „12.03.2024”. */
    fun relative(t: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val day = 86_400_000L
        return when {
            t >= today -> time(t)
            t >= today - day -> tr("Wczoraj")
            t >= today - 6 * day -> SimpleDateFormat("EEEE", I18n.locale).format(Date(t))
            Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) == Calendar.getInstance().get(Calendar.YEAR) ->
                SimpleDateFormat("d MMM", I18n.locale).format(Date(t))
            else -> SimpleDateFormat("dd.MM.yyyy", I18n.locale).format(Date(t))
        }
    }

    /** Nagłówek dnia na liście: „Dzisiaj”, „Wczoraj”, „Ten tydzień”, „Wcześniej”. */
    fun bucket(t: Long, now: Long = System.currentTimeMillis()): String {
        val today = startOfDay(now)
        val day = 86_400_000L
        return when {
            t >= today -> tr("Dzisiaj")
            t >= today - day -> tr("Wczoraj")
            t >= today - 6 * day -> tr("Ostatnie 7 dni")
            t >= today - 30 * day -> tr("Ostatnie 30 dni")
            else -> tr("Wcześniej")
        }
    }

    fun full(t: Long): String = SimpleDateFormat("d MMMM yyyy, HH:mm", I18n.locale).format(Date(t))

    fun csvDate(t: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", I18n.locale).format(Date(t))

    fun kind(k: CallKind): String = when (k) {
        CallKind.INCOMING -> tr("Przychodzące")
        CallKind.OUTGOING -> tr("Wychodzące")
        CallKind.MISSED -> tr("Nieodebrane")
        CallKind.REJECTED -> tr("Odrzucone")
        CallKind.BLOCKED -> tr("Zablokowane")
        CallKind.VOICEMAIL -> tr("Poczta głosowa")
    }

    /** Typy numerów w języku interfejsu (systemowe etykiety bywają tylko po angielsku). */
    fun phoneType(type: Int, label: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inny")
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE -> tr("Komórka")
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME -> tr("Dom")
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK -> tr("Praca")
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE -> tr("Komórka służbowa")
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN -> tr("Główny")
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK, ContactsContract.CommonDataKinds.Phone.TYPE_FAX_HOME -> tr("Faks")
        ContactsContract.CommonDataKinds.Phone.TYPE_PAGER -> tr("Pager")
        else -> tr("Inny")
    }

    fun emailType(type: Int, label: String?): String = when (type) {
        ContactsContract.CommonDataKinds.Email.TYPE_CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inny")
        ContactsContract.CommonDataKinds.Email.TYPE_HOME -> tr("Prywatny")
        ContactsContract.CommonDataKinds.Email.TYPE_WORK -> tr("Praca")
        ContactsContract.CommonDataKinds.Email.TYPE_MOBILE -> tr("Komórka")
        else -> tr("Inny")
    }

    val phoneTypes = listOf(
        ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_HOME,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_WORK_MOBILE,
        ContactsContract.CommonDataKinds.Phone.TYPE_MAIN,
        ContactsContract.CommonDataKinds.Phone.TYPE_FAX_WORK,
        ContactsContract.CommonDataKinds.Phone.TYPE_OTHER,
    )

    val emailTypes = listOf(
        ContactsContract.CommonDataKinds.Email.TYPE_HOME,
        ContactsContract.CommonDataKinds.Email.TYPE_WORK,
        ContactsContract.CommonDataKinds.Email.TYPE_OTHER,
    )

    fun postalType(type: Int, label: String?): String = when (type) {
        ContactKinds.CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inny")
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_HOME -> tr("Dom")
        ContactsContract.CommonDataKinds.StructuredPostal.TYPE_WORK -> tr("Praca")
        else -> tr("Inny")
    }

    fun sipType(type: Int, label: String?): String = postalType(type, label)

    fun websiteType(type: Int, label: String?): String = when (type) {
        ContactKinds.CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Inna")
        ContactsContract.CommonDataKinds.Website.TYPE_HOMEPAGE -> tr("Strona domowa")
        ContactsContract.CommonDataKinds.Website.TYPE_BLOG -> tr("Blog")
        ContactsContract.CommonDataKinds.Website.TYPE_PROFILE -> tr("Profil")
        ContactsContract.CommonDataKinds.Website.TYPE_HOME -> tr("Prywatna")
        ContactsContract.CommonDataKinds.Website.TYPE_WORK -> tr("Praca")
        else -> tr("Inna")
    }

    fun eventType(type: Int, label: String?): String = when (type) {
        ContactKinds.CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Wydarzenie")
        ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY -> tr("Urodziny")
        ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY -> tr("Rocznica")
        else -> tr("Wydarzenie")
    }

    fun relationType(type: Int, label: String?): String = when (type) {
        ContactKinds.CUSTOM -> label?.takeIf { it.isNotBlank() } ?: tr("Osoba")
        ContactsContract.CommonDataKinds.Relation.TYPE_ASSISTANT -> tr("Asystent")
        ContactsContract.CommonDataKinds.Relation.TYPE_BROTHER -> tr("Brat")
        ContactsContract.CommonDataKinds.Relation.TYPE_CHILD -> tr("Dziecko")
        ContactsContract.CommonDataKinds.Relation.TYPE_DOMESTIC_PARTNER -> tr("Partner życiowy")
        ContactsContract.CommonDataKinds.Relation.TYPE_FATHER -> tr("Ojciec")
        ContactsContract.CommonDataKinds.Relation.TYPE_FRIEND -> tr("Przyjaciel")
        ContactsContract.CommonDataKinds.Relation.TYPE_MANAGER -> tr("Przełożony")
        ContactsContract.CommonDataKinds.Relation.TYPE_MOTHER -> tr("Matka")
        ContactsContract.CommonDataKinds.Relation.TYPE_PARENT -> tr("Rodzic")
        ContactsContract.CommonDataKinds.Relation.TYPE_PARTNER -> tr("Partner")
        ContactsContract.CommonDataKinds.Relation.TYPE_REFERRED_BY -> tr("Z polecenia")
        ContactsContract.CommonDataKinds.Relation.TYPE_RELATIVE -> tr("Krewny")
        ContactsContract.CommonDataKinds.Relation.TYPE_SISTER -> tr("Siostra")
        ContactsContract.CommonDataKinds.Relation.TYPE_SPOUSE -> tr("Małżonek")
        else -> tr("Osoba")
    }

    fun imType(protocol: Int, custom: String?): String = ContactKinds.imName(protocol, custom).ifBlank { tr("Komunikator") }

    /** Data wydarzenia: „12 maja 1990” albo „12 maja” bez roku. */
    fun eventDate(raw: String): String {
        val (y, month, day) = ContactKinds.parseDate(raw) ?: return raw
        val cal = Calendar.getInstance().apply { set(y ?: 2000, month - 1, day) }
        return SimpleDateFormat(if (y != null) "d MMMM yyyy" else "d MMMM", I18n.locale).format(cal.time)
    }

    /** Dni do najbliższej rocznicy daty (0 = dziś) i która to rocznica (null bez roku). */
    fun nextOccurrence(raw: String, now: Calendar = Calendar.getInstance()): Pair<Int, Int?>? {
        val (y, month, day) = ContactKinds.parseDate(raw) ?: return null
        val today = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        val next = (today.clone() as Calendar).apply {
            set(Calendar.MONTH, month - 1)
            // 29 lutego w roku nieprzestępnym – 28 lutego.
            set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        if (next.before(today)) {
            next.add(Calendar.YEAR, 1)
            next.set(Calendar.MONTH, month - 1)
            next.set(Calendar.DAY_OF_MONTH, minOf(day, next.getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        val days = ((next.timeInMillis - today.timeInMillis + 3_600_000L) / 86_400_000L).toInt()
        val years = y?.let { next.get(Calendar.YEAR) - it }?.takeIf { it in 0..200 }
        return days to years
    }

    /** „dziś”, „jutro”, „za 12 dni” + „skończy 34 lata” / „34. rocznica”. */
    fun countdown(raw: String, birthday: Boolean): String? {
        val (days, years) = nextOccurrence(raw) ?: return null
        val whenText = when (days) {
            0 -> tr("dziś")
            1 -> tr("jutro")
            else -> tr("za {0} dni", days)
        }
        if (years == null || years == 0) return whenText
        return whenText + " · " + if (birthday) {
            if (days == 0) tr("kończy {0}", yearsText(years)) else tr("skończy {0}", yearsText(years))
        } else {
            tr("{0}. rocznica", years)
        }
    }

    /** „1 rok”, „34 lata”, „25 lat”. */
    private fun yearsText(n: Int): String {
        val m10 = n % 10
        val m100 = n % 100
        return when {
            n == 1 -> tr("{0} rok", n)
            m10 in 2..4 && m100 !in 12..14 -> tr("{0} lata", n)
            else -> tr("{0} lat", n)
        }
    }

    /** Urodziny z zapisu „1990-05-12” lub „--05-12” (bez roku) – „12 maja 1990 (34 lata)”. */
    fun birthday(raw: String): String {
        val m = Regex("^(\\d{4}|-)-?-(\\d{2})-(\\d{2})").find(raw.trim()) ?: return raw
        val y = m.groupValues[1].toIntOrNull()
        val month = m.groupValues[2].toInt()
        val day = m.groupValues[3].toInt()
        val cal = Calendar.getInstance().apply { set(y ?: 2000, month - 1, day) }
        val text = SimpleDateFormat(if (y != null) "d MMMM yyyy" else "d MMMM", I18n.locale).format(cal.time)
        if (y == null) return text
        val now = Calendar.getInstance()
        var age = now.get(Calendar.YEAR) - y
        if (now.get(Calendar.MONTH) + 1 < month || (now.get(Calendar.MONTH) + 1 == month && now.get(Calendar.DAY_OF_MONTH) < day)) age--
        return if (age in 0..130) "$text · ${tr("{0} lat", age)}" else text
    }
}
