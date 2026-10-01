package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.ArrayDeque

class AgentRuntimeTest {
    @Test
    fun toolObservationFeedsNextStep() {
        val responses = ArrayDeque(
            listOf(
                ModelResponse("Plan: comprobar."),
                ModelResponse(
                    text = "Necesito un dato.",
                    requestedTool = "test.echo",
                    toolArguments = mapOf("value" to "42")
                ),
                ModelResponse("Resultado verificado: 42")
            )
        )

        val model = ModelGateway { responses.removeFirst() }
        val broker = ToolBroker().apply {
            register(object : AgentTool {
                override val id = "test.echo"
                override val description = "Eco de prueba."
                override val requiredPermissions = emptySet<ToolPermission>()
                override fun invoke(call: ToolCall, context: ToolContext) =
                    ToolResponse(true, call.arguments["value"].orEmpty())
            })
        }

        val result = ToolCallingAgentEngine(model, broker).run(
            task = "Obtén 42",
            context = ToolContext(File("."), emptySet()),
            config = AgentRunConfig(maxSteps = 3, planningInterval = 4)
        )

        assertTrue(result.completed)
        assertEquals("Resultado verificado: 42", result.answer)
        assertTrue(result.memory.any {
            it.kind == AgentMemoryKind.OBSERVATION && it.text == "42"
        })
    }
}
