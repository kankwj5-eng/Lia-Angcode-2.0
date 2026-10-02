package com.kankwj.angcode.connectors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubNamesTest {
    @Test
    fun validatesOwnersAndRepos() {
        assertTrue(GitHubNames.validOwner("kankwj5-eng"))
        assertTrue(GitHubNames.validRepo("Lia-Angcode-2.0"))
        assertFalse(GitHubNames.validOwner("-bad"))
        assertFalse(GitHubNames.validOwner("bad-"))
        assertFalse(GitHubNames.validRepo("../secret"))
    }
    @Test
    fun validatesWriteRefsAndPaths() {
        assertTrue(GitHubRefs.validBranch("angcode/feature-1"))
        assertFalse(GitHubRefs.validBranch("bad//branch"))
        assertFalse(GitHubRefs.validBranch("a/../main"))

        assertTrue(GitHubPaths.validRepositoryPath("src/main/App.kt"))
        assertFalse(GitHubPaths.validRepositoryPath("../secret"))
        assertFalse(GitHubPaths.validRepositoryPath("/root/file"))
    }
}
