package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolAuditLogTest {
    @Test
    fun redactsSensitiveToolArguments() {
        val root = createTempDirectory("angcode-audit-").toFile()
        val broker = ToolBroker().apply {
            register(object : AgentTool {
                override val id = "fake"
                override val description = "fake"
                override val requiredPermissions = emptySet<ToolPermission>()
                override fun invoke(call: ToolCall, context: ToolContext) =
                    ToolResponse(true, "ok")
            })
        }

        broker.execute(
            ToolCall(
                "fake",
                mapOf(
                    "host" to "example.com",
                    "token" to "super-secret",
                    "content" to "private-file-body"
                )
            ),
            ToolContext(root, emptySet())
        )

        val audit = ToolAuditLog.file(root).readText()
        assertTrue(audit.contains("example.com"))
        assertTrue(audit.contains("<redacted>"))
        assertFalse(audit.contains("super-secret"))
        assertFalse(audit.contains("private-file-body"))
    }
}
