package com.kankwj.angcode.runtime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitWorktreeNamingTest {
    @Test
    fun acceptsAgentBranchesAndRejectsTraversalLikeRefs() {
        assertTrue(GitWorktreeNaming.validCellName("coder-01"))
        assertTrue(GitWorktreeNaming.validBranch("angcode/coder-01"))

        assertFalse(GitWorktreeNaming.validCellName("../coder"))
        assertFalse(GitWorktreeNaming.validBranch("angcode/../main"))
        assertFalse(GitWorktreeNaming.validBranch("bad//branch"))
        assertFalse(GitWorktreeNaming.validBranch("refs/@{bad"))
    }
}
