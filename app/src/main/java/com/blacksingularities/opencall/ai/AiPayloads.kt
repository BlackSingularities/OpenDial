package com.blacksingularities.opencall.ai

import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant

/** Żądania do platform i odczyt ich odpowiedzi – czysty JSON, bez sieci, testowany jednostkowo. */
object AiPayloads {
    private fun String?.clean(): String? = this?.trim()?.takeIf { it.isNotEmpty() && it != "null" }

    private fun JSONObject.text(key: String): String? = if (isNull(key)) null else optString(key).clean()

    private fun iso(value: String?): Long =
        value.clean()?.let { runCatching { Instant.parse(it).toEpochMilli() }.getOrNull() } ?: 0L

    private fun arrayOf(raw: String, key: String): JSONArray {
        val t = raw.trim()
        return if (t.startsWith("[")) JSONArray(t) else JSONObject(t).optJSONArray(key) ?: JSONArray()
    }

    private fun objects(a: JSONArray): List<JSONObject> = (0 until a.length()).mapNotNull { a.optJSONObject(it) }

    private fun verdictSchema() = JSONObject().apply {
        put("type", "object")
        put(
            "properties",
            JSONObject().apply {
                put(
                    "verdict",
                    JSONObject().apply {
                        put("type", "string")
                        put("enum", JSONArray(listOf("important", "message", "spam")))
                        put("description", AiPrompt.verdictDescription)
                    },
                )
                put("caller", JSONObject().apply { put("type", "string"); put("description", "Name or company of the caller, if given.") })
            },
        )
        put("required", JSONArray(listOf("verdict")))
    }

    // ---- Vapi ---------------------------------------------------------------------------------

    /**
     * Asystent Vapi. [existing] to dotychczasowy obiekt `model`: pusty wybór modelu w aplikacji
     * zostawia ten z panelu. Przełączenie idzie z numerem agenta jako nadawcą, bo inaczej
     * ScreeningService uznałby je za nieznany numer i odesłał z powrotem do agenta.
     */
    fun vapiAssistant(a: AiScreening, existing: JSONObject?): JSONObject {
        val model = existing?.let { JSONObject(it.toString()) } ?: JSONObject()
        val chosen = a.model.trim()
        if (chosen.contains('/')) {
            model.put("provider", chosen.substringBefore('/'))
            model.put("model", chosen.substringAfter('/'))
        } else if (!model.has("provider")) {
            model.put("provider", "openai")
            model.put("model", "gpt-5.4-mini")
        }
        model.put("temperature", 0.3)
        model.put(
            "messages",
            JSONArray().put(JSONObject().put("role", "system").put("content", AiPrompt.system(a))),
        )
        val tools = JSONArray()
        model.optJSONArray("tools")?.let { old ->
            objects(old).filter { it.optString("type") !in listOf("transferCall", "endCall") }.forEach { tools.put(it) }
        }
        tools.put(JSONObject().put("type", "endCall"))
        tools.put(
            JSONObject().apply {
                put("type", "transferCall")
                put(
                    "destinations",
                    JSONArray().put(
                        JSONObject().apply {
                            put("type", "number")
                            put("number", AiRules.e164(a.ownNumber))
                            put("callerId", "{{phoneNumber.number}}")
                            put("description", "Connect an important caller to the phone owner.")
                            put(
                                "transferPlan",
                                JSONObject().apply {
                                    put("mode", "warm-transfer-say-summary")
                                    put(
                                        "summaryPlan",
                                        JSONObject().apply {
                                            put("enabled", true)
                                            put(
                                                "messages",
                                                JSONArray()
                                                    .put(JSONObject().put("role", "system").put("content", AiPrompt.handoffInstruction(a)))
                                                    .put(JSONObject().put("role", "user").put("content", "Transcript:\n\n{{transcript}}")),
                                            )
                                        },
                                    )
                                },
                            )
                        },
                    ),
                )
            },
        )
        model.put("tools", tools)

        return JSONObject().apply {
            put("name", "OpenDial")
            put("firstMessage", AiPrompt.greeting(a))
            put("firstMessageMode", "assistant-speaks-first")
            put("maxDurationSeconds", a.maxSeconds + 30)
            put("transcriber", JSONObject().put("provider", "deepgram").put("model", "nova-2").put("language", a.language))
            put("model", model)
            val voice = a.voice.trim()
            if (voice.contains('/')) {
                put("voice", JSONObject().put("provider", voice.substringBefore('/')).put("voiceId", voice.substringAfter('/')))
            }
            put(
                "analysisPlan",
                JSONObject().apply {
                    put(
                        "summaryPlan",
                        JSONObject().apply {
                            put("enabled", true)
                            put(
                                "messages",
                                JSONArray()
                                    .put(JSONObject().put("role", "system").put("content", AiPrompt.summaryInstruction(a)))
                                    .put(JSONObject().put("role", "user").put("content", "Transcript:\n\n{{transcript}}")),
                            )
                        },
                    )
                    put("structuredDataPlan", JSONObject().put("enabled", true).put("schema", verdictSchema()))
                },
            )
            put("artifactPlan", JSONObject().put("recordingEnabled", a.record))
        }
    }

