package com.blacksingularities.opencall.ai

import com.blacksingularities.opencall.data.PhoneText
import org.json.JSONArray
import org.json.JSONObject

/** Ocena rozmowy przez agenta. */
enum class AiVerdict {
    IMPORTANT, MESSAGE, SPAM, UNKNOWN;

    companion object {
        fun parse(value: String?): AiVerdict = when (value?.trim()?.lowercase()) {
            "important", "wazne", "ważne" -> IMPORTANT
            "message", "wiadomosc", "wiadomość" -> MESSAGE
            "spam", "unwanted" -> SPAM
            else -> UNKNOWN
        }
    }
}

/** Numer na platformie, na który można przekierować połączenia. */
data class AgentLine(val id: String, val number: String, val agentId: String, val label: String)

/** Rozmowa agenta w jednolitej postaci, niezależnie od platformy. */
data class AgentCall(
    val id: String,
    val number: String,
    val startedAt: Long,
    val endedAt: Long,
    val ended: Boolean,
    val summary: String?,
    val verdict: AiVerdict,
    val caller: String?,
    val transcript: String?,
    val transferred: Boolean,
)

enum class AiStatus { PENDING, LIVE, DONE, MISSED }

/** Wpis w historii asystenta: od przekazania połączenia po streszczenie. */
data class AiRecord(
    val id: String,
    val at: Long,
    val number: String,
    val hidden: Boolean = false,
    val status: AiStatus = AiStatus.PENDING,
    val callId: String? = null,
    val summary: String? = null,
    val verdict: AiVerdict = AiVerdict.UNKNOWN,
    val caller: String? = null,
    val transcript: String? = null,
    val transferred: Boolean = false,
) {
    val open: Boolean get() = status == AiStatus.PENDING || status == AiStatus.LIVE

    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("at", at)
        put("number", number)
        put("hidden", hidden)
        put("status", status.name)
        put("callId", callId ?: "")
        put("summary", summary ?: "")
        put("verdict", verdict.name)
        put("caller", caller ?: "")
        put("transcript", transcript ?: "")
        put("transferred", transferred)
    }

    companion object {
        fun fromJson(o: JSONObject) = AiRecord(
            id = o.optString("id"),
            at = o.optLong("at"),
            number = o.optString("number"),
            hidden = o.optBoolean("hidden"),
            status = AiStatus.entries.firstOrNull { it.name == o.optString("status") } ?: AiStatus.DONE,
            callId = o.optString("callId").ifBlank { null },
            summary = o.optString("summary").ifBlank { null },
            verdict = AiVerdict.entries.firstOrNull { it.name == o.optString("verdict") } ?: AiVerdict.UNKNOWN,
            caller = o.optString("caller").ifBlank { null },
            transcript = o.optString("transcript").ifBlank { null },
            transferred = o.optBoolean("transferred"),
        )

        fun encodeList(list: List<AiRecord>): String = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()

        fun decodeList(raw: String?): List<AiRecord> {
            if (raw.isNullOrBlank()) return emptyList()
            return runCatching {
                val a = JSONArray(raw)
                (0 until a.length()).map { fromJson(a.getJSONObject(it)) }
            }.getOrDefault(emptyList())
        }
    }
}

/** Łączy to, co telefon przekazał, z tym, co zgłasza platforma. */
object AiMatch {
    /** Bez śladu rozmowy po tym czasie uznajemy, że połączenie nie dotarło do agenta. */
    const val PENDING_TIMEOUT_MS = 150_000L
    /** Tyle po końcu rozmowy czekamy na streszczenie, zanim pokażemy wynik bez niego. */
    const val SUMMARY_GRACE_MS = 30_000L
    /** Rozmowa bez zakończenia po tym czasie jest porzucana (zawieszony wpis nie może zostać). */
    const val LIVE_TIMEOUT_MS = 20 * 60_000L
    const val MAX_RECORDS = 200

    data class Result(val records: List<AiRecord>, val finished: List<AiRecord>)

    private fun isFinal(c: AgentCall, now: Long) =
        c.ended && (c.summary != null || now - c.endedAt >= SUMMARY_GRACE_MS)

    /** Numer u operatora bywa podmieniany przy przekierowaniu, więc pusty lub własny też pasuje. */
    private fun fits(r: AiRecord, c: AgentCall, own: String): Boolean {
        if (r.status != AiStatus.PENDING) return false
        if (c.startedAt < r.at - 60_000L || c.startedAt > r.at + PENDING_TIMEOUT_MS) return false
        return r.hidden || c.number.isBlank() || PhoneText.sameNumber(c.number, r.number) || PhoneText.sameNumber(c.number, own)
    }

    fun merge(
        records: List<AiRecord>,
        calls: List<AgentCall>,
        now: Long,
        own: String,
        keepTranscripts: Boolean,
    ): Result {
        val out = records.toMutableList()
        val finished = ArrayList<AiRecord>()
        for (c in calls.sortedBy { it.startedAt }) {
            var i = out.indexOfFirst { it.callId == c.id }
            if (i < 0) {
                i = out.withIndex()
                    .filter { fits(it.value, c, own) }
                    .minByOrNull { it.value.at }?.index ?: -1
            }
            val base = if (i >= 0) out[i] else AiRecord(id = c.id, at = c.startedAt, number = c.number)
            if (base.status == AiStatus.DONE || base.status == AiStatus.MISSED && base.callId == c.id) continue
            val final = isFinal(c, now)
            val updated = base.copy(
                status = if (final) AiStatus.DONE else AiStatus.LIVE,
                callId = c.id,
                number = base.number.ifBlank { c.number },
                summary = c.summary ?: base.summary,
                verdict = if (c.verdict != AiVerdict.UNKNOWN) c.verdict else if (c.transferred) AiVerdict.IMPORTANT else base.verdict,
                caller = c.caller ?: base.caller,
                transcript = if (keepTranscripts) c.transcript ?: base.transcript else null,
                transferred = c.transferred || base.transferred,
            )
            if (i >= 0) out[i] = updated else out.add(0, updated)
            if (final) finished.add(updated)
        }
        for (i in out.indices) {
            val r = out[i]
            val expired = when (r.status) {
                AiStatus.PENDING -> now - r.at > PENDING_TIMEOUT_MS
                AiStatus.LIVE -> now - r.at > LIVE_TIMEOUT_MS
                else -> false
            }
            if (expired) {
                out[i] = r.copy(status = if (r.status == AiStatus.PENDING) AiStatus.MISSED else AiStatus.DONE)
                finished.add(out[i])
            }
        }
        return Result(out.sortedByDescending { it.at }.take(MAX_RECORDS), finished)
    }

    /** Użytkownik odebrał sam, zanim agent przejął połączenie – nie ma na co czekać. */
    fun withoutPending(records: List<AiRecord>, number: String): List<AiRecord> =
        records.filterNot { it.status == AiStatus.PENDING && PhoneText.sameNumber(it.number, number) }

    /** Czy ten numer był przed chwilą przekazany asystentowi (druga próba = dzwoni normalnie). */
    fun isRepeat(records: List<AiRecord>, number: String, minutes: Int, now: Long): Boolean {
        if (minutes <= 0 || number.isBlank()) return false
        return records.any { PhoneText.sameNumber(it.number, number) && now - it.at in 0..minutes * 60_000L }
    }
}
