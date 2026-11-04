package com.blacksingularities.opencall.ai

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.os.Build
import com.blacksingularities.opencall.call.CallNotifications
import com.blacksingularities.opencall.data.BlockRepo
import com.blacksingularities.opencall.data.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Przekazanie połączenia agentowi i śledzenie, co z nim zrobił. */
object AiSync {
    private const val JOB_ID = 7041
    private const val DAY_MS = 86_400_000L

    /**
     * Wołane z filtra połączeń: zapisuje oczekiwanie i uruchamia śledzenie w tle. W trybie cichym
     * telefon jeszcze dzwoni, więc [announce] jest false – użytkownik może odebrać sam.
     */
    fun handoff(context: Context, number: String, hidden: Boolean, announce: Boolean) {
        val at = System.currentTimeMillis()
        val record = AiRecord(id = "local-$at", at = at, number = number, hidden = hidden)
        AiLog(context).update { listOf(record) + it }
        if (announce) CallNotifications.aiLive(context, record)
        schedule(context)
    }

    fun schedule(context: Context) {
        val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
        val builder = JobInfo.Builder(JOB_ID, ComponentName(context, AiPollJob::class.java))
            .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
        // Kwota pilnych zadań może się wyczerpać – wtedy zwykłe zadanie, trochę później.
        if (Build.VERSION.SDK_INT >= 31) {
            val urgent = runCatching { scheduler.schedule(builder.setExpedited(true).build()) }
                .getOrDefault(JobScheduler.RESULT_FAILURE)
            if (urgent == JobScheduler.RESULT_SUCCESS) return
            builder.setExpedited(false)
        }
        runCatching { scheduler.schedule(builder.build()) }
    }

    /** Jeden przebieg: pyta platformę i rozlicza wpisy. Zwraca true, gdy są jeszcze otwarte. */
    fun run(context: Context): Boolean {
        val app = context.applicationContext
        val cfg = SettingsStore(app).load().ai
        val log = AiLog(app)
        val now = System.currentTimeMillis()
        val oldestOpen = log.load().filter { it.open }.minOfOrNull { it.at } ?: now
        val since = minOf(oldestOpen, log.lastSync.coerceAtLeast(now - DAY_MS)) - 60_000L
        val key = SecretStore(app).get(cfg.provider.name)
        // Błąd sieci nie może zawiesić wpisów: bez rozmów scalanie i tak rozliczy przeterminowane.
        val calls = if (key.isBlank() || cfg.agentId.isBlank()) {
            null
        } else {
            runCatching { AgentPlatform.of(cfg.provider, key, cfg.customUrl).calls(cfg, since) }.getOrNull()
        }
        var finished = emptyList<AiRecord>()
        var stillOpen = false
        log.update { records ->
            val result = AiMatch.merge(records, calls.orEmpty(), now, cfg.ownNumber, cfg.keepTranscripts)
            finished = result.finished
            stillOpen = result.records.any { it.open }
            result.records
        }
        if (calls != null) log.lastSync = now
        finished.forEach { r ->
            if (r.verdict == AiVerdict.SPAM && cfg.autoBlockSpam && !r.transferred && r.number.isNotBlank()) {
                runCatching { BlockRepo(app).block(r.number) }
            }
            CallNotifications.aiResult(app, r, cfg)
        }
        return stillOpen
    }
}

/** Zadanie systemowe: dopytuje platformę, dopóki przekazane połączenia się nie rozliczą. */
class AiPollJob : JobService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartJob(params: JobParameters): Boolean {
        scope.launch {
            val deadline = System.currentTimeMillis() + 9 * 60_000L
            while (isActive && System.currentTimeMillis() < deadline) {
                val more = runCatching { AiSync.run(this@AiPollJob) }.getOrDefault(true)
                if (!more) break
                delay(4_000)
            }
            jobFinished(params, false)
        }
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        scope.coroutineContext.cancelChildren()
        return true
    }
}
