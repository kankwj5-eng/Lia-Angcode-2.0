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
}
