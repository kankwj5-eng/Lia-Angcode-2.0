package com.kankwj.angcode.agents

import java.util.UUID

enum class AgentRole(val title: String) {
    DIRECTOR("Director"),
    CODER("Código"),
    RESEARCHER("Investigación"),
    TESTER("Pruebas"),
    BUILDER("Builder"),
    BROWSER("Navegador")
}

enum class AgentStatus {
    IDLE,
    QUEUED,
    WORKING,
    DONE,
    FAILED
}

data class AgentCell(
    val id: String = UUID.randomUUID().toString(),
    val role: AgentRole,
    val status: AgentStatus,
    val currentTask: String,
    val progress: Float = 0f,
    val workspacePath: String? = null,
    val branch: String? = null,
    val sandbox: String? = null
)

data class MissionTask(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val role: AgentRole,
    val status: AgentStatus = AgentStatus.QUEUED,
    val dependsOn: Set<String> = emptySet()
)

data class MissionPlan(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val tasks: List<MissionTask>
)

class DirectorEngine {
    fun bootstrapPlan(title: String = "Preparar estación de desarrollo"): MissionPlan {
        val research = MissionTask(title = "Auditar runtime y herramientas", role = AgentRole.RESEARCHER, status = AgentStatus.WORKING)
        val code = MissionTask(title = "Construir interfaz y Tool Broker", role = AgentRole.CODER, dependsOn = setOf(research.id))
        val browser = MissionTask(title = "Preparar navegador agentico", role = AgentRole.BROWSER, dependsOn = setOf(research.id))
        val tests = MissionTask(title = "Validar runtime y permisos", role = AgentRole.TESTER, dependsOn = setOf(code.id))
        val build = MissionTask(title = "Generar APK de desarrollo", role = AgentRole.BUILDER, dependsOn = setOf(code.id, tests.id))
        return MissionPlan(title = title, tasks = listOf(research, code, browser, tests, build))
    }

    fun cellsFor(plan: MissionPlan): List<AgentCell> {
        return plan.tasks.map { task ->
            AgentCell(
                role = task.role,
                status = task.status,
                currentTask = task.title,
                progress = when (task.status) {
                    AgentStatus.DONE -> 1f
                    AgentStatus.WORKING -> .42f
                    else -> 0f
                }
            )
        }
    }
}
