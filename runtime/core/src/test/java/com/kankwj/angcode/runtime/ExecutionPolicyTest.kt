package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.file.Files

class ExecutionPolicyTest {
    private val workspace = Files.createTempDirectory("angcode-policy-").toFile()

    @Test
    fun shellCommandStringRequiresExplicitPermission() {
        val policy = ExecutionPolicy.androidBase()
        val context = ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(ToolPermission.PROCESS_EXECUTE)
        )

        val decision = policy.check("/system/bin/sh", context, listOf("-c", "id"))
        assertFalse(decision.allowed)
    }

    @Test
    fun userApprovedShellCanEvaluateCommandString() {
        val policy = ExecutionPolicy.androidBase()
        val context = ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.UNRESTRICTED_SHELL
            )
        )

        val decision = policy.check("/system/bin/sh", context, listOf("-c", "id"))
        assertTrue(decision.allowed)
    }
}
