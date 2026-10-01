package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class CodeSymbolsToolTest {
    @Test
    fun extractsKotlinClassAndFunction() {
        val root = createTempDirectory("angcode-symbols-").toFile()
        File(root, "Demo.kt").writeText(
            """
            class Demo
            fun buildThing() = 1
            """.trimIndent()
        )

        val result = CodeSymbolsTool().invoke(
            ToolCall("code.symbols"),
            ToolContext(root, setOf(ToolPermission.WORKSPACE_READ))
        )

        assertTrue(result.ok)
        assertTrue(result.output.contains("class\tDemo"))
        assertTrue(result.output.contains("fun\tbuildThing"))
    }
}
