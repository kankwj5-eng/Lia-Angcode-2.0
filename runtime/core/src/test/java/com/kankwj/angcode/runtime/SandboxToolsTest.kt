package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SandboxToolsTest {
    @Test
    fun sandboxInstallNeedsDedicatedManagePermission() {
        val root = createTempDirectory("angcode-sandbox-").toFile()
        val broker = ToolBroker().apply { register(SandboxInstallTool()) }

        val response = broker.execute(
            ToolCall("sandbox.install", mapOf("image" to "debian:bookworm")),
            ToolContext(
                root,
                setOf(ToolPermission.PROCESS_EXECUTE, ToolPermission.NETWORK)
            )
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("SANDBOX_MANAGE"))
    }

    @Test
    fun sandboxExecRejectsShellSnippetAsExecutable() {
        val root = createTempDirectory("angcode-sandbox-exec-").toFile()
        val response = SandboxExecTool().invoke(
            ToolCall(
                "sandbox.exec",
                mapOf(
                    "name" to "debian",
                    "executable" to "sh -c echo unsafe"
                )
            ),
            ToolContext(
                root,
                setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)
            )
        )

        assertFalse(response.ok)
    }
}
