package com.blacksingularities.opencall.data

import android.content.Context
import com.blacksingularities.opencall.i18n.AppLanguage
import org.json.JSONArray
import org.json.JSONObject

/** Kolejność sortowania i zapis nazw kontaktów. */
enum class NameOrder { FIRST_LAST, LAST_FIRST }

/** Ekran po uruchomieniu. */
enum class StartTab { FAVORITES, RECENTS, CONTACTS, KEYPAD, LAST }

/** Co robi dotknięcie wpisu na liście „Ostatnie”. */
enum class RecentTap { EXPAND, CALL, DETAILS }

data class AppSettings(
    val language: AppLanguage = AppLanguage.SYSTEM,
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
    val contactColumns: Int = 1,
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
    // Szybkie wybieranie: cyfra 2–9 → numer
    val speedDial: Map<Int, String> = emptyMap(),
    val quickReplies: List<String> = DEFAULT_REPLIES,
    val defaultSim: String? = null,
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
            put("contactColumns", s.contactColumns)
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
            put("speedDial", JSONObject().apply { s.speedDial.forEach { (k, v) -> put(k.toString(), v) } })
            put("quickReplies", JSONArray(s.quickReplies))
            put("defaultSim", s.defaultSim ?: "")
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
            return AppSettings(
                language = e("language", d.language),
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
                contactColumns = o.optInt("contactColumns", d.contactColumns).coerceIn(1, 4),
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
                speedDial = speed,
                quickReplies = list("quickReplies", d.quickReplies).ifEmpty { d.quickReplies },
                defaultSim = o.optString("defaultSim", "").ifBlank { null },
                onboardingDone = b("onboardingDone", d.onboardingDone),
            )
        }
    }
}
