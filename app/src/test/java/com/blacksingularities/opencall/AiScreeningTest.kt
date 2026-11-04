package com.blacksingularities.opencall

import com.blacksingularities.opencall.ai.AgentCall
import com.blacksingularities.opencall.ai.AiHandoff
import com.blacksingularities.opencall.ai.AiMatch
import com.blacksingularities.opencall.ai.AiPayloads
import com.blacksingularities.opencall.ai.AiPrompt
import com.blacksingularities.opencall.ai.AiProvider
import com.blacksingularities.opencall.ai.AiRecord
import com.blacksingularities.opencall.ai.AiRules
import com.blacksingularities.opencall.ai.AiScreening
import com.blacksingularities.opencall.ai.AiStatus
import com.blacksingularities.opencall.ai.AiTone
import com.blacksingularities.opencall.ai.AiVerdict
import com.blacksingularities.opencall.data.AppSettings
import com.blacksingularities.opencall.data.SettingsStore
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiScreeningTest {
    private val ready = AiScreening(enabled = true, forwardingOn = true, agentNumber = "+48221112233", ownNumber = "600100200")

    private fun screen(
        a: AiScreening = ready,
        hidden: Boolean = false,
        unknown: Boolean = true,
        repeated: Boolean = false,
        calledRecently: Boolean = false,
        hour: Int = 12,
        roaming: Boolean = false,
    ) = AiRules.shouldScreen(a, hidden, unknown, repeated, calledRecently, hour, roaming)

    @Test
    fun unknownNumberGoesToTheAgent() {
        assertTrue(screen())
    }

    @Test
    fun anythingUncertainRingsNormally() {
        assertFalse(screen(unknown = false))
        assertFalse(screen(repeated = true))
        assertFalse(screen(calledRecently = true))
        assertFalse(screen(roaming = true))
        assertTrue(screen(a = ready.copy(skipRoaming = false), roaming = true))
    }

    @Test
    fun notReadyNeverRejectsAnyCall() {
        assertFalse(screen(a = ready.copy(enabled = false)))
        assertFalse(screen(a = ready.copy(forwardingOn = false)))
        assertFalse(screen(a = ready.copy(agentNumber = "")))
    }

    @Test
    fun hiddenNumbersFollowTheirOwnSwitch() {
        assertTrue(screen(hidden = true, unknown = false))
        assertFalse(screen(a = ready.copy(screenHidden = false), hidden = true, unknown = false))
    }

    @Test
    fun scheduleWrapsPastMidnight() {
        val night = ready.copy(scheduleOn = true, fromHour = 22, toHour = 7)
        assertTrue(AiRules.inSchedule(night, 23))
        assertTrue(AiRules.inSchedule(night, 3))
        assertFalse(AiRules.inSchedule(night, 7))
        assertFalse(AiRules.inSchedule(night, 12))
        val day = ready.copy(scheduleOn = true, fromHour = 8, toHour = 20)
        assertTrue(AiRules.inSchedule(day, 8))
        assertFalse(AiRules.inSchedule(day, 20))
        assertTrue(AiRules.inSchedule(ready.copy(scheduleOn = true, fromHour = 9, toHour = 9), 3))
    }

    @Test
    fun forwardingCodes() {
        assertEquals("**67*+48221112233#", AiRules.enableCode(ready))
        assertEquals("##67#", AiRules.disableCode(ready))
        val silent = ready.copy(handoff = AiHandoff.NO_ANSWER, noAnswerSeconds = 20)
        assertEquals("**61*+48221112233**20#", AiRules.enableCode(silent))
        assertEquals("##61#", AiRules.disableCode(silent))
    }

    @Test
    fun e164() {
        assertEquals("+48600100200", AiRules.e164("600 100 200"))
        assertEquals("+48600100200", AiRules.e164("0048 600 100 200"))
        assertEquals("+48600100200", AiRules.e164("+48 600-100-200"))
        assertEquals("+48600100200", AiRules.e164("48600100200"))
        assertEquals("", AiRules.e164(""))
    }

    @Test
    fun configRoundTripsInsideSettings() {
        val s = AppSettings(
            ai = ready.copy(provider = AiProvider.RETELL, tone = AiTone.BRIEF, greeting = "Halo", repeatMinutes = 10, model = "gpt-5.4-mini"),
        )
        assertEquals(s, SettingsStore.decode(SettingsStore.encode(s)))
    }

    @Test
    fun settingsFromBeforeTheAssistantStayOff() {
        val o = SettingsStore.encode(AppSettings())
        o.remove("ai")
        assertFalse(SettingsStore.decode(o).ai.enabled)
    }

    @Test
    fun greetingAlwaysDisclosesAi() {
        assertTrue(AiPrompt.greeting(AiScreening()).contains("AI"))
        assertTrue(AiPrompt.greeting(AiScreening(greeting = "Dzień dobry, słucham.")).contains("asystentem AI"))
        assertEquals("Tu asystent AI Adama.", AiPrompt.greeting(AiScreening(greeting = "Tu asystent AI Adama.")))
        assertTrue(AiPrompt.greeting(AiScreening(record = true)).contains("nagrywana"))
    }

    @Test
    fun systemPromptCarriesOwnerRules() {
        val p = AiPrompt.system(AiScreening(ownerName = "Adam", spamRules = "fotowoltaika", extraRules = "Żona to Ewa"))
        assertTrue(p.contains("Adam"))
        assertTrue(p.contains("fotowoltaika"))
        assertTrue(p.contains("Żona to Ewa"))
        assertTrue(p.contains("Polish"))
    }

    @Test
    fun fingerprintNoticesChanges() {
        val a = AiScreening(ownNumber = "600100200")
        assertEquals(AiPrompt.fingerprint(a), AiPrompt.fingerprint(a.copy(notifySpam = true)))
        assertFalse(AiPrompt.fingerprint(a) == AiPrompt.fingerprint(a.copy(spamRules = "inne")))
    }

    @Test
    fun vapiTransferShowsAgentNumberSoItCannotLoop() {
        val body = AiPayloads.vapiAssistant(ready, null)
        val tools = body.getJSONObject("model").getJSONArray("tools")
        val transfer = (0 until tools.length()).map { tools.getJSONObject(it) }.first { it.getString("type") == "transferCall" }
        val destination = transfer.getJSONArray("destinations").getJSONObject(0)
        assertEquals("+48600100200", destination.getString("number"))
        assertEquals("{{phoneNumber.number}}", destination.getString("callerId"))
        assertEquals("warm-transfer-say-summary", destination.getJSONObject("transferPlan").getString("mode"))
    }

    @Test
    fun vapiKeepsDashboardModelWhenNoneChosen() {
        val existing = JSONObject("""{"provider":"groq","model":"compound-beta","tools":[{"type":"endCall"},{"type":"dtmf"}]}""")
        val model = AiPayloads.vapiAssistant(ready, existing).getJSONObject("model")
        assertEquals("groq", model.getString("provider"))
        assertEquals(3, model.getJSONArray("tools").length())
        val chosen = AiPayloads.vapiAssistant(ready.copy(model = "anthropic/claude-haiku-5-5"), existing).getJSONObject("model")
        assertEquals("anthropic", chosen.getString("provider"))
        assertEquals("claude-haiku-5-5", chosen.getString("model"))
    }

    @Test
    fun parsesVapiCalls() {
        val raw = """[{"id":"c1","type":"inboundPhoneCall","status":"ended","endedReason":"assistant-forwarded-call",
            "startedAt":"2026-11-04T10:00:00.000Z","endedAt":"2026-11-04T10:01:00.000Z","customer":{"number":"+48500600700"},
            "analysis":{"summary":"Kurier z paczką.","structuredData":{"verdict":"important","caller":"DPD"}},
            "artifact":{"transcript":"AI: Dzień dobry"}},
            {"id":"web","type":"webCall","status":"ended"}]"""
        val c = AiPayloads.vapiCalls(raw).single()
        assertEquals("c1", c.id)
        assertEquals("+48500600700", c.number)
        assertTrue(c.ended)
        assertTrue(c.transferred)
        assertEquals(AiVerdict.IMPORTANT, c.verdict)
        assertEquals("DPD", c.caller)
        assertEquals(60_000L, c.endedAt - c.startedAt)
    }

    @Test
    fun parsesRetellCalls() {
        val raw = """{"items":[{"call_id":"r1","call_type":"phone_call","call_status":"ongoing","from_number":"+48500600700",
            "start_timestamp":1000,"call_analysis":{"call_summary":"Oferta","custom_analysis_data":{"verdict":"spam"}}}]}"""
        val c = AiPayloads.retellCalls(raw).single()
        assertFalse(c.ended)
        assertEquals(AiVerdict.SPAM, c.verdict)
        assertEquals("Oferta", c.summary)
    }

    @Test
    fun parsesElevenLabsConversation() {
        val raw = """{"conversation_id":"e1","status":"done",
            "metadata":{"start_time_unix_secs":100,"call_duration_secs":30,"phone_call":{"external_number":"+48500600700"}},
            "analysis":{"transcript_summary":"Pytanie o fakturę","data_collection_results":{"verdict":{"value":"message"}}},
            "transcript":[{"role":"agent","message":"Słucham"},{"role":"user","message":"Faktura"}]}"""
        val c = AiPayloads.elevenCall(raw)!!
        assertEquals("+48500600700", c.number)
        assertEquals(130_000L, c.endedAt)
        assertEquals(AiVerdict.MESSAGE, c.verdict)
        assertEquals("AI: Słucham\nCaller: Faktura", c.transcript)
    }

    private fun call(
        id: String = "c1",
        number: String = "+48500600700",
        startedAt: Long = 10_000,
        ended: Boolean = true,
        endedAt: Long = 40_000,
        summary: String? = "Kurier",
        verdict: AiVerdict = AiVerdict.IMPORTANT,
    ) = AgentCall(id, number, startedAt, endedAt, ended, summary, verdict, null, "transkrypcja", false)

    private val pending = AiRecord(id = "local-1", at = 5_000, number = "500600700")

    @Test
    fun pendingCallIsMatchedByNumberAndFinished() {
        val r = AiMatch.merge(listOf(pending), listOf(call()), 45_000, "600100200", true)
        val rec = r.records.single()
        assertEquals("local-1", rec.id)
        assertEquals(AiStatus.DONE, rec.status)
        assertEquals("Kurier", rec.summary)
        assertEquals(listOf(rec), r.finished)
    }

    @Test
    fun finishedCallIsNotReportedTwice() {
        val first = AiMatch.merge(listOf(pending), listOf(call()), 45_000, "", true)
        val second = AiMatch.merge(first.records, listOf(call()), 50_000, "", true)
        assertTrue(second.finished.isEmpty())
        assertEquals(1, second.records.size)
    }

    @Test
    fun endedCallWaitsBrieflyForItsSummary() {
        val early = AiMatch.merge(listOf(pending), listOf(call(summary = null)), 45_000, "", true)
        assertEquals(AiStatus.LIVE, early.records.single().status)
        assertTrue(early.finished.isEmpty())
        val late = AiMatch.merge(early.records, listOf(call(summary = null)), 40_000 + AiMatch.SUMMARY_GRACE_MS, "", true)
        assertEquals(AiStatus.DONE, late.records.single().status)
    }

    @Test
    fun callThatNeverReachesTheAgentIsReportedAsMissed() {
        val r = AiMatch.merge(listOf(pending), emptyList(), pending.at + AiMatch.PENDING_TIMEOUT_MS + 1, "", true)
        assertEquals(AiStatus.MISSED, r.records.single().status)
        assertEquals(1, r.finished.size)
    }

    @Test
    fun carrierReplacingTheNumberStillMatches() {
        val r = AiMatch.merge(listOf(pending), listOf(call(number = "+48600100200")), 45_000, "600100200", true)
        assertEquals("local-1", r.records.single().id)
    }

    @Test
    fun otherCallerGetsItsOwnRecord() {
        val r = AiMatch.merge(listOf(pending), listOf(call(number = "+48111222333")), 45_000, "600100200", true)
        assertEquals(2, r.records.size)
    }

    @Test
    fun transcriptsCanBeDiscarded() {
        val r = AiMatch.merge(listOf(pending), listOf(call()), 45_000, "", false)
        assertNull(r.records.single().transcript)
    }

    @Test
    fun secondCallSoonAfterHandoffRingsNormally() {
        val log = listOf(pending)
        assertTrue(AiMatch.isRepeat(log, "+48 500 600 700", 3, pending.at + 60_000))
        assertFalse(AiMatch.isRepeat(log, "+48 500 600 700", 3, pending.at + 200_000))
        assertFalse(AiMatch.isRepeat(log, "+48 500 600 700", 0, pending.at + 1_000))
        assertFalse(AiMatch.isRepeat(log, "+48 111 222 333", 3, pending.at + 1_000))
    }

    @Test
    fun answeringYourselfCancelsTheWait() {
        assertTrue(AiMatch.withoutPending(listOf(pending), "+48500600700").isEmpty())
        val done = pending.copy(status = AiStatus.DONE)
        assertEquals(1, AiMatch.withoutPending(listOf(done), "+48500600700").size)
    }

    @Test
    fun recordsSurviveStorage() {
        val r = pending.copy(status = AiStatus.DONE, summary = "Kurier", verdict = AiVerdict.SPAM, transferred = true, hidden = true)
        assertEquals(listOf(r), AiRecord.decodeList(AiRecord.encodeList(listOf(r))))
        assertTrue(AiRecord.decodeList("nie json").isEmpty())
    }
}
