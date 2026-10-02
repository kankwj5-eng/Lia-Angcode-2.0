package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshToolsTest {
    @Test
    fun sshRequiresDedicatedPermission() {
        val root = createTempDirectory("angcode-ssh-").toFile()
        val broker = ToolBroker().apply { register(SshExecTool()) }

        val response = broker.execute(
            ToolCall(
                "ssh.exec",
                mapOf(
                    "host" to "example.com",
                    "user" to "build",
                    "executable" to "/usr/bin/true"
                )
            ),
            ToolContext(
                root,
                setOf(ToolPermission.PROCESS_EXECUTE, ToolPermission.NETWORK)
            )
        )

        assertFalse(response.ok)
        assertTrue(response.output.contains("SSH_REMOTE"))
    }

    @Test
    fun sshRefusesMissingKnownHostsBeforeConnecting() {
        val root = createTempDirectory("angcode-ssh-known-").toFile()
        val response = SshExecTool().invoke(
            ToolCall(
                "ssh.exec",
                mapOf(
                    "host" to "example.com",
                    "user" to "build",
                    "executable" to "/usr/bin/true"
                )
            ),
            ToolContext(
                root,
                setOf(
                    ToolPermission.PROCESS_EXECUTE,
                    ToolPermission.NETWORK,
                    ToolPermission.SSH_REMOTE
                ),
                executables = mapOf("ssh" to "/system/bin/false")
            )
        )
        assertFalse(response.ok)
        assertTrue(response.output.contains("known_hosts"))
    }
}
