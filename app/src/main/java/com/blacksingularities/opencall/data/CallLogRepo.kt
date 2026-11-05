package com.blacksingularities.opencall.data

import android.content.ContentValues
import android.content.Context
import android.provider.CallLog.Calls
import java.util.Calendar

/** Rejestr połączeń: odczyt, oznaczanie nieodebranych jako przeczytanych, usuwanie, statystyki. */
class CallLogRepo(private val context: Context) {
    private val cr get() = context.contentResolver

    fun load(limit: Int = 2000, number: String? = null): List<CallEntry> {
        val out = ArrayList<CallEntry>()
        val projection = arrayOf(
            Calls._ID, Calls.NUMBER, Calls.CACHED_NAME, Calls.CACHED_PHOTO_URI, Calls.TYPE, Calls.DATE, Calls.DURATION,
            Calls.PHONE_ACCOUNT_ID, Calls.GEOCODED_LOCATION, Calls.NUMBER_PRESENTATION, Calls.NEW, Calls.FEATURES,
            Calls.VOICEMAIL_URI,
        )
        cr.query(Calls.CONTENT_URI, projection, null, null, "${Calls.DATE} DESC")?.use { c ->
            while (c.moveToNext() && out.size < limit) {
                val num = c.getString(1) ?: ""
                if (number != null && !PhoneText.sameNumber(num, number)) continue
                out.add(
                    CallEntry(
                        id = c.getLong(0),
                        number = num,
                        name = c.getString(2)?.takeIf { it.isNotBlank() },
                        photoUri = c.getString(3),
                        kind = CallKind.of(c.getInt(4)),
                        date = c.getLong(5),
                        durationSec = c.getLong(6),
                        accountId = c.getString(7),
                        location = c.getString(8)?.takeIf { it.isNotBlank() },
                        hidden = c.getInt(9) != Calls.PRESENTATION_ALLOWED || num.isBlank(),
                        isNew = c.getInt(10) != 0,
                        video = (c.getInt(11) and Calls.FEATURES_VIDEO) != 0,
                        voicemailUri = c.getString(12)?.takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
        return out
    }

    /** Liczba nowych nieodebranych – plakietka na zakładce „Ostatnie”. */
    fun unseenMissed(): Int = runCatching {
        cr.query(Calls.CONTENT_URI, arrayOf(Calls._ID), "${Calls.TYPE}=? AND ${Calls.NEW}=1", arrayOf(Calls.MISSED_TYPE.toString()), null)?.use { it.count } ?: 0
    }.getOrDefault(0)

    fun markMissedSeen() {
        runCatching {
            cr.update(
                Calls.CONTENT_URI,
                ContentValues().apply {
                    put(Calls.NEW, 0)
                    put(Calls.IS_READ, 1)
                },
                "${Calls.TYPE}=? AND ${Calls.NEW}=1", arrayOf(Calls.MISSED_TYPE.toString()),
            )
        }
    }

    fun delete(ids: Collection<Long>) {
        if (ids.isEmpty()) return
        ids.chunked(200).forEach { chunk ->
            cr.delete(Calls.CONTENT_URI, "${Calls._ID} IN (${chunk.joinToString(",")})", null)
        }
    }

    fun deleteNumber(number: String) {
        delete(load(number = number).map { it.id })
    }

    fun clear() {
        cr.delete(Calls.CONTENT_URI, null, null)
    }

    companion object {
        /** Eksport rejestru do CSV: średnik i BOM, żeby Excel otwierał plik bez kreatora importu. */
        fun toCsv(entries: List<CallEntry>, kindLabel: (CallKind) -> String, dateFormat: (Long) -> String): String = buildString {
            append('﻿')
            append("Data;Numer;Nazwa;Rodzaj;Czas (s);Lokalizacja\r\n")
            fun q(s: String?) = "\"" + (s ?: "").replace("\"", "\"\"") + "\""
            for (e in entries) {
                append(q(dateFormat(e.date))).append(';')
                append(q(e.number)).append(';')
                append(q(e.name)).append(';')
                append(q(kindLabel(e.kind))).append(';')
                append(e.durationSec).append(';')
                append(q(e.location)).append("\r\n")
            }
        }

        /** Statystyki z wpisów od [since]; [nameOf] – nazwa do rankingu (kontakt albo numer). */
        fun stats(entries: List<CallEntry>, since: Long, nameOf: (CallEntry) -> String): CallStats {
            val list = entries.filter { it.date >= since }
            val cal = Calendar.getInstance()
            val weekday = IntArray(7)
            val hour = IntArray(24)
            val counts = HashMap<String, Int>()
            val times = HashMap<String, Long>()
            list.forEach { e ->
                cal.timeInMillis = e.date
                // Poniedziałek = 0 … niedziela = 6.
                weekday[(cal.get(Calendar.DAY_OF_WEEK) + 5) % 7]++
                hour[cal.get(Calendar.HOUR_OF_DAY)]++
                if (!e.hidden) {
                    val n = nameOf(e)
                    counts[n] = (counts[n] ?: 0) + 1
                    times[n] = (times[n] ?: 0L) + e.durationSec
                }
            }
            val talk = list.filter { it.kind == CallKind.INCOMING || it.kind == CallKind.OUTGOING }
            return CallStats(
                total = list.size,
                incoming = list.count { it.kind == CallKind.INCOMING },
                outgoing = list.count { it.kind == CallKind.OUTGOING },
                missed = list.count { it.kind == CallKind.MISSED || it.kind == CallKind.REJECTED },
                talkSec = talk.sumOf { it.durationSec },
                inSec = talk.filter { it.kind == CallKind.INCOMING }.sumOf { it.durationSec },
                outSec = talk.filter { it.kind == CallKind.OUTGOING }.sumOf { it.durationSec },
                longest = talk.maxByOrNull { it.durationSec }?.takeIf { it.durationSec > 0 },
                top = counts.entries.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                topTime = times.entries.filter { it.value > 0 }.sortedByDescending { it.value }.take(5).map { it.key to it.value },
                byWeekday = weekday,
                byHour = hour,
            )
        }

        /** Najczęstsze numery z rejestru (do sekcji „Często”), pomijając podane. */
        fun frequent(entries: List<CallEntry>, since: Long, limit: Int): List<String> =
            entries.asSequence()
                .filter { it.date >= since && !it.hidden && it.number.isNotBlank() && it.kind != CallKind.BLOCKED }
                .groupBy { PhoneText.key(it.number) }
                .entries.sortedByDescending { it.value.size }
                .filter { it.value.size >= 2 }
                .take(limit)
                .map { it.value.first().number }
    }
}
