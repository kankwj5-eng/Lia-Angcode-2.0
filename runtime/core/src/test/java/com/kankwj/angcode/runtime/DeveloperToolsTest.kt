package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DeveloperToolsTest {
    @Test
    fun jsonQueryReadsNestedValue() {
        val root = createTempDirectory("angcode-json-").toFile()
        File(root, "data.json").writeText("""{"user":{"items":[{"name":"Ada"}]}}""")

        val response = JsonQueryTool().invoke(
            ToolCall(
                "json.query",
                mapOf("path" to "data.json", "query" to "user.items.0.name")
            ),
            ToolContext(root, setOf(ToolPermission.WORKSPACE_READ))
        )

        assertTrue(response.ok)
        assertEquals("Ada", response.output)
    }

    @Test
    fun pythonToolFailsCleanlyWhenBackendMissing() {
        val root = createTempDirectory("angcode-python-").toFile()
        val response = PythonRunTool().invoke(
            ToolCall("python.run", mapOf("code" to "print(1)")),
            ToolContext(
                root,
                setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)
            )
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("python"))
    }
}
