package com.blacksingularities.opencall.ai

import com.blacksingularities.opencall.data.PhoneText
import org.json.JSONObject

/** Platforma, na której działa agent głosowy – to ona odbiera połączenie, nie telefon. */
enum class AiProvider { VAPI, RETELL, ELEVENLABS, CUSTOM }

/** Jak połączenie trafia do agenta: odrzucenie („zajęte”) albo cisza i brak odpowiedzi. */
enum class AiHandoff { BUSY, NO_ANSWER }

enum class AiTone { FORMAL, FRIENDLY, BRIEF }

/** Języki rozmowy: kod bazowy, nazwa własna i locale dla platform, które go wymagają. */
data class AiLanguage(val code: String, val name: String, val locale: String)

val AiLanguages = listOf(
    AiLanguage("pl", "Polski", "pl-PL"),
    AiLanguage("en", "English", "en-US"),
    AiLanguage("de", "Deutsch", "de-DE"),
    AiLanguage("uk", "Українська", "uk-UA"),
    AiLanguage("es", "Español", "es-ES"),
    AiLanguage("fr", "Français", "fr-FR"),
)

/**
 * Ustawienia trybu „Kto dzwoni naprawdę”. Klucz API nie jest tutaj – trzyma go [SecretStore],
 * bo ustawienia trafiają do zwykłego pliku objętego kopią zapasową.
 */
data class AiScreening(
    val enabled: Boolean = false,
    // Połączenie z platformą
    val provider: AiProvider = AiProvider.VAPI,
    val agentId: String = "",
    /** Identyfikator numeru na platformie (Vapi przypisuje agenta do numeru po tym ID). */
    val lineId: String = "",
    /** Numer agenta: tu operator przekierowuje połączenia. */
    val agentNumber: String = "",
    /** Twój numer: tu agent przełącza ważne rozmowy. */
    val ownNumber: String = "",
    val customUrl: String = "",
    /** Odcisk konfiguracji ostatnio wysłanej do agenta – pokazuje niewysłane zmiany. */
    val syncedHash: String = "",
    // Przekierowanie u operatora
    val handoff: AiHandoff = AiHandoff.BUSY,
    val noAnswerSeconds: Int = 15,
    val forwardingOn: Boolean = false,
    // Kto trafia do asystenta
    val screenHidden: Boolean = true,
    /** Ponowny telefon z tego samego numeru w tym czasie (minuty) dzwoni normalnie; 0 = wyłączone. */
    val repeatMinutes: Int = 3,
    /** Numery, do których dzwoniłeś w tym czasie (dni), dzwonią normalnie; 0 = wyłączone. */
    val recentOutgoingDays: Int = 30,
    val scheduleOn: Boolean = false,
    val fromHour: Int = 8,
    val toHour: Int = 20,
    val skipRoaming: Boolean = true,
    // Sposób rozmowy
    val assistantName: String = "",
    val ownerName: String = "",
    val language: String = "pl",
    val tone: AiTone = AiTone.FORMAL,
    val greeting: String = "",
    val importantRules: String = DEFAULT_IMPORTANT,
    val spamRules: String = DEFAULT_SPAM,
    val extraRules: String = "",
    val maxSeconds: Int = 90,
    /** „dostawca/model” (Vapi) albo sama nazwa; puste = model ustawiony w panelu platformy. */
    val model: String = "",
    val voice: String = "",
    val record: Boolean = false,
    // Po rozmowie
    val notifySpam: Boolean = false,
    val autoBlockSpam: Boolean = false,
    val keepTranscripts: Boolean = true,
) {
    /** Czy telefon może już kierować połączenia do agenta. */
    val ready: Boolean get() = enabled && forwardingOn && agentNumber.isNotBlank()

    companion object {
        const val DEFAULT_IMPORTANT =
            "Urząd, sąd, policja, szpital, lekarz, szkoła, przedszkole, bank w sprawie mojego konta, " +
                "kurier z przesyłką, ktoś, kogo znam prywatnie lub zawodowo, sprawy nagłe i zagrażające zdrowiu."
        const val DEFAULT_SPAM =
            "Fotowoltaika, pompy ciepła, zmiana operatora prądu lub telefonu, pożyczki, inwestycje, " +
                "ubezpieczenia, „badanie opinii”, darmowe prezenty, oferty i sprzedaż wszelkiego rodzaju."

        fun encode(a: AiScreening): JSONObject = JSONObject().apply {
            put("enabled", a.enabled)
            put("provider", a.provider.name)
            put("agentId", a.agentId)
            put("lineId", a.lineId)
            put("agentNumber", a.agentNumber)
            put("ownNumber", a.ownNumber)
            put("customUrl", a.customUrl)
            put("syncedHash", a.syncedHash)
            put("handoff", a.handoff.name)
            put("noAnswerSeconds", a.noAnswerSeconds)
            put("forwardingOn", a.forwardingOn)
            put("screenHidden", a.screenHidden)
            put("repeatMinutes", a.repeatMinutes)
            put("recentOutgoingDays", a.recentOutgoingDays)
            put("scheduleOn", a.scheduleOn)
            put("fromHour", a.fromHour)
            put("toHour", a.toHour)
            put("skipRoaming", a.skipRoaming)
            put("assistantName", a.assistantName)
            put("ownerName", a.ownerName)
            put("language", a.language)
            put("tone", a.tone.name)
            put("greeting", a.greeting)
            put("importantRules", a.importantRules)
            put("spamRules", a.spamRules)
            put("extraRules", a.extraRules)
            put("maxSeconds", a.maxSeconds)
            put("model", a.model)
            put("voice", a.voice)
            put("record", a.record)
            put("notifySpam", a.notifySpam)
            put("autoBlockSpam", a.autoBlockSpam)
            put("keepTranscripts", a.keepTranscripts)
        }

        fun decode(o: JSONObject?): AiScreening {
            val d = AiScreening()
            if (o == null) return d
            fun b(k: String, def: Boolean) = if (o.has(k)) o.optBoolean(k, def) else def
            fun s(k: String, def: String) = if (o.has(k)) o.optString(k, def) else def
            return AiScreening(
                enabled = b("enabled", d.enabled),
                provider = AiProvider.entries.firstOrNull { it.name == o.optString("provider") } ?: d.provider,
                agentId = s("agentId", d.agentId),
                lineId = s("lineId", d.lineId),
                agentNumber = s("agentNumber", d.agentNumber),
                ownNumber = s("ownNumber", d.ownNumber),
                customUrl = s("customUrl", d.customUrl),
                syncedHash = s("syncedHash", d.syncedHash),
                handoff = AiHandoff.entries.firstOrNull { it.name == o.optString("handoff") } ?: d.handoff,
                noAnswerSeconds = o.optInt("noAnswerSeconds", d.noAnswerSeconds).coerceIn(5, 30),
                forwardingOn = b("forwardingOn", d.forwardingOn),
                screenHidden = b("screenHidden", d.screenHidden),
                repeatMinutes = o.optInt("repeatMinutes", d.repeatMinutes).coerceIn(0, 60),
                recentOutgoingDays = o.optInt("recentOutgoingDays", d.recentOutgoingDays).coerceIn(0, 365),
                scheduleOn = b("scheduleOn", d.scheduleOn),
                fromHour = o.optInt("fromHour", d.fromHour).coerceIn(0, 23),
                toHour = o.optInt("toHour", d.toHour).coerceIn(0, 23),
                skipRoaming = b("skipRoaming", d.skipRoaming),
                assistantName = s("assistantName", d.assistantName),
                ownerName = s("ownerName", d.ownerName),
                language = s("language", d.language),
                tone = AiTone.entries.firstOrNull { it.name == o.optString("tone") } ?: d.tone,
                greeting = s("greeting", d.greeting),
                importantRules = s("importantRules", d.importantRules),
                spamRules = s("spamRules", d.spamRules),
                extraRules = s("extraRules", d.extraRules),
                maxSeconds = o.optInt("maxSeconds", d.maxSeconds).coerceIn(30, 600),
                model = s("model", d.model),
                voice = s("voice", d.voice),
                record = b("record", d.record),
                notifySpam = b("notifySpam", d.notifySpam),
                autoBlockSpam = b("autoBlockSpam", d.autoBlockSpam),
                keepTranscripts = b("keepTranscripts", d.keepTranscripts),
            )
        }
    }
}

