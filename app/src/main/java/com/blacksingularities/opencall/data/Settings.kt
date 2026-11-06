package com.blacksingularities.opencall.data

import android.content.Context
import com.blacksingularities.opencall.ai.AiScreening
import com.blacksingularities.opencall.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

/** Kolejność sortowania i zapis nazw kontaktów. */
enum class NameOrder { FIRST_LAST, LAST_FIRST }

/** Motyw: według systemu, zawsze ciemny albo zawsze jasny. */
enum class ThemeMode { SYSTEM, DARK, LIGHT }

/** Ekran po uruchomieniu. */
enum class StartTab { FAVORITES, RECENTS, CONTACTS, KEYPAD, LAST }

/** Co robi dotknięcie wpisu na liście „Ostatnie”. */
enum class RecentTap { EXPAND, CALL, DETAILS }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val haptics: Boolean = true,
    val autoUpdate: Boolean = true,
    val startTab: StartTab = StartTab.LAST,
    val lastTab: Int = 1,
    // Klawiatura
    val dtmfTones: Boolean = true,
    val keypadVibrate: Boolean = true,
    val t9Search: Boolean = true,
    val confirmCall: Boolean = false,
    // Lista „Ostatnie”
    val groupCalls: Boolean = true,
    val recentTap: RecentTap = RecentTap.EXPAND,
    val showSim: Boolean = true,
    // Kontakty
    val nameOrder: NameOrder = NameOrder.FIRST_LAST,
    val onlyWithNumbers: Boolean = false,
    val showFrequent: Boolean = true,
    /** Układ listy kontaktów: 1 = lista, 2–4 = kafelki (zmieniane szczypaniem). */
    /** Poziom przybliżenia kontaktów: 0–4 lista od najgęstszej, 5–7 kafelki coraz większe. */
    /** Powiększenie listy kontaktów gestem: < [CONTACT_TILES_AT] lista, wyżej kafelki. */
    val contactScale: Float = 1f,
    /** Powiększenie historii połączeń gestem (te same progi co w kontaktach). */
    val recentScale: Float = 1f,
    /** Przypomnienia o urodzinach i rocznicach: powiadomienie rano i pasek w Ulubionych. */
    val eventReminders: Boolean = false,
    /** Godzina porannego powiadomienia (0–23). */
    val reminderHour: Int = 9,
    /** Zakładka „Ulubione” na dolnym pasku; wyłączona – aplikacja startuje od „Ostatnich”. */
    val showFavoritesTab: Boolean = true,
    // W trakcie rozmowy
    val proximity: Boolean = true,
    val vibrateOnConnect: Boolean = true,
    val vibrateOnEnd: Boolean = false,
    val showCallerLocation: Boolean = true,
    val swipeToAnswer: Boolean = true,
    // Blokowanie i filtrowanie
    val blockHidden: Boolean = false,
    val blockUnknown: Boolean = false,
    val silenceUnknown: Boolean = false,
    val blockRules: List<String> = emptyList(),
    val logBlocked: Boolean = true,
    val notifyBlocked: Boolean = false,
    // Asystent AI odbierający nieznane numery
    val ai: AiScreening = AiScreening(),
    // Szybkie wybieranie: cyfra 2–9 → numer
    val speedDial: Map<Int, String> = emptyMap(),
    val quickReplies: List<String> = DEFAULT_REPLIES,
    val defaultSim: String? = null,
    /** Karta SIM wybrana dla kontaktu: klucz kontaktu (LOOKUP_KEY) → id konta telefonicznego. */
    val contactSim: Map<String, String> = emptyMap(),
    val onboardingDone: Boolean = false,
) {
    companion object {
        val DEFAULT_REPLIES = listOf(
            "Nie mogę teraz rozmawiać. Oddzwonię później.",
            "Jestem w drodze, zadzwonię za chwilę.",
            "Jestem na spotkaniu – napisz proszę SMS.",
            "Zadzwoń proszę później.",
        )
    }
}

const val CONTACT_SCALE_MIN = 0.6f
const val CONTACT_SCALE_MAX = 3f

/** Od tego powiększenia lista kontaktów zamienia się w kafelki. */
const val CONTACT_TILES_AT = 1.6f

/** Liczba kafelków w rzędzie dla powiększenia [scale] (1 = lista). */
fun contactColumns(scale: Float): Int = when {
    scale < CONTACT_TILES_AT -> 1
    scale < 2.0f -> 4
    scale < 2.45f -> 3
    else -> 2
}

/** Ustawienia w SharedPreferences jako jeden obiekt JSON – odporny na dodawanie nowych pól. */
class SettingsStore(context: Context) {
    private val prefs = context.getSharedPreferences("opencall_settings", Context.MODE_PRIVATE)

    fun load(): AppSettings {
        val raw = prefs.getString("settings", null) ?: return AppSettings()
        return runCatching { decode(JSONObject(raw)) }.getOrElse { AppSettings() }
    }

    fun save(s: AppSettings) {
        prefs.edit().putString("settings", encode(s).toString()).apply()
    }

