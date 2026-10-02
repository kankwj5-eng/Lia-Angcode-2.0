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
    @Test
    fun githubWriteAndRootRequireParentApproval() {
        val coderBase = setOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.WORKSPACE_WRITE,
            ToolPermission.PROCESS_EXECUTE,
            ToolPermission.GITHUB_READ
        )

        assertFalse(
            ToolPermission.GITHUB_WRITE in
                AgentPermissionProfiles.constrainedTo(AgentRole.CODER, coderBase)
        )
        assertTrue(
            ToolPermission.GITHUB_WRITE in
                AgentPermissionProfiles.constrainedTo(
                    AgentRole.CODER,
                    coderBase + ToolPermission.GITHUB_WRITE
                )
        )

        val builderBase = setOf(
            ToolPermission.WORKSPACE_READ,
            ToolPermission.WORKSPACE_WRITE,
            ToolPermission.PROCESS_EXECUTE
        )
        assertFalse(
            ToolPermission.ROOT_PRIVILEGED in
                AgentPermissionProfiles.constrainedTo(AgentRole.BUILDER, builderBase)
        )
        assertTrue(
            ToolPermission.ROOT_PRIVILEGED in
                AgentPermissionProfiles.constrainedTo(
                    AgentRole.BUILDER,
                    builderBase + ToolPermission.ROOT_PRIVILEGED
                )
        )
    }
}
