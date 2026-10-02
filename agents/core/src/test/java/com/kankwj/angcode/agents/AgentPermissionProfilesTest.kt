package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolPermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPermissionProfilesTest {
    @Test
    fun workerRolesDoNotReceivePrivilegedBackendsByDefault() {
        val all = ToolPermission.entries.toSet()
        val privileged = setOf(
            ToolPermission.ROOT_PRIVILEGED,
            ToolPermission.SHIZUKU_PRIVILEGED,
            ToolPermission.ADB_REMOTE
        )

        listOf(
            AgentRole.CODER,
            AgentRole.RESEARCHER,
            AgentRole.TESTER,
            AgentRole.BUILDER,
            AgentRole.BROWSER
        ).forEach { role ->
            val permissions = AgentPermissionProfiles.constrainedTo(role, all)
            assertTrue(
                "$role heredó backend privilegiado: ${permissions.intersect(privileged)}",
                permissions.intersect(privileged).isEmpty()
            )
        }
    }

    @Test
    fun testerAndBuilderKeepNormalAndroidBridge() {
        val all = ToolPermission.entries.toSet()
        assertTrue(ToolPermission.ANDROID_BRIDGE in AgentPermissionProfiles.constrainedTo(AgentRole.TESTER, all))
        assertTrue(ToolPermission.ANDROID_BRIDGE in AgentPermissionProfiles.constrainedTo(AgentRole.BUILDER, all))
        assertFalse(ToolPermission.ROOT_PRIVILEGED in AgentPermissionProfiles.constrainedTo(AgentRole.BUILDER, all))
    }
    @Test
    fun explicitlyApprovedElevatedPermissionCanReachBuilder() {
        val parent = setOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.WORKSPACE_WRITE,
            ToolPermission.PROCESS_EXECUTE,
            ToolPermission.ANDROID_BRIDGE,
            ToolPermission.SHIZUKU_PRIVILEGED,
            ToolPermission.ROOT_PRIVILEGED,
            ToolPermission.ADB_REMOTE,
            ToolPermission.PRIVATE_NETWORK
        )
        val approved = setOf(
            ToolPermission.SHIZUKU_PRIVILEGED,
            ToolPermission.ADB_REMOTE,
            ToolPermission.PRIVATE_NETWORK
        )

        val permissions = AgentPermissionProfiles.constrainedTo(
            AgentRole.BUILDER,
            parent,
            approved
        )

        assertTrue(ToolPermission.SHIZUKU_PRIVILEGED in permissions)
        assertTrue(ToolPermission.ADB_REMOTE in permissions)
        assertTrue(ToolPermission.PRIVATE_NETWORK in permissions)
        assertFalse(ToolPermission.ROOT_PRIVILEGED in permissions)
    }
}
