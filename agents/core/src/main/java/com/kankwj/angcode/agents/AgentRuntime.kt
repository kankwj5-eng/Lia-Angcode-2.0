package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import java.util.concurrent.atomic.AtomicBoolean

enum class AgentMemoryKind {
    TASK, PLAN, MODEL, TOOL_CALL, OBSERVATION, ERROR, FINAL
}

data class AgentMemoryStep(
    val kind: AgentMemoryKind,
    val text: String,
    val toolId: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis()
)

class AgentRunMemory {
    private val steps = mutableListOf<AgentMemoryStep>()

    fun add(kind: AgentMemoryKind, text: String, toolId: String? = null) {
        steps += AgentMemoryStep(kind, text, toolId)
    }

    fun snapshot(): List<AgentMemoryStep> = steps.toList()

    fun render(maxChars: Int = 16_000): String {
        val all = steps.joinToString("\n\n") { step ->
            buildString {
                append("[")
                append(step.kind.name)
                append("]")
                step.toolId?.let { append(" ").append(it) }
                append("\n")
                append(step.text)
            }
        }
        return if (all.length <= maxChars) all else all.takeLast(maxChars)
    }
}

class AgentCancellationToken {
    private val cancelled = AtomicBoolean(false)
    val isCancelled: Boolean get() = cancelled.get()
    fun cancel(): Boolean = cancelled.compareAndSet(false, true)
}

data class AgentRunConfig(
    val maxSteps: Int = 12,
    val planningInterval: Int = 4,
    val memoryWindowChars: Int = 16_000
)

data class AgentRunResult(
    val completed: Boolean,
    val answer: String,
    val stepsUsed: Int,
    val memory: List<AgentMemoryStep>,
    val cancelled: Boolean = false
)

fun interface FinalAnswerValidator {
    fun isValid(answer: String, memory: List<AgentMemoryStep>): Boolean
}

/**
 * Bounded tool-calling loop. The model can request capabilities, but every
 * real action still crosses ToolBroker and its permission/policy layer.
 */
class ToolCallingAgentEngine(
    private val model: ModelGateway,
    private val broker: ToolBroker,
    private val eventBus: EventBus = EventBus(),
    private val validators: List<FinalAnswerValidator> = emptyList()
) {
    fun run(
        task: String,
        context: ToolContext,
        config: AgentRunConfig = AgentRunConfig(),
        cancellation: AgentCancellationToken = AgentCancellationToken()
    ): AgentRunResult {
        require(config.maxSteps in 1..100)
        require(config.planningInterval >= 0)

        val memory = AgentRunMemory()
        memory.add(AgentMemoryKind.TASK, task)
        val toolIds = broker.availableTools().map { tool ->
            tool.id + " — " + tool.description
        }
        var lastText = ""

        for (step in 1..config.maxSteps) {
            if (cancellation.isCancelled) {
                memory.add(AgentMemoryKind.ERROR, "Misión cancelada por el usuario.")
                return AgentRunResult(false, "Misión cancelada.", step - 1, memory.snapshot(), true)
            }
            if (
                config.planningInterval > 0 &&
                (step == 1 || (step - 1) % config.planningInterval == 0)
            ) {
                val plan = model.complete(
                    ModelRequest(
                        system = "Haz un plan corto para continuar. No uses herramientas en este turno.",
                        user = memory.render(config.memoryWindowChars),
                        tools = emptyList(),
                        maxOutputTokens = 384
                    )
                )
                if (cancellation.isCancelled) {
                    memory.add(AgentMemoryKind.ERROR, "Misión cancelada durante planificación.")
                    return AgentRunResult(false, "Misión cancelada.", step - 1, memory.snapshot(), true)
                }
                if (plan.text.isNotBlank()) {
                    memory.add(AgentMemoryKind.PLAN, plan.text.trim())
                    eventBus.publish(
                        AgentEvent(
                            type = "agent.plan",
                            source = "tool-calling-agent",
                            payload = mapOf("step" to step.toString())
                        )
                    )
                }
            }

            val response = model.complete(
                ModelRequest(
                    system = "Resuelve la misión paso a paso. Usa herramientas cuando haga falta. " +
                        "No afirmes éxito hasta haber verificado el resultado.",
                    user = memory.render(config.memoryWindowChars),
                    tools = toolIds
                )
            )

            if (cancellation.isCancelled) {
                memory.add(AgentMemoryKind.ERROR, "Misión cancelada durante inferencia.")
                return AgentRunResult(false, "Misión cancelada.", step - 1, memory.snapshot(), true)
            }
            lastText = response.text.trim()
            if (lastText.isNotBlank()) memory.add(AgentMemoryKind.MODEL, lastText)

            val toolId = response.requestedTool
            if (toolId != null) {
                memory.add(
                    AgentMemoryKind.TOOL_CALL,
                    response.toolArguments.entries.joinToString { it.key + "=" + it.value },
                    toolId
                )

                if (cancellation.isCancelled) {
                    memory.add(AgentMemoryKind.ERROR, "Misión cancelada antes de ejecutar herramienta.", toolId)
                    return AgentRunResult(false, "Misión cancelada.", step - 1, memory.snapshot(), true)
                }
                val result = broker.execute(
                    ToolCall(toolId, response.toolArguments),
                    context
                )
                if (cancellation.isCancelled) {
                    memory.add(AgentMemoryKind.ERROR, "Misión cancelada después de herramienta.", toolId)
                    return AgentRunResult(false, "Misión cancelada.", step, memory.snapshot(), true)
                }
                memory.add(
                    if (result.ok) AgentMemoryKind.OBSERVATION else AgentMemoryKind.ERROR,
                    result.output,
                    toolId
                )
                eventBus.publish(
                    AgentEvent(
                        type = if (result.ok) "tool.completed" else "tool.failed",
                        source = toolId,
                        payload = result.metadata + mapOf("step" to step.toString())
                    )
                )
                continue
            }

            if (lastText.isNotBlank()) {
                val snapshot = memory.snapshot()
                if (validators.all { it.isValid(lastText, snapshot) }) {
                    memory.add(AgentMemoryKind.FINAL, lastText)
                    eventBus.publish(
                        AgentEvent(
                            type = "agent.completed",
                            source = "tool-calling-agent",
                            payload = mapOf("steps" to step.toString())
                        )
                    )
                    return AgentRunResult(true, lastText, step, memory.snapshot())
                }

                memory.add(
                    AgentMemoryKind.ERROR,
                    "La respuesta final no pasó las validaciones. Continúa trabajando."
                )
            }
        }

        return AgentRunResult(
            completed = false,
            answer = lastText.ifBlank { "Límite de pasos alcanzado sin respuesta final." },
            stepsUsed = config.maxSteps,
            memory = memory.snapshot()
        )
    }
}
