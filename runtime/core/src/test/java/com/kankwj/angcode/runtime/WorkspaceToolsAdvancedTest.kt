package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.io.path.createTempDirectory

class WorkspaceToolsAdvancedTest {
    private fun context(root: File) = ToolContext(
        workspace = root,
        grantedPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)
    )

    @Test
    fun searchFindsTextInsideWorkspace() {
        val root = createTempDirectory("angcode-search-").toFile()
        File(root, "src").mkdirs()
        File(root, "src/Main.kt").writeText("fun main() { println(\"needle\") }")

        val response = WorkspaceSearchTool().invoke(
            ToolCall("workspace.search", mapOf("query" to "needle")),
            context(root)
        )

        assertTrue(response.ok)
        assertTrue(response.output.contains("src/Main.kt:1:"))
    }

    @Test
    fun patchRejectsAmbiguousReplacement() {
        val root = createTempDirectory("angcode-patch-").toFile()
        File(root, "a.txt").writeText("x x")

        val response = FilePatchTool().invoke(
            ToolCall("file.patch", mapOf("path" to "a.txt", "old" to "x", "new" to "y")),
            context(root)
        )

        assertFalse(response.ok)
    }
}
