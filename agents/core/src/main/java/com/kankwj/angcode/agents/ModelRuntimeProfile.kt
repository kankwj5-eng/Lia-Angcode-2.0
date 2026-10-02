package com.kankwj.angcode.agents

data class ModelRuntimeProfile(
    val id: String,
    val contextSize: Int,
    val threads: Int,
    val batchSize: Int,
    val maxAgentSteps: Int,
    val memoryWindowChars: Int,
    val reason: String
)

object ModelRuntimeProfileSelector {
    fun select(
        resources: ResourceSnapshot,
        logicalCores: Int = Runtime.getRuntime().availableProcessors()
    ): ModelRuntimeProfile {
        val cores = logicalCores.coerceIn(1, 16)
        val constrained =
            resources.availableRamMb < 1_800 ||
                resources.thermalLevel >= 4 ||
                (resources.batteryPercent < 12 && !resources.charging)

        if (constrained) {
            return ModelRuntimeProfile(
                id = "ahorro",
                contextSize = 2_048,
                threads = minOf(2, cores),
                batchSize = 128,
                maxAgentSteps = 7,
                memoryWindowChars = 10_000,
                reason = reason("ahorro", resources)
            )
        }

        val balanced =
            resources.availableRamMb < 3_600 ||
                resources.thermalLevel >= 2 ||
                (resources.batteryPercent < 25 && !resources.charging)

        if (balanced) {
            return ModelRuntimeProfile(
                id = "balanceado",
                contextSize = 4_096,
                threads = minOf(4, cores),
                batchSize = 256,
                maxAgentSteps = 10,
                memoryWindowChars = 18_000,
                reason = reason("balanceado", resources)
            )
        }

        return ModelRuntimeProfile(
            id = "amplio",
            contextSize = if (resources.availableRamMb >= 6_000) 8_192 else 6_144,
            threads = minOf(6, cores),
            batchSize = 512,
            maxAgentSteps = 14,
            memoryWindowChars = 28_000,
            reason = reason("amplio", resources)
        )
    }

    private fun reason(
        id: String,
        resources: ResourceSnapshot
    ): String =
        id + " · ram=" + resources.availableRamMb + "MB" +
            " battery=" + resources.batteryPercent + "%" +
            " thermal=" + resources.thermalLevel
}
