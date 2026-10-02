package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import java.io.File
import java.util.UUID

data class CellProvisionRequest(
    val role: AgentRole,
    val task: String,
    val name: String = defaultCellName(role),
    val baseRef: String = "HEAD",
    val sandboxImage: String? = null
) {
    companion object {
        private fun defaultCellName(role: AgentRole): String =
            role.name.lowercase() + "-" + UUID.randomUUID().toString().take(8)
    }
}

data class ProvisionedCell(
    val cell: AgentCell,
    val context: ToolContext
)

data class CellProvisionResult(
    val ok: Boolean,
    val provisioned: ProvisionedCell? = null,
    val detail: String
)

object AgentPermissionProfiles {
    fun forRole(role: AgentRole): Set<ToolPermission> =
        when (role) {
            AgentRole.DIRECTOR -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.NETWORK,
                ToolPermission.WORKTREE_MANAGE,
                ToolPermission.SANDBOX_MANAGE
            )
            AgentRole.CODER -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.WORKSPACE_WRITE,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.NETWORK,
                ToolPermission.GITHUB_READ
            )
            AgentRole.RESEARCHER -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.ARTIFACT_WRITE,
                ToolPermission.NETWORK,
                ToolPermission.MCP_EXTERNAL,
                ToolPermission.GITHUB_READ
            )
            AgentRole.TESTER -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.ARTIFACT_WRITE,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.ANDROID_BRIDGE
            )
            AgentRole.BUILDER -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.ARTIFACT_WRITE,
                ToolPermission.WORKSPACE_WRITE,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.ANDROID_BRIDGE
            )
            AgentRole.BROWSER -> setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.ARTIFACT_WRITE,
                ToolPermission.NETWORK,
                ToolPermission.PRIVATE_NETWORK,
                ToolPermission.MCP_EXTERNAL
            )
        }

    fun elevatedForRole(role: AgentRole): Set<ToolPermission> =
        when (role) {
            AgentRole.DIRECTOR -> emptySet()
            AgentRole.CODER -> setOf(
                ToolPermission.GITHUB_WRITE,
                ToolPermission.SSH_REMOTE
            )
            AgentRole.RESEARCHER -> setOf(
                ToolPermission.SSH_REMOTE
            )
            AgentRole.TESTER -> setOf(
                ToolPermission.SHIZUKU_PRIVILEGED,
                ToolPermission.ROOT_PRIVILEGED,
                ToolPermission.ADB_REMOTE,
                ToolPermission.PRIVATE_NETWORK,
                ToolPermission.SSH_REMOTE,
                ToolPermission.CLIPBOARD_READ
            )
            AgentRole.BUILDER -> setOf(
                ToolPermission.SHIZUKU_PRIVILEGED,
                ToolPermission.ROOT_PRIVILEGED,
                ToolPermission.ADB_REMOTE,
                ToolPermission.PRIVATE_NETWORK,
                ToolPermission.SSH_REMOTE,
                ToolPermission.GITHUB_WRITE
            )
            AgentRole.BROWSER -> setOf(
                ToolPermission.ANDROID_UI_ACTION,
                ToolPermission.CLIPBOARD_READ,
                ToolPermission.CLIPBOARD_WRITE
            )
        }

    fun constrainedTo(
        role: AgentRole,
        parentPermissions: Set<ToolPermission>,
        approvedElevatedPermissions: Set<ToolPermission> = emptySet()
    ): Set<ToolPermission> {
        val base = forRole(role).intersect(parentPermissions)
        val elevated = elevatedForRole(role)
            .intersect(parentPermissions)
            .intersect(approvedElevatedPermissions)
        return base + elevated
    }
}

class AgentCellProvisioner(
    private val broker: ToolBroker,
    private val eventBus: EventBus = EventBus()
) {
    fun provision(
        request: CellProvisionRequest,
        parentContext: ToolContext
    ): CellProvisionResult {
        val create = broker.execute(
            ToolCall(
                "git.worktree.create",
                mapOf(
                    "name" to request.name,
                    "branch" to ("angcode/" + request.name),
                    "base" to request.baseRef
                )
            ),
            parentContext
        )

        if (!create.ok) {
            return CellProvisionResult(false, detail = create.output)
        }

        val worktreePath = create.metadata["worktree"]
            ?: return CellProvisionResult(false, detail = "git.worktree.create no devolvió ruta")
        val branch = create.metadata["branch"]
            ?: "angcode/" + request.name

        var sandboxName: String? = null
        if (request.sandboxImage != null) {
            val candidate = ("cell-" + request.name).take(63)
            val sandbox = broker.execute(
                ToolCall(
                    "sandbox.install",
                    mapOf(
                        "image" to request.sandboxImage,
                        "name" to candidate
                    )
                ),
                parentContext
            )
            if (!sandbox.ok) {
                broker.execute(
                    ToolCall(
                        "git.worktree.remove",
                        mapOf("name" to request.name, "force" to "true")
                    ),
                    parentContext
                )
                return CellProvisionResult(
                    false,
                    detail = "No se pudo crear sandbox: " + sandbox.output
                )
            }
            sandboxName = candidate
        }

        val childPermissions = AgentPermissionProfiles.constrainedTo(
            request.role,
            parentContext.grantedPermissions,
            parentContext.approvedElevatedPermissions
        )

        val childContext = ToolContext(
            workspace = File(worktreePath),
            grantedPermissions = childPermissions,
            executables = parentContext.executables,
            approvedElevatedPermissions = parentContext.approvedElevatedPermissions
        )

        val cell = AgentCell(
            role = request.role,
            status = AgentStatus.QUEUED,
            currentTask = request.task,
            workspacePath = worktreePath,
            branch = branch,
            sandbox = sandboxName
        )

        eventBus.publish(
            AgentEvent(
                type = "cell.provisioned",
                source = request.name,
                payload = mapOf(
                    "role" to request.role.name,
                    "workspace" to worktreePath,
                    "branch" to branch,
                    "sandbox" to (sandboxName ?: "")
                )
            )
        )

        return CellProvisionResult(
            ok = true,
            provisioned = ProvisionedCell(cell, childContext),
            detail = "Celda lista"
        )
    }

    fun release(
        cellName: String,
        cell: AgentCell,
        parentContext: ToolContext,
        forceWorktree: Boolean = false
    ): CellProvisionResult {
        cell.sandbox?.let { sandbox ->
            val removed = broker.execute(
                ToolCall("sandbox.remove", mapOf("name" to sandbox)),
                parentContext
            )
            if (!removed.ok) {
                return CellProvisionResult(false, detail = "No se pudo retirar sandbox: " + removed.output)
            }
        }

        val worktree = broker.execute(
            ToolCall(
                "git.worktree.remove",
                mapOf(
                    "name" to cellName,
                    "force" to forceWorktree.toString()
                )
            ),
            parentContext
        )

        if (!worktree.ok) {
            return CellProvisionResult(false, detail = worktree.output)
        }

        eventBus.publish(
            AgentEvent(
                type = "cell.released",
                source = cellName,
                payload = mapOf("branch" to (cell.branch ?: ""))
            )
        )

        return CellProvisionResult(true, detail = "Celda liberada")
    }
}