    companion object {
        fun encode(s: AppSettings): JSONObject = JSONObject().apply {
            put("language", s.language.name)
            put("theme", s.theme.name)
            put("haptics", s.haptics)
            put("autoUpdate", s.autoUpdate)
            put("startTab", s.startTab.name)
            put("lastTab", s.lastTab)
            put("dtmfTones", s.dtmfTones)
            put("keypadVibrate", s.keypadVibrate)
            put("t9Search", s.t9Search)
            put("confirmCall", s.confirmCall)
            put("groupCalls", s.groupCalls)
            put("recentTap", s.recentTap.name)
            put("showSim", s.showSim)
            put("nameOrder", s.nameOrder.name)
            put("onlyWithNumbers", s.onlyWithNumbers)
            put("showFrequent", s.showFrequent)
            put("contactScale", s.contactScale.toDouble())
            put("recentScale", s.recentScale.toDouble())
            put("eventReminders", s.eventReminders)
            put("reminderHour", s.reminderHour)
            put("showFavoritesTab", s.showFavoritesTab)
            put("proximity", s.proximity)
            put("vibrateOnConnect", s.vibrateOnConnect)
            put("vibrateOnEnd", s.vibrateOnEnd)
            put("showCallerLocation", s.showCallerLocation)
            put("swipeToAnswer", s.swipeToAnswer)
            put("blockHidden", s.blockHidden)
            put("blockUnknown", s.blockUnknown)
            put("silenceUnknown", s.silenceUnknown)
            put("blockRules", JSONArray(s.blockRules))
            put("logBlocked", s.logBlocked)
            put("notifyBlocked", s.notifyBlocked)
            put("ai", AiScreening.encode(s.ai))
            put("speedDial", JSONObject().apply { s.speedDial.forEach { (k, v) -> put(k.toString(), v) } })
            put("quickReplies", JSONArray(s.quickReplies))
            put("defaultSim", s.defaultSim ?: "")
            put("contactSim", JSONObject().apply { s.contactSim.forEach { (k, v) -> put(k, v) } })
            put("onboardingDone", s.onboardingDone)
        }

        fun decode(o: JSONObject): AppSettings {
            val d = AppSettings()
            fun b(k: String, def: Boolean) = if (o.has(k)) o.optBoolean(k, def) else def
            fun list(k: String, def: List<String>): List<String> {
                val a = o.optJSONArray(k) ?: return def
                return (0 until a.length()).map { a.optString(it) }.filter { it.isNotBlank() }
            }
            fun <E : Enum<E>> e(k: String, def: E): E =
                def.declaringJavaClass.enumConstants?.firstOrNull { it.name == o.optString(k, def.name) } ?: def
            val speed = HashMap<Int, String>()
            o.optJSONObject("speedDial")?.let { sd ->
                sd.keys().forEach { k -> k.toIntOrNull()?.let { n -> sd.optString(k).takeIf { it.isNotBlank() }?.let { speed[n] = it } } }
            }
            val contactSim = HashMap<String, String>()
            o.optJSONObject("contactSim")?.let { cs ->
                cs.keys().forEach { k -> cs.optString(k).takeIf { it.isNotBlank() }?.let { contactSim[k] = it } }
            }
            return AppSettings(
                language = e("language", d.language),
                theme = e("theme", d.theme),
                haptics = b("haptics", d.haptics),
                autoUpdate = b("autoUpdate", d.autoUpdate),
                startTab = e("startTab", d.startTab),
                lastTab = o.optInt("lastTab", d.lastTab),
                dtmfTones = b("dtmfTones", d.dtmfTones),
                keypadVibrate = b("keypadVibrate", d.keypadVibrate),
                t9Search = b("t9Search", d.t9Search),
                confirmCall = b("confirmCall", d.confirmCall),
                groupCalls = b("groupCalls", d.groupCalls),
                recentTap = e("recentTap", d.recentTap),
                showSim = b("showSim", d.showSim),
                nameOrder = e("nameOrder", d.nameOrder),
                onlyWithNumbers = b("onlyWithNumbers", d.onlyWithNumbers),
                showFrequent = b("showFrequent", d.showFrequent),
                contactScale = if (o.has("contactScale")) {
                    o.optDouble("contactScale", 1.0).toFloat().coerceIn(CONTACT_SCALE_MIN, CONTACT_SCALE_MAX)
                } else {
                    // Starsze wersje: poziomy 0–7 (5–7 = kafelki), a jeszcze starsze – liczba kolumn.
                    val level = if (o.has("contactZoom")) o.optInt("contactZoom", 2) else when (o.optInt("contactColumns", 1)) { 4 -> 5; 3 -> 6; 2 -> 7; else -> 2 }
                    listOf(0.65f, 0.8f, 1f, 1.2f, 1.45f, 1.75f, 2.2f, 2.7f)[level.coerceIn(0, 7)]
                },
                eventReminders = b("eventReminders", d.eventReminders),
                reminderHour = o.optInt("reminderHour", d.reminderHour).coerceIn(0, 23),
                recentScale = o.optDouble("recentScale", 1.0).toFloat().coerceIn(CONTACT_SCALE_MIN, CONTACT_SCALE_MAX),
                showFavoritesTab = b("showFavoritesTab", d.showFavoritesTab),
                proximity = b("proximity", d.proximity),
                vibrateOnConnect = b("vibrateOnConnect", d.vibrateOnConnect),
                vibrateOnEnd = b("vibrateOnEnd", d.vibrateOnEnd),
                showCallerLocation = b("showCallerLocation", d.showCallerLocation),
                swipeToAnswer = b("swipeToAnswer", d.swipeToAnswer),
                blockHidden = b("blockHidden", d.blockHidden),
                blockUnknown = b("blockUnknown", d.blockUnknown),
                silenceUnknown = b("silenceUnknown", d.silenceUnknown),
                blockRules = list("blockRules", d.blockRules),
                logBlocked = b("logBlocked", d.logBlocked),
                notifyBlocked = b("notifyBlocked", d.notifyBlocked),
                ai = AiScreening.decode(o.optJSONObject("ai")),
                speedDial = speed,
                quickReplies = list("quickReplies", d.quickReplies).ifEmpty { d.quickReplies },
                defaultSim = o.optString("defaultSim", "").ifBlank { null },
                contactSim = contactSim,
                onboardingDone = b("onboardingDone", d.onboardingDone),
            )
        }
    }
}
