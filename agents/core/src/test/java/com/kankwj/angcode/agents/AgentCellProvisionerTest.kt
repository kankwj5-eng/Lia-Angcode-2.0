package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse
import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentCellProvisionerTest {
    @Test
    fun createsChildContextWithRoleScopedPermissions() {
        val root = createTempDirectory("angcode-cell-parent-").toFile()
        val child = File(root.parentFile, "fake-child").apply { mkdirs() }

        val broker = ToolBroker().apply {
            register(object : AgentTool {
                override val id = "git.worktree.create"
                override val description = "fake"
                override val requiredPermissions = setOf(ToolPermission.WORKTREE_MANAGE)
                override fun invoke(call: ToolCall, context: ToolContext) =
                    ToolResponse(
                        true,
                        "ok",
                        mapOf(
                            "worktree" to child.absolutePath,
                            "branch" to "angcode/coder-01"
                        )
                    )
            })
        }

        val parent = ToolContext(
            workspace = root,
            grantedPermissions = setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.WORKSPACE_WRITE,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.WORKTREE_MANAGE,
                ToolPermission.NETWORK
            )
        )

        val result = AgentCellProvisioner(broker).provision(
            CellProvisionRequest(
                role = AgentRole.CODER,
                task = "Editar runtime",
                name = "coder-01"
            ),
            parent
        )

        assertTrue(result.ok)
        val provisioned = result.provisioned
        assertNotNull(provisioned)
        assertEquals(child.absolutePath, provisioned!!.context.workspace.absolutePath)
        assertTrue(ToolPermission.WORKSPACE_WRITE in provisioned.context.grantedPermissions)
        assertTrue(ToolPermission.NETWORK !in provisioned.context.grantedPermissions)
    }
}
