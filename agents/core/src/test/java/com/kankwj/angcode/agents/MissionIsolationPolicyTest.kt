package com.kankwj.angcode.agents

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MissionIsolationPolicyTest {
    @Test
    fun isolatesOnlyCleanGitReposWithPermission() {
        assertTrue(
            MissionIsolationPolicy.canIsolate(
                gitAvailable = true,
                worktreePermission = true,
                gitStatusOutput = "## main"
            )
        )
        assertFalse(
            MissionIsolationPolicy.canIsolate(
                gitAvailable = true,
                worktreePermission = true,
                gitStatusOutput = "## main\n M app/Main.kt"
            )
        )
        assertFalse(
            MissionIsolationPolicy.canIsolate(
                gitAvailable = false,
                worktreePermission = true,
                gitStatusOutput = "## main"
            )
        )
    }

    @Test
    fun onlyMutableRolesUseMissionWorktree() {
        assertTrue(MissionIsolationPolicy.mutableRole(AgentRole.CODER))
        assertTrue(MissionIsolationPolicy.mutableRole(AgentRole.TESTER))
        assertTrue(MissionIsolationPolicy.mutableRole(AgentRole.BUILDER))
        assertFalse(MissionIsolationPolicy.mutableRole(AgentRole.RESEARCHER))
        assertFalse(MissionIsolationPolicy.mutableRole(AgentRole.BROWSER))
    }
}
