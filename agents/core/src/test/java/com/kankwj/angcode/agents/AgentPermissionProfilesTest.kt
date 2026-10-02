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
}
