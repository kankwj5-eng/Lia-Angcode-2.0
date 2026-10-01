package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class CoreToolsTest {
    private fun workspace() = Files.createTempDirectory("angcode-core-tools-").toFile()

    @Test
    fun patchAndSearchStayInsideWorkspace() {
        val root = workspace()
        val file = root.resolve("src/test.txt").apply {
            parentFile.mkdirs()
            writeText("alpha beta gamma")
        }
        val broker = ToolBroker().registerCoreTools()
        val context = ToolContext(
            workspace = root,
            grantedPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)
        )

        val patch = broker.execute(
            ToolCall("file.patch", mapOf("path" to "src/test.txt", "old" to "beta", "new" to "delta")),
            context
        )
        assertTrue(patch.ok)
        assertTrue(file.readText().contains("delta"))

        val search = broker.execute(ToolCall("code.search", mapOf("query" to "delta")), context)
        assertTrue(search.ok)
        assertTrue(search.output.contains("src"))
    }

    @Test
    fun traversalIsRejected() {
        val root = workspace()
        val broker = ToolBroker().registerCoreTools()
        val context = ToolContext(root, setOf(ToolPermission.WORKSPACE_READ))
        val response = broker.execute(ToolCall("file.read", mapOf("path" to "../outside.txt")), context)
        assertFalse(response.ok)
    }

    @Test
    fun privateNetworkRequiresSeparatePermission() {
        val root = workspace()
        val broker = ToolBroker().registerCoreTools()
        val context = ToolContext(root, setOf(ToolPermission.NETWORK))
        val response = broker.execute(
            ToolCall("http.get", mapOf("url" to "http://127.0.0.1/")),
            context
        )
        assertFalse(response.ok)
    }

    @Test
    fun zipCreatesArtifact() {
        val root = workspace()
        root.resolve("hello.txt").writeText("hola")
        val broker = ToolBroker().registerCoreTools()
        val context = ToolContext(
            root,
            setOf(ToolPermission.WORKSPACE_READ, ToolPermission.WORKSPACE_WRITE)
        )
        val response = broker.execute(
            ToolCall("artifact.zip", mapOf("path" to "hello.txt", "name" to "salida")),
            context
        )
        assertTrue(response.ok)
        assertTrue(root.resolve("artifacts/salida.zip").isFile)
    }
}
