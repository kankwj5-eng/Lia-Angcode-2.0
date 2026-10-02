package com.kankwj.angcode.connectors.root

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootToolsTest {
    @Test
    fun rootToolsRequireExplicitPermissionBeforeTouchingShell() {
        val root = createTempDirectory("angcode-root-test-").toFile()
        val broker = ToolBroker().apply { register(RootLogcatTool()) }

        val response = broker.execute(
            ToolCall("android.root.logcat", mapOf("lines" to "100")),
            ToolContext(root, emptySet())
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("ROOT_PRIVILEGED"))
    }

    @Test
    fun rootToolDoesNotPromptWhenNoCachedShellExists() {
        val root = createTempDirectory("angcode-root-cache-").toFile()
        val response = RootLogcatTool().invoke(
            ToolCall("android.root.logcat"),
            ToolContext(root, setOf(ToolPermission.ROOT_PRIVILEGED))
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("Ajustes") || response.output.contains("root"))
    }
}
