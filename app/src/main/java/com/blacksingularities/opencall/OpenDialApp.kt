package com.blacksingularities.opencall

import android.app.Application
import com.blacksingularities.opencall.call.CallManager
import com.blacksingularities.opencall.call.CallNotifications
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.I18n

class OpenDialApp : Application() {
    override fun onCreate() {
        super.onCreate()
        I18n.apply(SettingsStore(this).load().language)
        CallManager.attach(this)
        CallNotifications.channels(this)
    }
}