    fun vapiLines(raw: String): List<AgentLine> = objects(arrayOf(raw, "results")).mapNotNull { o ->
        val number = o.text("number") ?: return@mapNotNull null
        AgentLine(o.text("id") ?: "", number, o.text("assistantId") ?: "", o.text("name") ?: number)
    }

    fun vapiCalls(raw: String): List<AgentCall> = objects(arrayOf(raw, "results"))
        .filter { (it.text("type") ?: "inboundPhoneCall") == "inboundPhoneCall" }
        .mapNotNull { o ->
            val id = o.text("id") ?: return@mapNotNull null
            val analysis = o.optJSONObject("analysis")
            val data = analysis?.optJSONObject("structuredData")
            val status = o.text("status")
            val started = iso(o.text("startedAt")).takeIf { it > 0 } ?: iso(o.text("createdAt"))
            AgentCall(
                id = id,
                number = o.optJSONObject("customer")?.text("number") ?: "",
                startedAt = started,
                endedAt = iso(o.text("endedAt")),
                ended = status == "ended",
                summary = analysis?.text("summary"),
                verdict = AiVerdict.parse(data?.text("verdict")),
                caller = data?.text("caller"),
                transcript = o.optJSONObject("artifact")?.text("transcript") ?: o.text("transcript"),
                transferred = status == "forwarding" || o.text("endedReason") == "assistant-forwarded-call",
            )
        }

    // ---- Retell -------------------------------------------------------------------------------

    fun retellLlm(a: AiScreening): JSONObject = JSONObject().apply {
        put("general_prompt", AiPrompt.system(a))
        put("begin_message", AiPrompt.greeting(a))
        val chosen = a.model.trim()
        if (chosen.isNotEmpty()) put("model", chosen.substringAfter('/'))
        put(
            "general_tools",
            JSONArray()
                .put(
                    JSONObject().apply {
                        put("type", "end_call")
                        put("name", "end_call")
                        put("description", "End the call once it is finished.")
                    },
                )
                .put(
                    JSONObject().apply {
                        put("type", "transfer_call")
                        put("name", "transfer_to_owner")
                        put("description", "Connect an important caller to the phone owner.")
                        put(
                            "transfer_destination",
                            JSONObject().put("type", "predefined").put("number", AiRules.e164(a.ownNumber)),
                        )
                        put(
                            "transfer_option",
                            JSONObject().apply {
                                put("type", "warm_transfer")
                                put("show_transferee_as_caller", false)
                                put(
                                    "private_handoff_option",
                                    JSONObject().put("type", "prompt").put("prompt", AiPrompt.handoffInstruction(a)),
                                )
                            },
                        )
                    },
                ),
        )
    }

