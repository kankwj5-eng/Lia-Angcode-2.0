package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.ToolPermission
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPermissionProfilesTest {
    @Test
    fun elevatedPermissionsStillRequireParentGrant() {
        val base = setOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.PROCESS_EXECUTE
        )

        val testerDenied = AgentPermissionProfiles.constrainedTo(
            AgentRole.TESTER,
            base
        )
        assertFalse(ToolPermission.SHIZUKU_PRIVILEGED in testerDenied)

        val testerAllowed = AgentPermissionProfiles.constrainedTo(
            AgentRole.TESTER,
            base + ToolPermission.SHIZUKU_PRIVILEGED
        )
        assertTrue(ToolPermission.SHIZUKU_PRIVILEGED in testerAllowed)

        val researcherAllowed = AgentPermissionProfiles.constrainedTo(
            AgentRole.RESEARCHER,
            setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.NETWORK,
                ToolPermission.GITHUB_READ
            )
        )
        assertTrue(ToolPermission.GITHUB_READ in researcherAllowed)
    }
}
