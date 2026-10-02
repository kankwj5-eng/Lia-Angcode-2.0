package com.kankwj.angcode.agents

data class MissionTaskState(
    val task: MissionTask,
    val startedAtMillis: Long? = null,
    val finishedAtMillis: Long? = null,
    val detail: String? = null
)

data class MissionSession(
    val analysis: MissionAnalysis,
    val tasks: List<MissionTaskState>,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    val completedCount: Int
        get() = tasks.count { it.task.status == AgentStatus.DONE }

    val failedCount: Int
        get() = tasks.count { it.task.status == AgentStatus.FAILED }

    val progress: Float
        get() = if (tasks.isEmpty()) 0f else completedCount.toFloat() / tasks.size
}

class MissionStateMachine {
    fun create(analysis: MissionAnalysis): MissionSession =
        MissionSession(
            analysis = analysis,
            tasks = analysis.plan.tasks.map(::MissionTaskState)
        )

    fun update(
        session: MissionSession,
        taskId: String,
        status: AgentStatus,
        detail: String? = null,
        nowMillis: Long = System.currentTimeMillis()
    ): MissionSession {
        val updated = session.tasks.map { state ->
            if (state.task.id != taskId) return@map state

            state.copy(
                task = state.task.copy(status = status),
                startedAtMillis = when {
                    state.startedAtMillis != null -> state.startedAtMillis
                    status == AgentStatus.WORKING -> nowMillis
                    else -> null
                },
                finishedAtMillis = when (status) {
                    AgentStatus.DONE, AgentStatus.FAILED -> nowMillis
                    else -> state.finishedAtMillis
                },
                detail = detail ?: state.detail
            )
        }
        return session.copy(tasks = updated)
    }

    fun prepareForResume(session: MissionSession): MissionSession =
        session.copy(
            tasks = session.tasks.map { state ->
                when (state.task.status) {
                    AgentStatus.DONE -> state
                    else -> state.copy(
                        task = state.task.copy(status = AgentStatus.QUEUED),
                        startedAtMillis = null,
                        finishedAtMillis = null
                    )
                }
            }
        )

    fun nextRunnable(
        session: MissionSession,
        resources: ResourceSnapshot,
        scheduler: MissionScheduler = MissionScheduler()
    ): ScheduleDecision {
        val livePlan = session.analysis.plan.copy(
            tasks = session.tasks.map { it.task }
        )
        return scheduler.schedule(livePlan, resources)
    }
}