    fun retellAgent(a: AiScreening): JSONObject = JSONObject().apply {
        put("language", AiPrompt.locale(a))
        put("max_call_duration_ms", (a.maxSeconds + 30).coerceAtLeast(60) * 1000)
        if (a.voice.isNotBlank()) put("voice_id", a.voice.trim())
        put(
            "post_call_analysis_data",
            JSONArray()
                .put(
                    JSONObject().apply {
                        put("type", "enum")
                        put("name", "verdict")
                        put("description", AiPrompt.verdictDescription)
                        put("choices", JSONArray(listOf("important", "message", "spam")))
                    },
                )
                .put(
                    JSONObject().apply {
                        put("type", "string")
                        put("name", "caller")
                        put("description", "Name or company of the caller, if given.")
                    },
                ),
        )
    }

    fun retellLlmId(rawAgent: String): String? = JSONObject(rawAgent).optJSONObject("response_engine")?.text("llm_id")

    fun retellLines(raw: String): List<AgentLine> = objects(arrayOf(raw, "items")).mapNotNull { o ->
        val number = o.text("phone_number") ?: return@mapNotNull null
        val agent = o.optJSONArray("inbound_agents")?.optJSONObject(0)?.text("agent_id") ?: o.text("inbound_agent_id") ?: ""
        AgentLine(number, number, agent, o.text("nickname") ?: o.text("phone_number_pretty") ?: number)
    }

    fun retellCalls(raw: String): List<AgentCall> = objects(arrayOf(raw, "items"))
        .filter { (it.text("call_type") ?: "phone_call") == "phone_call" }
        .mapNotNull { o ->
            val id = o.text("call_id") ?: return@mapNotNull null
            val analysis = o.optJSONObject("call_analysis")
            val custom = analysis?.optJSONObject("custom_analysis_data")
            val status = o.text("call_status")
            AgentCall(
                id = id,
                number = o.text("from_number") ?: "",
                startedAt = o.optLong("start_timestamp"),
                endedAt = o.optLong("end_timestamp"),
                ended = status == "ended" || status == "error" || status == "not_connected",
                summary = analysis?.text("call_summary"),
                verdict = AiVerdict.parse(custom?.text("verdict")),
                caller = custom?.text("caller"),
                transcript = o.text("transcript"),
                transferred = o.text("disconnection_reason") == "call_transfer",
            )
        }

    fun retellTranscript(raw: String): String? = JSONObject(raw).text("transcript")

    // ---- ElevenLabs ---------------------------------------------------------------------------

    fun elevenAgent(a: AiScreening): JSONObject {
        val transfer = JSONObject().apply {
            put("name", "transfer_to_number")
            put("description", "Connect an important caller to the phone owner.")
            put(
                "params",
                JSONObject().apply {
                    put("system_tool_type", "transfer_to_number")
                    put(
                        "transfers",
                        JSONArray().put(
                            JSONObject().apply {
                                put(
                                    "transfer_destination",
                                    JSONObject().put("type", "phone").put("phone_number", AiRules.e164(a.ownNumber)),
                                )
                                put("condition", "The caller is important according to the rules.")
                                put("transfer_type", "conference")
                            },
                        ),
                    )
                },
            )
        }
        val endCall = JSONObject().apply {
            put("name", "end_call")
            put("params", JSONObject().put("system_tool_type", "end_call"))
        }
        val prompt = JSONObject().apply {
            put("prompt", AiPrompt.system(a))
            if (a.model.isNotBlank()) put("llm", a.model.trim().substringAfter('/'))
            put("built_in_tools", JSONObject().put("end_call", endCall).put("transfer_to_number", transfer))
        }
        val config = JSONObject().apply {
            put(
                "agent",
                JSONObject().apply {
                    put("first_message", AiPrompt.greeting(a))
                    put("language", a.language)
                    put("prompt", prompt)
                },
            )
            put("conversation", JSONObject().put("max_duration_seconds", a.maxSeconds + 30))
            if (a.voice.isNotBlank()) put("tts", JSONObject().put("voice_id", a.voice.trim()))
        }
        val collection = JSONObject()
            .put(
                "verdict",
                JSONObject().apply {
                    put("type", "string")
                    put("description", AiPrompt.verdictDescription)
                    put("enum", JSONArray(listOf("important", "message", "spam")))
                },
            )
            .put(
                "caller",
                JSONObject().put("type", "string").put("description", "Name or company of the caller, if given."),
            )
        return JSONObject()
            .put("conversation_config", config)
            .put("platform_settings", JSONObject().put("data_collection", collection))
    }

