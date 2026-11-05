package com.blacksingularities.opencall

import android.app.Application
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.call.CallNotifications
import com.blacksingularities.opencall.data.PhoneText
import com.blacksingularities.opencall.data.SettingsStore
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import java.util.Locale
import com.blacksingularities.opencall.i18n.I18n

class OpenDialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.apply(SettingsStore(this).load().language)
        CallManager.attach(this)
        CallNotifications.channels(this)
        // Podział numerów na bloki według kraju karty SIM / sieci (baza numeracji Androida).
        val app = this
        PhoneText.countryFormatter = { n -> PhoneNumberUtils.formatNumber(n, countryIso(app)) }
    }

    private fun countryIso(context: android.content.Context): String {
        val tm = context.getSystemService(TelephonyManager::class.java)
        val iso = runCatching { tm?.simCountryIso?.takeIf { it.isNotBlank() } ?: tm?.networkCountryIso?.takeIf { it.isNotBlank() } }.getOrNull()
            ?: Locale.getDefault().country.takeIf { it.isNotBlank() }
            ?: "PL"
        return iso.uppercase(Locale.ROOT)
    }
}
