package com.blacksingularities.opencall.call

import android.Manifest
import android.annotation.SuppressLint
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telecom.PhoneAccountHandle
import android.telecom.TelecomManager
import android.telephony.TelephonyManager
import com.blacksingularities.opencall.data.SimAccount

/** Telecom: rola domyślnej aplikacji telefonu, karty SIM i wybieranie numerów. */
object Dialer {
    fun telecom(context: Context): TelecomManager = context.getSystemService(TelecomManager::class.java)

    fun isDefault(context: Context): Boolean =
        runCatching { telecom(context).defaultDialerPackage == context.packageName }.getOrDefault(false)

    /** Okno systemu „Ustawić OpenDial jako domyślną aplikację telefonu?” – null, gdy rola już jest. */
    fun roleRequest(context: Context): Intent? {
        val rm = context.getSystemService(RoleManager::class.java) ?: return null
        if (!rm.isRoleAvailable(RoleManager.ROLE_DIALER) || rm.isRoleHeld(RoleManager.ROLE_DIALER)) return null
        return rm.createRequestRoleIntent(RoleManager.ROLE_DIALER)
    }

    fun has(context: Context, permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    /** Konta telefoniczne zdolne do połączeń (zwykle karty SIM, czasem też SIP/Wi-Fi). */
    @SuppressLint("MissingPermission")
    fun sims(context: Context): List<SimAccount> {
        if (!has(context, Manifest.permission.READ_PHONE_STATE)) return emptyList()
        val tm = telecom(context)
        return runCatching {
            tm.callCapablePhoneAccounts.mapIndexedNotNull { i, h ->
                val acc = tm.getPhoneAccount(h) ?: return@mapIndexedNotNull null
                SimAccount(
                    id = h.id,
                    label = acc.label?.toString()?.takeIf { it.isNotBlank() } ?: "SIM ${i + 1}",
                    number = acc.address?.schemeSpecificPart?.takeIf { it.isNotBlank() },
                    slot = i,
                    color = acc.highlightColor,
                )
            }
        }.getOrDefault(emptyList())
    }

    @SuppressLint("MissingPermission")
    private fun handleFor(context: Context, simId: String?): PhoneAccountHandle? {
        if (simId == null || !has(context, Manifest.permission.READ_PHONE_STATE)) return null
        return runCatching { telecom(context).callCapablePhoneAccounts.firstOrNull { it.id == simId } }.getOrNull()
    }

    // ---- Numery alarmowe -----------------------------------------------------------------------

    /** Numery alarmowe na wypadek braku karty SIM / usługi telefonii (system zna listę z sieci i karty). */
    private val fallbackEmergency = setOf("112", "911", "999", "997", "998", "000", "110", "119", "118", "08")

    /**
     * Czy numer jest alarmowy. Pierwszeństwo ma lista systemu (zależna od kraju, sieci i karty);
     * gdy telefonia nie odpowiada, zostaje lista podstawowa. Numery alarmowe omijają okno
     * potwierdzenia, wybór karty SIM i wszystkie filtry.
     */
    fun isEmergency(context: Context, number: String): Boolean {
        val digits = number.filter { it.isDigit() || it == '+' }
        if (digits.isEmpty() || digits.length > 6 || digits.startsWith("+")) return false
        val system = runCatching { context.getSystemService(TelephonyManager::class.java)?.isEmergencyNumber(digits) }.getOrNull()
        return system == true || (system == null && digits in fallbackEmergency)
    }

    private const val EMERGENCY_PREFS = "emergency"
    private const val EMERGENCY_AT = "lastAt"

    /** Okno po połączeniu alarmowym, w którym oddzwaniające służby nie trafiają na żaden filtr. */
    const val EMERGENCY_CALLBACK_MS = 30 * 60_000L

    fun markEmergency(context: Context) {
        context.getSharedPreferences(EMERGENCY_PREFS, Context.MODE_PRIVATE).edit().putLong(EMERGENCY_AT, System.currentTimeMillis()).apply()
    }

    /** Czy niedawno było połączenie alarmowe – wtedy każde przychodzące połączenie dzwoni normalnie. */
    fun recentEmergency(context: Context, now: Long = System.currentTimeMillis()): Boolean {
        val at = context.getSharedPreferences(EMERGENCY_PREFS, Context.MODE_PRIVATE).getLong(EMERGENCY_AT, 0L)
        return at > 0 && now - at in 0..EMERGENCY_CALLBACK_MS
    }

    /** Dzwoni przez Telecom (z wybraną kartą SIM, jeśli podana). Zwraca false, gdy brak uprawnienia. */
    @SuppressLint("MissingPermission")
    fun call(context: Context, number: String, simId: String? = null, video: Boolean = false): Boolean {
        if (!has(context, Manifest.permission.CALL_PHONE)) return false
        val uri = Uri.fromParts("tel", number.trim(), null)
        val extras = Bundle()
        val emergency = isEmergency(context, number)
        // Połączenie alarmowe: kartę (albo jej brak) wybiera system – działa też bez SIM i w obcej sieci.
        if (emergency) markEmergency(context) else handleFor(context, simId)?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        if (video && !emergency) extras.putInt(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, android.telecom.VideoProfile.STATE_BIDIRECTIONAL)
        return runCatching {
            telecom(context).placeCall(uri, extras)
            true
        }.getOrDefault(false)
    }

    /** Połączenie z dowolnym adresem (np. „sip:”) – konto wybiera system. */
    @SuppressLint("MissingPermission")
    fun callUri(context: Context, uri: Uri): Boolean {
        if (!has(context, Manifest.permission.CALL_PHONE)) return false
        return runCatching {
            telecom(context).placeCall(uri, Bundle())
            true
        }.getOrDefault(false)
    }

    /** Czy któreś konto telefoniczne obsługuje rozmowy wideo (VT/ViLTE). */
    @SuppressLint("MissingPermission")
    fun supportsVideo(context: Context): Boolean {
        if (!has(context, Manifest.permission.READ_PHONE_STATE)) return false
        val tm = telecom(context)
        return runCatching {
            tm.callCapablePhoneAccounts.any { h ->
                tm.getPhoneAccount(h)?.hasCapabilities(android.telecom.PhoneAccount.CAPABILITY_VIDEO_CALLING) == true
            }
        }.getOrDefault(false)
    }

    /** Poczta głosowa operatora (jak przytrzymanie „1” w każdym dialerze). */
    @SuppressLint("MissingPermission")
    fun voicemail(context: Context, simId: String? = null): Boolean {
        if (!has(context, Manifest.permission.CALL_PHONE)) return false
        val extras = Bundle()
        handleFor(context, simId)?.let { extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, it) }
        return runCatching {
            telecom(context).placeCall(Uri.fromParts("voicemail", "", null), extras)
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun voicemailNumber(context: Context): String? = runCatching {
        if (!has(context, Manifest.permission.READ_PHONE_STATE)) return null
        context.getSystemService(TelephonyManager::class.java)?.voiceMailNumber
    }.getOrNull()

    /** Kod MMI/USSD („*100#”) albo kod serwisowy („*#06#”) – wysyłany jak połączenie. */
    fun isMmi(number: String): Boolean {
        val n = number.trim()
        return (n.startsWith("*") || n.startsWith("#")) && n.endsWith("#")
    }

    /** IMEI (lub MEID) wszystkich gniazd SIM; pusta lista, gdy system nie udostępnia identyfikatorów. */
    @SuppressLint("MissingPermission", "HardwareIds")
    @Suppress("DEPRECATION")
    fun deviceIds(context: Context): List<String> {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: return emptyList()
        val count = runCatching { if (Build.VERSION.SDK_INT >= 30) tm.activeModemCount else tm.phoneCount }.getOrDefault(1).coerceAtLeast(1)
        return (0 until count).mapNotNull { slot ->
            runCatching { tm.getImei(slot) ?: tm.getMeid(slot) }.getOrNull()?.takeIf { it.isNotBlank() }
        }
    }

    /** Kody tajne „*#*#1234#*#*” – obsługiwane przez system, nie przez połączenie. */
    @SuppressLint("MissingPermission")
    fun handleSecretCode(context: Context, number: String): Boolean {
        val m = Regex("^\\*#\\*#(\\d+)#\\*#\\*$").matchEntire(number.trim()) ?: return false
        return runCatching {
            context.getSystemService(TelephonyManager::class.java).sendDialerSpecialCode(m.groupValues[1])
            true
        }.getOrDefault(false)
    }

    @SuppressLint("MissingPermission")
    fun silenceRinger(context: Context) {
        runCatching { telecom(context).silenceRinger() }
    }

    @SuppressLint("MissingPermission")
    fun cancelMissedCallsNotification(context: Context) {
        runCatching { telecom(context).cancelMissedCallsNotification() }
    }

    /** Czy któreś połączenie trwa (np. aby wrócić do ekranu rozmowy z aplikacji). */
    @SuppressLint("MissingPermission")
    fun inCall(context: Context): Boolean = runCatching { telecom(context).isInCall }.getOrDefault(false)
}
