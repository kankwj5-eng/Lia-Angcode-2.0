package com.kankwj.angcode.runtime

import org.junit.Assert.assertTrue
import org.junit.Test

class CommandRunnerBoundsTest {
    @Test
    fun truncatesCapturedOutputWhileDrainingProcess() {
        val result = CommandRunner().run(
            CommandRequest(
                executable = "/bin/sh",
                arguments = listOf(
                    "-c",
                    "python3 - <<'PY'\nprint('x' * 50000)\nPY"
                ),
                timeoutMillis = 10_000,
                maxCapturedCharsPerStream = 4_096
            )
        )
        assertTrue(result.succeeded)
        assertTrue(result.stdout.length <= 4_096)
        assertTrue(result.stdoutTruncated)
    }
}