    fun elevenLines(raw: String): List<AgentLine> = objects(arrayOf(raw, "phone_numbers")).mapNotNull { o ->
        val number = o.text("phone_number") ?: return@mapNotNull null
        AgentLine(
            o.text("phone_number_id") ?: number,
            number,
            o.optJSONObject("assigned_agent")?.text("agent_id") ?: "",
            o.text("label") ?: number,
        )
    }

    fun elevenConversationIds(raw: String): List<String> =
        objects(arrayOf(raw, "conversations")).mapNotNull { it.text("conversation_id") }

    private fun collected(results: JSONObject?, key: String): String? {
        val v = results?.opt(key) ?: return null
        return if (v is JSONObject) v.text("value") else v.toString().clean()
    }

    /** Szczegóły rozmowy: lista ich nie podaje numeru dzwoniącego ani transkrypcji. */
    fun elevenCall(raw: String): AgentCall? {
        val o = JSONObject(raw)
        val id = o.text("conversation_id") ?: return null
        val meta = o.optJSONObject("metadata")
        val analysis = o.optJSONObject("analysis")
        val results = analysis?.optJSONObject("data_collection_results")
        val status = o.text("status")
        val started = (meta?.optLong("start_time_unix_secs") ?: 0L) * 1000L
        val turns = o.optJSONArray("transcript")?.let { objects(it) }.orEmpty()
        val transcript = turns.mapNotNull { t ->
            t.text("message")?.let { (if (t.text("role") == "agent") "AI: " else "Caller: ") + it }
        }.joinToString("\n").clean()
        val toolCalls = turns.flatMap { t -> t.optJSONArray("tool_calls")?.let { objects(it) }.orEmpty() }
        return AgentCall(
            id = id,
            number = meta?.optJSONObject("phone_call")?.text("external_number") ?: "",
            startedAt = started,
            endedAt = started + (meta?.optLong("call_duration_secs") ?: 0L) * 1000L,
            ended = status == "done" || status == "failed",
            summary = analysis?.text("transcript_summary"),
            verdict = AiVerdict.parse(collected(results, "verdict")),
            caller = collected(results, "caller"),
            transcript = transcript,
            transferred = toolCalls.any { it.text("tool_name") == "transfer_to_number" },
        )
    }

    // ---- Własny serwer ------------------------------------------------------------------------

    fun customConfig(a: AiScreening): JSONObject = JSONObject().apply {
        put("systemPrompt", AiPrompt.system(a))
        put("firstMessage", AiPrompt.greeting(a))
        put("transferNumber", AiRules.e164(a.ownNumber))
        put("language", a.language)
        put("maxSeconds", a.maxSeconds)
        put("summaryInstruction", AiPrompt.summaryInstruction(a))
    }

    fun customCalls(raw: String): List<AgentCall> = objects(arrayOf(raw, "calls")).mapNotNull { o ->
        val id = o.text("id") ?: return@mapNotNull null
        AgentCall(
            id = id,
            number = o.text("number") ?: "",
            startedAt = o.optLong("startedAt"),
            endedAt = o.optLong("endedAt"),
            ended = o.optBoolean("ended"),
            summary = o.text("summary"),
            verdict = AiVerdict.parse(o.text("verdict")),
            caller = o.text("caller"),
            transcript = o.text("transcript"),
            transferred = o.optBoolean("transferred"),
        )
    }
}
