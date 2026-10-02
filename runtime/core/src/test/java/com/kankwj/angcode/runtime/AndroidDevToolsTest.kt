package com.kankwj.angcode.runtime

import kotlin.io.path.createTempDirectory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidDevToolsTest {
    @Test
    fun adbConnectRequiresDedicatedPermission() {
        val root = createTempDirectory("angcode-adb-").toFile()
        val broker = ToolBroker().apply { register(AndroidAdbConnectTool()) }
        val response = broker.execute(
            ToolCall("android.adb.connect", mapOf("host" to "127.0.0.1", "port" to "5555")),
            ToolContext(
                root,
                setOf(
                    ToolPermission.PROCESS_EXECUTE,
                    ToolPermission.NETWORK,
                    ToolPermission.PRIVATE_NETWORK
                )
            )
        )
        assertFalse(response.ok)
        assertTrue(response.output.contains("ADB_REMOTE"))
    }
}
