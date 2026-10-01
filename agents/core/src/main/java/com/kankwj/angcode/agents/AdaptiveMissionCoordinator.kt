package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolContext
import java.util.concurrent.Callable
import java.util.concurrent.Executors

class SerializedModelGateway(
    private val delegate: ModelGateway
) : ModelGateway {
    private val lock = Any()

    override fun complete(request: ModelRequest): ModelResponse =
        synchronized(lock) {
            delegate.complete(request)
        }
}

data class MissionTaskResult(
    val taskId: String,
    val role: AgentRole,
    val completed: Boolean,
    val answer: String,
    val stepsUsed: Int
)

data class MissionCoordinatorResult(
    val session: MissionSession,
    val taskResults: List<MissionTaskResult>,
    val stalled: Boolean
) {
    val completed: Boolean
        get() = !stalled &&
            session.tasks.isNotEmpty() &&
            session.tasks.all { it.task.status == AgentStatus.DONE }
}

class AdaptiveMissionCoordinator(
    model: ModelGateway,
    private val broker: ToolBroker,
    private val eventBus: EventBus = EventBus(),
    private val scheduler: MissionScheduler = MissionScheduler()
) {
    private val sharedModel = SerializedModelGateway(model)

    fun run(
        initial: MissionSession,
        rootContext: ToolContext,
        resources: ResourceSnapshot,
        config: AgentRunConfig = AgentRunConfig(
            maxSteps = 10,
            planningInterval = 4,
            memoryWindowChars = 18_000
        )
    ): MissionCoordinatorResult {
        var session = initial
        val results = mutableListOf<MissionTaskResult>()
        val stateMachine = MissionStateMachine()
        var stalled = false

        while (session.tasks.any {
                it.task.status == AgentStatus.QUEUED ||
                    it.task.status == AgentStatus.WORKING
            }
        ) {
            val decision = stateMachine.nextRunnable(
                session = session,
                resources = resources,
                scheduler = scheduler
            )

            if (decision.runnable.isEmpty()) {
                stalled = true
                break
            }

            val batch = chooseSafeBatch(decision.runnable)
            val workerCount = batch.size.coerceAtLeast(1)

            batch.forEach { task ->
                session = stateMachine.update(
                    session,
                    task.id,
                    AgentStatus.WORKING,
                    detail = "Worker iniciado"
                )
            }

            val executor = Executors.newFixedThreadPool(workerCount)
            val calls = batch.map { task ->
                Callable {
                    runTask(
                        task = task,
                        mission = session.analysis,
                        rootContext = rootContext,
                        previous = session,
                        config = config
                    )
                }
            }

            val batchResults = try {
                executor.invokeAll(calls).map { it.get() }
            } finally {
                executor.shutdownNow()
            }

            for (taskResult in batchResults) {
                results += taskResult
                session = stateMachine.update(
                    session = session,
                    taskId = taskResult.taskId,
                    status = if (taskResult.completed) AgentStatus.DONE else AgentStatus.FAILED,
                    detail = taskResult.answer
                )

                eventBus.publish(
                    AgentEvent(
                        type = if (taskResult.completed) {
                            "mission.task.completed"
                        } else {
                            "mission.task.failed"
                        },
                        source = taskResult.role.name,
                        payload = mapOf(
                            "taskId" to taskResult.taskId,
                            "steps" to taskResult.stepsUsed.toString()
                        )
                    )
                )
            }

            if (batchResults.any { !it.completed }) {
                val failedIds = session.tasks
                    .filter { it.task.status == AgentStatus.FAILED }
                    .map { it.task.id }
                    .toSet()

                val blocked = session.tasks.filter { state ->
                    state.task.status == AgentStatus.QUEUED &&
                        state.task.dependsOn.any(failedIds::contains)
                }

                blocked.forEach { state ->
                    session = stateMachine.update(
                        session,
                        state.task.id,
                        AgentStatus.FAILED,
                        detail = "Dependencia fallida"
                    )
                }
            }
        }

        return MissionCoordinatorResult(
            session = session,
            taskResults = results,
            stalled = stalled
        )
    }

    private fun chooseSafeBatch(
        runnable: List<MissionTask>
    ): List<MissionTask> {
        val readOnly = runnable.filter {
            it.role == AgentRole.RESEARCHER ||
                it.role == AgentRole.BROWSER
        }

        return if (readOnly.isNotEmpty()) {
            readOnly
        } else {
            runnable.take(1)
        }
    }

    private fun runTask(
        task: MissionTask,
        mission: MissionAnalysis,
        rootContext: ToolContext,
        previous: MissionSession,
        config: AgentRunConfig
    ): MissionTaskResult {
        val permissions = AgentPermissionProfiles.constrainedTo(
            task.role,
            rootContext.grantedPermissions
        )

        val context = rootContext.copy(
            grantedPermissions = permissions
        )

        val engine = ToolCallingAgentEngine(
            model = sharedModel,
            broker = broker,
            eventBus = eventBus
        )

        val priorResults = previous.tasks
            .filter { it.task.status == AgentStatus.DONE }
            .mapNotNull { state ->
                state.detail?.let { detail ->
                    state.task.role.name + ": " + detail.take(1500)
                }
            }

        val result = engine.run(
            task = buildString {
                appendLine("MISIÓN GLOBAL")
                appendLine(mission.mission)
                appendLine()
                appendLine("TU ROL")
                appendLine(task.role.name)
                appendLine()
                appendLine("TU TAREA")
                appendLine(task.title)
                if (priorResults.isNotEmpty()) {
                    appendLine()
                    appendLine("RESULTADOS PREVIOS")
                    priorResults.forEach { appendLine("- " + it) }
                }
                appendLine()
                appendLine("Trabaja solo dentro de los permisos de tu rol. " +
                    "Si una herramienta es denegada, no intentes saltarte el Tool Broker.")
            },
            context = context,
            config = config
        )

        return MissionTaskResult(
            taskId = task.id,
            role = task.role,
            completed = result.completed,
            answer = result.answer,
            stepsUsed = result.stepsUsed
        )
    }
}
