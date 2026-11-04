package com.blacksingularities.opencall.ai

import com.blacksingularities.opencall.i18n.tr
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.time.Instant

/** Błąd z czytelnym komunikatem dla użytkownika. */
class AiException(message: String) : Exception(message)

/** Prosty klient JSON; wywołania blokujące, zawsze z wątku w tle. */
internal object AiHttp {
    fun request(method: String, url: String, headers: Map<String, String>, body: JSONObject? = null): String {
        val c = URL(url).openConnection() as HttpURLConnection
        try {
            c.requestMethod = method
            c.connectTimeout = 15_000
            c.readTimeout = 30_000
            c.setRequestProperty("Accept", "application/json")
            headers.forEach { (k, v) -> c.setRequestProperty(k, v) }
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/json")
                c.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code in 200..299) return c.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            throw when (code) {
                401, 403 -> AiException(tr("Platforma odrzuciła klucz API"))
                404 -> AiException(tr("Nie znaleziono agenta na platformie"))
                429 -> AiException(tr("Platforma ogranicza liczbę zapytań – spróbuj za chwilę"))
                else -> AiException(tr("Błąd platformy (kod {0})", code))
            }
        } finally {
            c.disconnect()
        }
    }
}

/** To, czego aplikacja potrzebuje od platformy agentów głosowych. */
internal interface AgentPlatform {
    /** Numery na koncie – pierwszy test klucza i wybór numeru do przekierowania. */
    fun lines(): List<AgentLine>

    /** Wysyła konfigurację do agenta i zwraca jego identyfikator (Vapi tworzy go, gdy go nie ma). */
    fun sync(a: AiScreening): String

    /** Rozmowy agenta od podanego momentu. */
    fun calls(a: AiScreening, since: Long): List<AgentCall>

    companion object {
        fun of(provider: AiProvider, key: String, customUrl: String): AgentPlatform = when (provider) {
            AiProvider.VAPI -> Vapi(key)
            AiProvider.RETELL -> Retell(key)
            AiProvider.ELEVENLABS -> ElevenLabs(key)
            AiProvider.CUSTOM -> CustomServer(key, customUrl)
        }
    }
}

private fun encoded(s: String) = URLEncoder.encode(s, "UTF-8")

private class Vapi(key: String) : AgentPlatform {
    private val base = "https://api.vapi.ai"
    private val auth = mapOf("Authorization" to "Bearer $key")

    override fun lines() = AiPayloads.vapiLines(AiHttp.request("GET", "$base/phone-number?limit=50", auth))

    override fun sync(a: AiScreening): String {
        var id = a.agentId
        if (id.isBlank()) {
            val created = AiHttp.request("POST", "$base/assistant", auth, AiPayloads.vapiAssistant(a, null))
            id = JSONObject(created).getString("id")
        } else {
            val current = JSONObject(AiHttp.request("GET", "$base/assistant/$id", auth))
            AiHttp.request("PATCH", "$base/assistant/$id", auth, AiPayloads.vapiAssistant(a, current.optJSONObject("model")))
        }
        if (a.lineId.isNotBlank()) {
            AiHttp.request("PATCH", "$base/phone-number/${a.lineId}", auth, JSONObject().put("assistantId", id))
        }
        return id
    }

    override fun calls(a: AiScreening, since: Long): List<AgentCall> {
        val from = encoded(Instant.ofEpochMilli(since).toString())
        return AiPayloads.vapiCalls(AiHttp.request("GET", "$base/call?assistantId=${encoded(a.agentId)}&limit=20&createdAtGe=$from", auth))
    }
}

private class Retell(key: String) : AgentPlatform {
    private val base = "https://api.retellai.com"
    private val auth = mapOf("Authorization" to "Bearer $key")

    override fun lines() = AiPayloads.retellLines(AiHttp.request("GET", "$base/v2/list-phone-numbers", auth))

    override fun sync(a: AiScreening): String {
        if (a.agentId.isBlank()) throw AiException(tr("Przypisz agenta do numeru w panelu platformy"))
        val agent = AiHttp.request("GET", "$base/get-agent/${a.agentId}", auth)
        val llm = AiPayloads.retellLlmId(agent)
            ?: throw AiException(tr("Ten agent nie używa Retell LLM – ustaw go w panelu platformy"))
        AiHttp.request("PATCH", "$base/update-retell-llm/$llm", auth, AiPayloads.retellLlm(a))
        AiHttp.request("PATCH", "$base/update-agent/${a.agentId}", auth, AiPayloads.retellAgent(a))
        return a.agentId
    }

    override fun calls(a: AiScreening, since: Long): List<AgentCall> {
        val body = JSONObject()
            .put("filter_criteria", JSONObject().put("agent", JSONArray().put(JSONObject().put("agent_id", a.agentId))))
            .put("sort_order", "descending")
            .put("limit", 20)
        val calls = AiPayloads.retellCalls(AiHttp.request("POST", "$base/v3/list-calls", auth, body)).filter { it.startedAt >= since }
        if (!a.keepTranscripts) return calls
        // Lista nie zawiera transkrypcji – dobieramy ją osobno dla zakończonych rozmów.
        return calls.map { c ->
            if (!c.ended) {
                c
            } else {
                val full = runCatching { AiPayloads.retellTranscript(AiHttp.request("GET", "$base/v2/get-call/${c.id}", auth)) }.getOrNull()
                c.copy(transcript = full ?: c.transcript)
            }
        }
    }
}

private class ElevenLabs(key: String) : AgentPlatform {
    private val base = "https://api.elevenlabs.io/v1/convai"
    private val auth = mapOf("xi-api-key" to key)

    override fun lines() = AiPayloads.elevenLines(AiHttp.request("GET", "$base/phone-numbers", auth))

    override fun sync(a: AiScreening): String {
        if (a.agentId.isBlank()) throw AiException(tr("Przypisz agenta do numeru w panelu platformy"))
        AiHttp.request("PATCH", "$base/agents/${a.agentId}", auth, AiPayloads.elevenAgent(a))
        return a.agentId
    }

    override fun calls(a: AiScreening, since: Long): List<AgentCall> {
        val list = AiHttp.request(
            "GET",
            "$base/conversations?agent_id=${encoded(a.agentId)}&call_start_after_unix=${since / 1000}&page_size=10",
            auth,
        )
        return AiPayloads.elevenConversationIds(list).mapNotNull { id ->
            runCatching { AiPayloads.elevenCall(AiHttp.request("GET", "$base/conversations/$id", auth)) }.getOrNull()
        }
    }
}

/** Własny serwer: GET zwraca rozmowy, POST przyjmuje konfigurację (format opisany w README). */
private class CustomServer(key: String, private val url: String) : AgentPlatform {
    private val auth = mapOf("Authorization" to "Bearer $key")

    private fun target(): String {
        if (!url.startsWith("https://")) throw AiException(tr("Adres serwera musi zaczynać się od https://"))
        return url
    }

    override fun lines(): List<AgentLine> {
        AiPayloads.customCalls(AiHttp.request("GET", "${target()}?since=${System.currentTimeMillis()}", auth))
        return emptyList()
    }

    override fun sync(a: AiScreening): String {
        AiHttp.request("POST", target(), auth, AiPayloads.customConfig(a))
        return a.agentId.ifBlank { "custom" }
    }

    override fun calls(a: AiScreening, since: Long) =
        AiPayloads.customCalls(AiHttp.request("GET", "${target()}?since=$since", auth))
}
