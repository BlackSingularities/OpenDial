package com.blacksingularities.opencall.ai

import android.content.Context

/** Historia asystenta w pliku wyłączonym z kopii zapasowej – są w niej treści cudzych rozmów. */
class AiLog(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun load(): List<AiRecord> = synchronized(LOCK) { AiRecord.decodeList(prefs.getString(KEY, null)) }

    fun update(change: (List<AiRecord>) -> List<AiRecord>) {
        synchronized(LOCK) {
            prefs.edit().putString(KEY, AiRecord.encodeList(change(load()))).commit()
        }
    }

    var lastSync: Long
        get() = prefs.getLong(LAST_SYNC, 0L)
        set(value) {
            prefs.edit().putLong(LAST_SYNC, value).apply()
        }

    private companion object {
        const val FILE = "opendial_ai_log"
        const val KEY = "records"
        const val LAST_SYNC = "lastSync"
        val LOCK = Any()
    }
}
