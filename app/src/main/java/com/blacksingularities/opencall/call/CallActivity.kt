package com.blacksingularities.opencall.call

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.CallAudioState
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.view.WindowCompat
import com.blacksingularities.opencall.data.SettingsStore
import com.blacksingularities.opencall.i18n.I18n
import com.blacksingularities.opencall.ui.InCallScreen
import com.blacksingularities.opencall.ui.theme.GlassScope
import com.blacksingularities.opencall.ui.theme.OpenDialTheme
import kotlinx.coroutines.delay

/**
 * Ekran rozmowy: osobne okno nad ekranem blokady (dzwoni / trwa rozmowa), z czujnikiem
 * zbliżeniowym wygaszającym ekran przy uchu.
 */
class CallActivity : ComponentActivity() {
    private var proximity: PowerManager.WakeLock? = null
    private var showDialpad by mutableStateOf(false)

    override fun onResume() {
        super.onResume()
        com.blacksingularities.opencall.ui.theme.ThemeState.dark = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        enableEdgeToEdge()
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = false
        val settings = SettingsStore(this).load()
        I18n.apply(settings.language)
        handle(intent)
        // Ekran rozmowy jest zawsze ciemny, także przy jasnym motywie aplikacji.
        com.blacksingularities.opencall.ui.theme.ThemeState.dark = true
        setContent {
            OpenDialTheme {
                GlassScope(haptics = settings.haptics) {
                    val tick by CallManager.tick.collectAsState()
                    val audio by CallManager.audio.collectAsState()
                    val calls = remember(tick) { CallManager.snapshot() }
                    var closing by remember { mutableStateOf(false) }
                    LaunchedEffect(calls.isEmpty()) {
                        if (calls.isEmpty()) {
                            // Chwila na „Połączenie zakończone” z czasem rozmowy, potem zamknięcie.
                            closing = true
                            delay(1400)
                            if (CallManager.snapshot().isEmpty()) finishAndRemoveTask()
                        } else {
                            closing = false
                        }
                    }
                    val primary = CallManager.primary(calls)
                    LaunchedEffect(primary?.state, audio?.route) {
                        updateProximity(settings.proximity && primary != null && !primary.ringing && audio?.route == CallAudioState.ROUTE_EARPIECE)
                    }
                    InCallScreen(
                        calls = calls,
                        audio = audio,
                        ended = if (closing) CallManager.lastEnded else null,
                        settings = settings,
                        dialpadRequested = showDialpad,
                        onDialpadShown = { showDialpad = false },
                        onAddCall = {
                            startActivity(
                                Intent(this@CallActivity, com.blacksingularities.opencall.MainActivity::class.java)
                                    .setAction(com.blacksingularities.opencall.MainActivity.ACTION_ADD_CALL)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        onMinimize = { moveTaskToBack(true) },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANSWER, false) == true) {
            CallManager.snapshot().firstOrNull { it.ringing }?.let { CallManager.answer(it.call) }
        }
        if (intent?.getBooleanExtra(EXTRA_DIALPAD, false) == true) showDialpad = true
    }

    private fun updateProximity(on: Boolean) {
        if (on) {
            if (proximity == null) {
                val pm = getSystemService(PowerManager::class.java)
                if (pm?.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK) == true) {
                    proximity = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "OpenDial:proximity")
                }
            }
            proximity?.takeIf { !it.isHeld }?.acquire(4 * 60 * 60 * 1000L)
        } else {
            proximity?.takeIf { it.isHeld }?.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // Klawisz głośności przy dzwonieniu wycisza dzwonek (połączenie dalej można odebrać).
        if ((keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP) &&
            CallManager.snapshot().any { it.ringing }
        ) {
            Dialer.silenceRinger(this)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        updateProximity(false)
        super.onDestroy()
    }

    companion object {
        const val EXTRA_ANSWER = "answer"
        const val EXTRA_DIALPAD = "dialpad"

        fun intent(context: Context, answer: Boolean = false, showDialpad: Boolean = false): Intent =
            Intent(context, CallActivity::class.java)
                .putExtra(EXTRA_ANSWER, answer)
                .putExtra(EXTRA_DIALPAD, showDialpad)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
