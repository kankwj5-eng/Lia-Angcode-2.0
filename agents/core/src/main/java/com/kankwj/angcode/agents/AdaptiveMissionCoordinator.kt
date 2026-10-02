package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import java.io.File
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

data class MissionIsolation(
    val enabled: Boolean,
    val cellName: String? = null,
    val branch: String? = null,
    val worktreePath: String? = null,
    val merged: Boolean = false,
    val review: String? = null,
    val detail: String = ""
)

data class MissionCoordinatorResult(
    val session: MissionSession,
    val taskResults: List<MissionTaskResult>,
    val stalled: Boolean,
    val isolation: MissionIsolation = MissionIsolation(false)
) {
    val completed: Boolean
        get() = !stalled &&
            session.tasks.isNotEmpty() &&
            session.tasks.all { it.task.status == AgentStatus.DONE }
}

object MissionIsolationPolicy {
    fun canIsolate(
        gitAvailable: Boolean,
        worktreePermission: Boolean,
        gitStatusOutput: String
    ): Boolean {
        if (!gitAvailable || !worktreePermission) return false
        val lines = gitStatusOutput.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .toList()
        if (lines.isEmpty()) return false
        val changes = if (lines.first().startsWith("##")) lines.drop(1) else lines
        return changes.isEmpty()
    }

    fun mutableRole(role: AgentRole): Boolean =
        role == AgentRole.CODER ||
            role == AgentRole.TESTER ||
            role == AgentRole.BUILDER
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

        var isolation = prepareIsolation(initial, rootContext)
        val mutableContext = isolation.worktreePath?.let { path ->
            rootContext.copy(workspace = File(path))
        } ?: rootContext

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
            val snapshot = session
            val calls = batch.map { task ->
                Callable {
                    runTask(
                        task = task,
                        mission = snapshot.analysis,
                        rootContext = if (MissionIsolationPolicy.mutableRole(task.role)) {
                            mutableContext
                        } else {
                            rootContext
                        },
                        previous = snapshot,
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
                            "steps" to taskResult.stepsUsed.toString(),
                            "workspace" to (
                                if (MissionIsolationPolicy.mutableRole(taskResult.role)) {
                                    mutableContext.workspace.absolutePath
                                } else {
                                    rootContext.workspace.absolutePath
                                }
                            )
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

        val preliminary = MissionCoordinatorResult(
            session = session,
            taskResults = results,
            stalled = stalled,
            isolation = isolation
        )

        if (preliminary.completed && isolation.enabled) {
            isolation = finalizeIsolation(
                mission = initial.analysis,
                rootContext = rootContext,
                mutableContext = mutableContext,
                isolation = isolation
            )
        }

        return MissionCoordinatorResult(
            session = session,
            taskResults = results,
            stalled = stalled,
            isolation = isolation
        )
    }

    private fun prepareIsolation(
        initial: MissionSession,
        rootContext: ToolContext
    ): MissionIsolation {
        if (initial.analysis.plan.tasks.none { MissionIsolationPolicy.mutableRole(it.role) }) {
            return MissionIsolation(false, detail = "Misión sin fase mutable")
        }

        val gitAvailable = "git" in rootContext.executables
        val hasPermission = ToolPermission.WORKTREE_MANAGE in rootContext.grantedPermissions
        if (!gitAvailable || !hasPermission) {
            return MissionIsolation(false, detail = "Git/worktree no disponible")
        }

        val status = broker.execute(ToolCall("git.status"), rootContext)
        if (!status.ok) {
            return MissionIsolation(false, detail = "Workspace no es repo Git")
        }

        if (!MissionIsolationPolicy.canIsolate(true, true, status.output)) {
            return MissionIsolation(false, detail = "Repo con cambios locales; aislamiento omitido")
        }

        val suffix = initial.analysis.plan.id
            .replace(Regex("[^A-Za-z0-9]"), "")
            .take(10)
            .ifBlank { "mission" }
        val cellName = "mission-" + suffix
        val branch = "angcode/" + cellName

        val created = broker.execute(
            ToolCall(
                "git.worktree.create",
                mapOf(
                    "name" to cellName,
                    "branch" to branch,
                    "base" to "HEAD"
                )
            ),
            rootContext
        )

        if (!created.ok) {
            return MissionIsolation(false, detail = "No se pudo crear worktree: " + created.output)
        }

        return MissionIsolation(
            enabled = true,
            cellName = cellName,
            branch = created.metadata["branch"] ?: branch,
            worktreePath = created.metadata["worktree"],
            detail = "Worktree aislado creado"
        )
    }

    private fun finalizeIsolation(
        mission: MissionAnalysis,
        rootContext: ToolContext,
        mutableContext: ToolContext,
        isolation: MissionIsolation
    ): MissionIsolation {
        val status = broker.execute(ToolCall("git.status"), mutableContext)
        if (!status.ok) {
            return isolation.copy(detail = "Misión terminó, pero no se pudo inspeccionar Git")
        }

        val dirty = status.output.lineSequence()
            .map(String::trim)
            .filter(String::isNotEmpty)
            .let { lines ->
                val list = lines.toList()
                if (list.firstOrNull()?.startsWith("##") == true) list.drop(1) else list
            }
            .isNotEmpty()

        if (dirty) {
            val add = broker.execute(
                ToolCall("git.add", mapOf("path" to ".")),
                mutableContext
            )
            if (!add.ok) {
                return isolation.copy(detail = "Cambios listos, pero git.add falló: " + add.output)
            }

            val commit = broker.execute(
                ToolCall(
                    "git.commit",
                    mapOf(
                        "message" to (
                            "angcode: " + mission.mission
                                .replace(Regex("\\s+"), " ")
                                .trim()
                                .take(72)
                        )
                    )
                ),
                mutableContext
            )
            if (!commit.ok) {
                return isolation.copy(detail = "Cambios listos, pero commit falló: " + commit.output)
            }
        }

        val branch = isolation.branch
            ?: return isolation.copy(detail = "Rama de misión desconocida")

        val review = broker.execute(
            ToolCall(
                "git.review",
                mapOf("branch" to branch, "base" to "HEAD")
            ),
            rootContext
        )
        if (!review.ok) {
            return isolation.copy(detail = "Review Git falló: " + review.output)
        }

        val merge = broker.execute(
            ToolCall("git.merge", mapOf("branch" to branch)),
            rootContext
        )
        if (!merge.ok) {
            return isolation.copy(
                review = review.output,
                detail = "Review listo, pero merge falló: " + merge.output
            )
        }

        isolation.cellName?.let { name ->
            broker.execute(
                ToolCall(
                    "git.worktree.remove",
                    mapOf("name" to name, "force" to "false")
                ),
                rootContext
            )
        }

        return isolation.copy(
            merged = true,
            review = review.output,
            detail = "Cambios revisados y fusionados"
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
                appendLine()
                appendLine("WORKSPACE ASIGNADO")
                appendLine(context.workspace.absolutePath)
                if (priorResults.isNotEmpty()) {
                    appendLine()
                    appendLine("RESULTADOS PREVIOS")
                    priorResults.forEach { appendLine("- " + it) }
                }
                appendLine()
                appendLine(
                    "Trabaja solo dentro de los permisos de tu rol. " +
                        "Si una herramienta es denegada, no intentes saltarte el Tool Broker. " +
                        "No declares éxito sin verificación."
                )
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
