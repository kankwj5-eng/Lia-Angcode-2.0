package com.kankwj.angcode.automation

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class AutomationScheduler(context: Context) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    fun schedule(definition: LocalAutomation) {
        require(definition.enabled) { "La automatización está pausada" }
        val errors = AutomationRules.validate(definition)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val constraints = constraints(definition)
        val request = PeriodicWorkRequestBuilder<AutomationWorker>(
            definition.intervalMinutes,
            TimeUnit.MINUTES
        )
            .setInputData(input(definition.id))
            .setConstraints(constraints)
            .addTag(TAG_PERIODIC)
            .addTag(tagFor(definition.id))
            .build()

        workManager.enqueueUniquePeriodicWork(
            periodicName(definition.id),
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun runNow(definition: LocalAutomation) {
        val errors = AutomationRules.validate(definition)
        require(errors.isEmpty()) { errors.joinToString("; ") }

        val request = OneTimeWorkRequestBuilder<AutomationWorker>()
            .setInputData(input(definition.id))
            .setConstraints(constraints(definition))
            .addTag(TAG_MANUAL)
            .addTag(tagFor(definition.id))
            .build()

        workManager.enqueueUniqueWork(
            runNowName(definition.id),
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    fun cancelPeriodic(id: String) {
        workManager.cancelUniqueWork(periodicName(id))
    }

    fun cancelAll(id: String) {
        workManager.cancelUniqueWork(periodicName(id))
        workManager.cancelUniqueWork(runNowName(id))
    }

    private fun constraints(definition: LocalAutomation): Constraints =
        Constraints.Builder()
            .apply {
                if (definition.requireNetwork) {
                    setRequiredNetworkType(NetworkType.CONNECTED)
                }
                setRequiresCharging(definition.requireCharging)
            }
            .build()

    companion object {
        const val KEY_AUTOMATION_ID = "automationId"
        private const val TAG_PERIODIC = "angcode-automation-periodic"
        private const val TAG_MANUAL = "angcode-automation-manual"

        internal fun periodicName(id: String) = "angcode-automation-periodic-" + id
        internal fun runNowName(id: String) = "angcode-automation-now-" + id
        internal fun tagFor(id: String) = "angcode-automation-" + id

        private fun input(id: String): Data =
            Data.Builder()
                .putString(KEY_AUTOMATION_ID, id)
                .build()
    }
}
