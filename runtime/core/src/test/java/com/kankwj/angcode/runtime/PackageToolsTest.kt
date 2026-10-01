package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PackageToolsTest {
    @Test
    fun packageInstallRequiresBackend() {
        val root = createTempDirectory("angcode-packages-").toFile()
        val response = PackageInstallTool().invoke(
            ToolCall("package.install", mapOf("packages" to "git")),
            ToolContext(
                workspace = root,
                grantedPermissions = setOf(
                    ToolPermission.PROCESS_EXECUTE,
                    ToolPermission.NETWORK,
                    ToolPermission.PACKAGE_MANAGE
                )
            )
        )
        assertFalse(response.ok)
        assertTrue(response.output.contains("no está disponible"))
    }

    @Test
    fun brokerBlocksPackageManagementWithoutDedicatedPermission() {
        val root = createTempDirectory("angcode-packages-perm-").toFile()
        val broker = ToolBroker().apply { register(PackageInstallTool()) }
        val response = broker.execute(
            ToolCall("package.install", mapOf("packages" to "git")),
            ToolContext(
                workspace = root,
                grantedPermissions = setOf(ToolPermission.PROCESS_EXECUTE, ToolPermission.NETWORK)
            )
        )
        assertFalse(response.ok)
        assertTrue(response.output.contains("PACKAGE_MANAGE"))
    }
}
