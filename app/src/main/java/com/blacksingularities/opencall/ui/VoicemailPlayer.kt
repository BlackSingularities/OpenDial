package com.blacksingularities.opencall.ui

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.provider.VoicemailContract
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.blacksingularities.opencall.i18n.tr

/**
 * Odsłuch poczty głosowej z rejestru połączeń. Nagrania pobiera systemowa usługa wizualnej
 * poczty głosowej (gdy operator ją obsługuje); domyślny dialer ma do nich dostęp.
 */
object VoicemailPlayer {
    /** Wpis rejestru, którego nagranie właśnie gra – przycisk zmienia się na „Zatrzymaj”. */
    var playingId by mutableStateOf<Long?>(null)
        private set
    private var player: MediaPlayer? = null

    fun toggle(context: Context, id: Long, uri: String) {
        if (playingId == id) {
            stop()
            return
        }
        stop()
        val app = context.applicationContext
        val target = Uri.parse(uri)
        val mp = MediaPlayer()
        runCatching {
            mp.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            mp.setDataSource(app, target)
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                stop()
                true
            }
            mp.setOnPreparedListener { it.start() }
            mp.prepareAsync()
            player = mp
            playingId = id
            markRead(app, target)
        }.onFailure {
            mp.release()
            Toast.makeText(app, tr("Nie można odtworzyć nagrania"), Toast.LENGTH_SHORT).show()
        }
    }

    fun stop() {
        player?.let { p -> runCatching { p.stop() }; p.release() }
        player = null
        playingId = null
    }

    private fun markRead(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.update(uri, ContentValues().apply { put(VoicemailContract.Voicemails.IS_READ, 1) }, null, null)
        }
    }
}
