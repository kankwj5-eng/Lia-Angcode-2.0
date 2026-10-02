package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolContext
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentCancellationTest {
    @Test
    fun engineStopsBeforeCallingModelWhenAlreadyCancelled() {
        var modelCalled = false
        val model = ModelGateway {
            modelCalled = true
            ModelResponse(text = "should-not-run")
        }
        val token = AgentCancellationToken().apply { cancel() }
        val root = createTempDirectory("angcode-cancel-").toFile()

        val result = ToolCallingAgentEngine(model, ToolBroker()).run(
            task = "cancelled",
            context = ToolContext(root, emptySet()),
            cancellation = token
        )

        assertTrue(result.cancelled)
        assertFalse(result.completed)
        assertFalse(modelCalled)
    }
}
