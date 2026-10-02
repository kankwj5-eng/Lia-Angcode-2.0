package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ToolAuditLogTest {
    @Test
    fun redactsSensitiveArguments() {
        val root = createTempDirectory("angcode-audit-").toFile()
        ToolAuditLog.record(
            workspace = root,
            call = ToolCall(
                "fake",
                mapOf(
                    "token" to "super-secret-token",
                    "header" to "Authorization: Bearer abc",
                    "path" to "src/Main.kt"
                )
            ),
            requiredPermissions = emptySet(),
            grantedPermissions = emptySet(),
            response = ToolResponse(true, "secret output not logged"),
            durationMs = 4
        )

        val line = ToolAuditLog.file(root).readText()
        assertFalse(line.contains("super-secret-token"))
        assertFalse(line.contains("Bearer abc"))
        assertFalse(line.contains("secret output not logged"))
        assertTrue(line.contains("<redacted>"))
        assertTrue(line.contains("src/Main.kt"))
    }
}
