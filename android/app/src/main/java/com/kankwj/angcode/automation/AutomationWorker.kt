package com.kankwj.angcode.automation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.Worker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kankwj.angcode.R
import com.kankwj.angcode.agents.AgentCancellationToken
import com.kankwj.angcode.agents.MissionAnalyzer
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.ui.LocalMissionExecutor
import com.kankwj.angcode.ui.MissionHistoryStore
import java.io.File
import java.util.concurrent.ConcurrentHashMap

internal object AutomationExecutionLocks {
    private val running = ConcurrentHashMap.newKeySet<String>()

    fun acquire(id: String): Boolean = running.add(id)
    fun release(id: String) {
        running.remove(id)
    }
}

class AutomationWorker(
    appContext: Context,
    params: WorkerParameters
) : Worker(appContext, params) {
    @Volatile
    private var cancellation: AgentCancellationToken? = null

    override fun doWork(): Result {
        val automationId = inputData.getString(AutomationScheduler.KEY_AUTOMATION_ID)
            ?: return Result.success()
        val store = AutomationStore(applicationContext)
        val definition = store.get(automationId) ?: return Result.success()

        if (!definition.enabled) {
            return Result.success()
        }

        if (!AutomationExecutionLocks.acquire(automationId)) {
            store.markResult(
                automationId,
                status = "skipped",
                detail = "Ya había una ejecución activa"
            )
            return Result.success()
        }

        val token = AgentCancellationToken()
        cancellation = token

        return try {
            setForegroundAsync(foregroundInfo(definition)).get()
            setProgressAsync(workDataOf("state" to "preparing")).get()

            val workspace = resolveWorkspace(definition)
            if (workspace == null) {
                store.markResult(
                    automationId,
                    status = "blocked",
                    detail = "Workspace no encontrado: " + definition.workspaceName
                )
                return Result.success()
            }

            val executor = LocalMissionExecutor(applicationContext)
            val readiness = executor.readiness()
            if (!readiness.ready) {
                store.markResult(
                    automationId,
                    status = "blocked",
                    detail = readiness.detail
                )
                return Result.success()
            }

            val analysis = MissionAnalyzer().analyze(
                definition.mission,
                workspace
            )

            setProgressAsync(workDataOf("state" to "running")).get()

            val outcome = executor.run(
                analysis = analysis,
                cancellation = token,
                approvedPermissions = emptySet(),
                workspaceOverride = workspace
            )

            MissionHistoryStore().save(
                workspace = outcome.workspace,
                analysis = analysis,
                result = outcome.result,
                modelName = outcome.modelName
            )

            val status = when {
                outcome.result.cancelled -> "cancelled"
                outcome.result.completed -> "success"
                outcome.result.stalled -> "stalled"
                else -> "incomplete"
            }
            val detail = buildString {
                append(
                    when (status) {
                        "success" -> "Misión completada"
                        "cancelled" -> "Misión cancelada"
                        "stalled" -> "Misión bloqueada"
                        else -> "Misión incompleta"
                    }
                )
                append(" · tareas=")
                append(outcome.result.taskResults.size)
            }

            store.markResult(automationId, status, detail)
            setProgressAsync(workDataOf("state" to status)).get()
            Result.success(
                workDataOf(
                    "status" to status,
                    "detail" to detail
                )
            )
        } catch (interrupted: InterruptedException) {
            Thread.currentThread().interrupt()
            store.markResult(
                automationId,
                status = "cancelled",
                detail = "Worker interrumpido"
            )
            Result.success()
        } catch (error: Throwable) {
            store.markResult(
                automationId,
                status = "error",
                detail = error.message ?: error::class.java.simpleName
            )
            // Periodic automations must survive one failed iteration.
            Result.success(
                workDataOf(
                    "status" to "error",
                    "detail" to (error.message ?: "Error")
                )
            )
        } finally {
            cancellation = null
            AutomationExecutionLocks.release(automationId)
        }
    }

    override fun onStopped() {
        cancellation?.cancel()
        cancellation = null
        super.onStopped()
    }

    private fun resolveWorkspace(definition: LocalAutomation): File? {
        val manager = WorkspaceManager(applicationContext)
        val root = manager.rootDirectory().canonicalFile
        val target = File(root, definition.workspaceName).canonicalFile
        if (!target.toPath().startsWith(root.toPath())) return null
        if (target == root || !target.isDirectory) return null
        return target
    }

    private fun foregroundInfo(definition: LocalAutomation): ForegroundInfo {
        ensureNotificationChannel()
        val cancelIntent = androidx.work.WorkManager
            .getInstance(applicationContext)
            .createCancelPendingIntent(id)

        val notification = NotificationCompat.Builder(
            applicationContext,
            CHANNEL_ID
        )
            .setSmallIcon(R.drawable.ic_angcode)
            .setContentTitle("AngCode · " + definition.name.take(60))
            .setContentText(definition.mission.take(120))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Cancelar",
                cancelIntent
            )
            .build()

        val notificationId = NOTIFICATION_BASE + (definition.id.hashCode() and 0x3FF)
        return ForegroundInfo(notificationId, notification)
    }

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(
            Context.NOTIFICATION_SERVICE
        ) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Automatizaciones de AngCode",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Misiones periódicas locales"
                setShowBadge(false)
            }
        )
    }

    companion object {
        private const val CHANNEL_ID = "angcode-automations"
        private const val NOTIFICATION_BASE = 9300
    }
}
