package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitMergePermissionTest {
    @Test
    fun mergeRequiresAdministrativeWorktreePermission() {
        val root = createTempDirectory("angcode-merge-").toFile()
        val broker = ToolBroker().apply { register(GitMergeTool()) }

        val response = broker.execute(
            ToolCall("git.merge", mapOf("branch" to "angcode/coder-01")),
            ToolContext(
                workspace = root,
                grantedPermissions = setOf(
                    ToolPermission.WORKSPACE_READ,
                    ToolPermission.WORKSPACE_WRITE
                )
            )
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("WORKTREE_MANAGE"))
    }
}
