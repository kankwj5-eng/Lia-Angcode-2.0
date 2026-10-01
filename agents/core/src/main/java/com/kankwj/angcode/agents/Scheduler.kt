package com.kankwj.angcode.agents

data class ResourceSnapshot(
    val availableRamMb: Int,
    val batteryPercent: Int,
    val charging: Boolean,
    val thermalLevel: Int = 0
)

data class ScheduleDecision(
    val runnable: List<MissionTask>,
    val deferred: List<MissionTask>,
    val reason: String
)

class MissionScheduler(
    private val minimumRamPerWorkerMb: Int = 700,
    private val maxParallelWorkers: Int = 3
) {
    fun schedule(plan: MissionPlan, resources: ResourceSnapshot): ScheduleDecision {
        val completed = plan.tasks.filter { it.status == AgentStatus.DONE }.map { it.id }.toSet()
        val candidates = plan.tasks.filter { task ->
            task.status == AgentStatus.QUEUED && task.dependsOn.all(completed::contains)
        }

        val thermalLimit = if (resources.thermalLevel >= 3) 1 else maxParallelWorkers
        val memoryLimit = (resources.availableRamMb / minimumRamPerWorkerMb).coerceAtLeast(1)
        val batteryLimit = if (resources.batteryPercent < 20 && !resources.charging) 1 else maxParallelWorkers
        val concurrency = minOf(thermalLimit, memoryLimit, batteryLimit, maxParallelWorkers)

        val runnable = candidates.take(concurrency)
        return ScheduleDecision(
            runnable = runnable,
            deferred = candidates.drop(concurrency),
            reason = "workers=${concurrency} ram=${resources.availableRamMb}MB battery=${resources.batteryPercent}% thermal=${resources.thermalLevel}"
        )
    }
}
