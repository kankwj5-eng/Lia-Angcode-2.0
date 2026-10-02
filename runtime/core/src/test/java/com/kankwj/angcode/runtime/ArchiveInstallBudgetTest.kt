package com.kankwj.angcode.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveInstallBudgetTest {
    @Test
    fun acceptsNormalBudgetAndRejectsExpansionBomb() {
        val budget = ArchiveInstallBudget(
            maxArchiveBytes = 100,
            maxExtractedBytes = 200,
            maxEntries = 3,
            maxSymlinks = 2
        )
        budget.onArchiveBytes(80)
        budget.onEntry()
        budget.onExtractedBytes(150)
        budget.onSymlink()

        assertTrue(budget.archiveBytes == 80L)
        assertTrue(budget.extractedBytes == 150L)
        assertTrue(runCatching { budget.onExtractedBytes(51) }.isFailure)
    }

    @Test
    fun rejectsEntryAndSymlinkFloods() {
        val budget = ArchiveInstallBudget(
            maxArchiveBytes = 100,
            maxExtractedBytes = 100,
            maxEntries = 1,
            maxSymlinks = 1
        )
        budget.onEntry()
        assertTrue(runCatching { budget.onEntry() }.isFailure)

        budget.onSymlink()
        assertTrue(runCatching { budget.onSymlink() }.isFailure)
    }
}
