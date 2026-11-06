package com.blacksingularities.opencall.data

import android.content.Context
import android.provider.ContactsContract.CommonDataKinds.Event
import android.provider.ContactsContract.Data
import java.util.Calendar

/** Data z kontaktu (urodziny, rocznica, własna) z dniami do najbliższego wystąpienia. */
data class ContactEvent(
    val contactId: Long,
    val name: String,
    val photoUri: String?,
    val date: String,
    val type: Int,
    val label: String?,
    /** 0 = dziś, 1 = jutro… */
    val days: Int,
    /** Która to rocznica / ile lat skończy (null, gdy data bez roku). */
    val years: Int?,
) {
    val birthday: Boolean get() = type == Event.TYPE_BIRTHDAY
}

/** Odczyt dat ze wszystkich kontaktów – do przypomnień i paska w Ulubionych. */
object ContactEvents {
    /** Wydarzenia w ciągu najbliższych [withinDays] dni (0 = tylko dziś), od najbliższych. */
    fun upcoming(context: Context, withinDays: Int, now: Calendar = Calendar.getInstance()): List<ContactEvent> {
        val out = ArrayList<ContactEvent>()
        val seen = HashSet<String>()
        runCatching {
            context.contentResolver.query(
                Data.CONTENT_URI,
                arrayOf(Data.CONTACT_ID, Data.DISPLAY_NAME, Data.PHOTO_THUMBNAIL_URI, Event.START_DATE, Event.TYPE, Event.LABEL),
                "${Data.MIMETYPE}=?", arrayOf(Event.CONTENT_ITEM_TYPE), null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(0)
                    val date = c.getString(3)?.trim().orEmpty()
                    if (date.isEmpty()) continue
                    val type = c.getInt(4)
                    // Ten sam kontakt z kilku kont – jedno przypomnienie.
                    if (!seen.add("$id|$type|$date")) continue
                    val (days, years) = next(date, now) ?: continue
                    if (days > withinDays) continue
                    out.add(ContactEvent(id, c.getString(1).orEmpty(), c.getString(2), date, type, c.getString(5), days, years))
                }
            }
        }
        return out.sortedWith(compareBy({ it.days }, { !it.birthday }, { it.name }))
    }

    /** Dni do najbliższej rocznicy daty (0 = dziś) i która to rocznica (null bez roku). */
    fun next(raw: String, now: Calendar = Calendar.getInstance()): Pair<Int, Int?>? {
        val (y, month, day) = ContactKinds.parseDate(raw) ?: return null
        val today = (now.clone() as Calendar).apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        fun inYear(year: Int): Calendar = (today.clone() as Calendar).apply {
            set(Calendar.DAY_OF_MONTH, 1)
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month - 1)
            // 29 lutego w roku nieprzestępnym – 28 lutego.
            set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
        }
        var next = inYear(today.get(Calendar.YEAR))
        if (next.before(today)) next = inYear(today.get(Calendar.YEAR) + 1)
        val days = ((next.timeInMillis - today.timeInMillis + 3_600_000L) / 86_400_000L).toInt()
        val years = y?.let { next.get(Calendar.YEAR) - it }?.takeIf { it in 0..200 }
        return days to years
    }
}
