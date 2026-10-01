package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolBrokerTest {
    @Test
    fun brokerRejectsMissingPermissions() {
        val broker = ToolBroker()
        broker.register(object : AgentTool {
            override val id = "dangerous"
            override val description = "test"
            override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)
            override fun invoke(call: ToolCall, context: ToolContext) = ToolResponse(true, "ok")
        })

        val response = broker.execute(
            ToolCall("dangerous"),
            ToolContext(File("."), emptySet())
        )

        assertFalse(response.ok)
    }

    @Test
    fun brokerRunsAuthorizedTool() {
        val broker = ToolBroker()
        broker.register(object : AgentTool {
            override val id = "safe"
            override val description = "test"
            override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)
            override fun invoke(call: ToolCall, context: ToolContext) = ToolResponse(true, "ok")
        })

        val response = broker.execute(
            ToolCall("safe"),
            ToolContext(File("."), setOf(ToolPermission.WORKSPACE_READ))
        )

        assertTrue(response.ok)
    }
}
