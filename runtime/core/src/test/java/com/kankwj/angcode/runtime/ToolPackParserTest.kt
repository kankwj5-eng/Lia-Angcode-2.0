package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ToolPackParserTest {
    private val parser = ToolPackParser()

    @Test
    fun parsesProotPackAndEvaluatesArchitecture() {
        val manifest = parser.parse(
            """
            {
              "id":"web-node",
              "name":"Web Node",
              "version":"1",
              "description":"x",
              "packages":["nodejs","git"],
              "tools":["code.search","git.*"],
              "permissions":["WORKSPACE_READ","PROCESS_EXECUTE"],
              "runtime":"PROOT",
              "architectures":["aarch64"]
            }
            """.trimIndent()
        )

        val evaluation = ToolPackEvaluator().evaluate(
            manifest = manifest,
            architecture = "arm64-v8a",
            availablePackages = setOf("nodejs"),
            registeredTools = setOf("code.search", "git.status"),
            prootReady = true
        )

        assertTrue(evaluation.architectureCompatible)
        assertTrue(evaluation.runtimeAvailable)
        assertFalse(evaluation.ready)
        assertTrue("git" in evaluation.missingPackages)
        assertTrue(evaluation.missingTools.isEmpty())
    }

    @Test(expected = IllegalStateException::class)
    fun rejectsUnknownPermission() {
        parser.parse(
            """
            {
              "id":"bad",
              "name":"Bad",
              "version":"1",
              "packages":[],
              "tools":[],
              "permissions":["DOES_NOT_EXIST"]
            }
            """.trimIndent()
        )
    }
}
