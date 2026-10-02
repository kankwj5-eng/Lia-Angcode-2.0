package com.kankwj.angcode.mission

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.kankwj.angcode.MainActivity
import com.kankwj.angcode.agents.AgentCancellationToken
import com.kankwj.angcode.agents.MissionAnalyzer
import com.kankwj.angcode.runtime.ActiveProjectStore
import com.kankwj.angcode.ui.LocalMissionExecutor
import com.kankwj.angcode.ui.MissionHistoryStore
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicReference

data class BackgroundMissionSnapshot(
    val running: Boolean,
    val mission: String?,
    val detail: String,
    val startedAtMillis: Long? = null
)

object BackgroundMissionState {
    private val state = AtomicReference(
        BackgroundMissionSnapshot(
            running = false,
            mission = null,
            detail = "Sin misión en segundo plano"
        )
    )

    fun snapshot(): BackgroundMissionSnapshot = state.get()

    internal fun update(value: BackgroundMissionSnapshot) {
        state.set(value)
    }
}

class MissionForegroundService : Service() {
    private val executor = Executors.newSingleThreadExecutor()
    private var future: Future<*>? = null
    private var cancellation: AgentCancellationToken? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                cancelCurrent("Cancelada por el usuario")
                stopForegroundCompat(remove = true)
                stopSelf()
            }

            ACTION_RUN -> {
                val mission = intent.getStringExtra(EXTRA_MISSION)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: return START_NOT_STICKY

                if (future?.isDone == false) {
                    updateNotification(
                        title = "AngCode ya está trabajando",
                        text = BackgroundMissionState.snapshot().mission ?: "Misión activa"
                    )
                    return START_NOT_STICKY
                }

                startForeground(
                    NOTIFICATION_ID,
                    buildRunningNotification(
                        title = "AngCode trabajando",
                        text = mission.take(120)
                    )
                )
                startMission(mission)
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        if (future?.isDone == false) {
            cancelCurrent("Servicio detenido")
        }
        executor.shutdownNow()
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startMission(mission: String) {
        val token = AgentCancellationToken()
        cancellation = token
        acquireWakeLock()

        val started = System.currentTimeMillis()
        BackgroundMissionState.update(
            BackgroundMissionSnapshot(
                running = true,
                mission = mission,
                detail = "Analizando misión",
                startedAtMillis = started
            )
        )

        future = executor.submit {
            try {
                val workspace = ActiveProjectStore(applicationContext)
                    .resolveActiveOrScratch()
                val analysis = MissionAnalyzer().analyze(mission, workspace)

                if (token.isCancelled) return@submit

                BackgroundMissionState.update(
                    BackgroundMissionSnapshot(
                        running = true,
                        mission = mission,
                        detail = "Ejecutando " + analysis.plan.tasks.size + " tareas",
                        startedAtMillis = started
                    )
                )
                updateNotification(
                    title = "AngCode trabajando",
                    text = analysis.plan.tasks.size.toString() + " tareas · " +
                        analysis.project.kind.name
                )

                val outcome = LocalMissionExecutor(applicationContext)
                    .run(analysis, cancellation = token)

                if (!token.isCancelled) {
                    MissionHistoryStore().save(
                        workspace = outcome.workspace,
                        analysis = analysis,
                        result = outcome.result,
                        modelName = outcome.modelName
                    )

                    val detail = when {
                        outcome.result.completed -> "Misión completada"
                        outcome.result.cancelled -> "Misión cancelada"
                        outcome.result.stalled -> "Misión bloqueada"
                        else -> "Misión incompleta"
                    }

                    BackgroundMissionState.update(
                        BackgroundMissionSnapshot(
                            running = false,
                            mission = mission,
                            detail = detail,
                            startedAtMillis = started
                        )
                    )

                    postFinishedNotification(
                        success = outcome.result.completed,
                        title = detail,
                        text = mission.take(120)
                    )
                }
            } catch (interrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                BackgroundMissionState.update(
                    BackgroundMissionSnapshot(
                        running = false,
                        mission = mission,
                        detail = "Misión cancelada",
                        startedAtMillis = started
                    )
                )
            } catch (error: Throwable) {
                BackgroundMissionState.update(
                    BackgroundMissionSnapshot(
                        running = false,
                        mission = mission,
                        detail = error.message ?: "Falló la misión",
                        startedAtMillis = started
                    )
                )
                postFinishedNotification(
                    success = false,
                    title = "AngCode encontró un error",
                    text = (error.message ?: mission).take(120)
                )
            } finally {
                cancellation = null
                future = null
                releaseWakeLock()
                stopForegroundCompat(remove = true)
                stopSelf()
            }
        }
    }

    private fun cancelCurrent(detail: String) {
        cancellation?.cancel()
        future?.cancel(true)
        future = null
        cancellation = null
        BackgroundMissionState.update(
            BackgroundMissionSnapshot(
                running = false,
                mission = BackgroundMissionState.snapshot().mission,
                detail = detail,
                startedAtMillis = BackgroundMissionState.snapshot().startedAtMillis
            )
        )
        releaseWakeLock()
    }

    private fun acquireWakeLock() {
        releaseWakeLock()
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = power.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "AngCode:Mission"
        ).apply {
            setReferenceCounted(false)
            acquire(WAKELOCK_TIMEOUT_MS)
        }
    }

    private fun releaseWakeLock() {
        wakeLock?.let { lock ->
            if (lock.isHeld) runCatching { lock.release() }
        }
        wakeLock = null
    }

    private fun buildRunningNotification(
        title: String,
        text: String
    ): android.app.Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancelIntent = PendingIntent.getService(
            this,
            1,
            Intent(this, MissionForegroundService::class.java).apply {
                action = ACTION_CANCEL
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.kankwj.angcode.R.drawable.ic_angcode)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                cancelIntent
            )
            .build()
    }

    private fun updateNotification(title: String, text: String) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, buildRunningNotification(title, text))
    }

    private fun postFinishedNotification(
        success: Boolean,
        title: String,
        text: String
    ) {
        val openIntent = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.kankwj.angcode.R.drawable.ic_angcode)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openIntent)
            .setAutoCancel(true)
            .setCategory(
                if (success) NotificationCompat.CATEGORY_STATUS
                else NotificationCompat.CATEGORY_ERROR
            )
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(FINISHED_NOTIFICATION_ID, notification)
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Misiones de AngCode",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Ejecución local de agentes y herramientas en segundo plano"
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private fun stopForegroundCompat(remove: Boolean) {
        if (Build.VERSION.SDK_INT >= 24) {
            stopForeground(
                if (remove) STOP_FOREGROUND_REMOVE
                else STOP_FOREGROUND_DETACH
            )
        } else {
            @Suppress("DEPRECATION")
            stopForeground(remove)
        }
    }

    companion object {
        private const val CHANNEL_ID = "angcode-missions"
        private const val NOTIFICATION_ID = 8402
        private const val FINISHED_NOTIFICATION_ID = 8403
        private const val EXTRA_MISSION = "mission"
        private const val ACTION_RUN = "com.kankwj.angcode.mission.RUN"
        private const val ACTION_CANCEL = "com.kankwj.angcode.mission.CANCEL"
        private const val WAKELOCK_TIMEOUT_MS = 2L * 60L * 60L * 1000L

        fun start(context: Context, mission: String) {
            val intent = Intent(
                context.applicationContext,
                MissionForegroundService::class.java
            ).apply {
                action = ACTION_RUN
                putExtra(EXTRA_MISSION, mission)
            }
            ContextCompat.startForegroundService(context.applicationContext, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(
                context.applicationContext,
                MissionForegroundService::class.java
            ).apply {
                action = ACTION_CANCEL
            }
            context.applicationContext.startService(intent)
        }
    }
}