/** Czyste reguły trybu – bez Androida, więc testowane jednostkowo. */
object AiRules {
    /** Okno godzin; `od > do` przechodzi przez północ, `od == do` oznacza całą dobę. */
    fun inSchedule(a: AiScreening, hour: Int): Boolean {
        if (!a.scheduleOn || a.fromHour == a.toHour) return true
        return if (a.fromHour < a.toHour) hour in a.fromHour until a.toHour else hour >= a.fromHour || hour < a.toHour
    }

    /**
     * Czy to połączenie ma trafić do asystenta. Przy każdej wątpliwości telefon po prostu dzwoni:
     * nieodebrany spam jest mniejszym złem niż zgubiony ważny telefon.
     */
    fun shouldScreen(
        a: AiScreening,
        hidden: Boolean,
        unknown: Boolean,
        repeated: Boolean,
        calledRecently: Boolean,
        hour: Int,
        roaming: Boolean,
    ): Boolean {
        if (!a.ready) return false
        if (!inSchedule(a, hour)) return false
        if (a.skipRoaming && roaming) return false
        if (repeated || calledRecently) return false
        return if (hidden) a.screenHidden else unknown
    }

    /** Kody GSM przekierowania warunkowego: 67 przy „zajęte”, 61 przy braku odpowiedzi. */
    fun enableCode(a: AiScreening): String {
        val n = e164(a.agentNumber)
        return when (a.handoff) {
            AiHandoff.BUSY -> "**67*$n#"
            AiHandoff.NO_ANSWER -> "**61*$n**${a.noAnswerSeconds.coerceIn(5, 30) / 5 * 5}#"
        }
    }

    fun disableCode(a: AiScreening): String = when (a.handoff) {
        AiHandoff.BUSY -> "##67#"
        AiHandoff.NO_ANSWER -> "##61#"
    }

    /** Zapis międzynarodowy; numer krajowy dostaje kod kraju [country]. */
    fun e164(number: String, country: String = "48"): String {
        val d = PhoneText.digits(number)
        return when {
            d.isEmpty() -> ""
            d.startsWith("+") -> d
            d.startsWith("00") -> "+" + d.drop(2)
            d.length == 9 -> "+$country$d"
            d.startsWith(country) && d.length > 9 -> "+$d"
            else -> "+" + d
        }
    }
}
