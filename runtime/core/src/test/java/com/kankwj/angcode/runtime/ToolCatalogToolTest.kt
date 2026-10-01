package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolCatalogToolTest {
    @Test
    fun canFilterCatalogByFamily() {
        val root = createTempDirectory("angcode-catalog-").toFile()
        val response = ToolCatalogTool().invoke(
            ToolCall("tool.catalog", mapOf("family" to "FILES")),
            ToolContext(root, emptySet())
        )

        assertTrue(response.ok)
        assertTrue(response.output.contains("file.read"))
        assertTrue(!response.output.contains("android.battery"))
    }
}
