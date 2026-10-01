package com.kankwj.angcode.runtime

import android.os.Build

data class RuntimeHealth(
    val ready: Boolean,
    val architecture: String,
    val shellPath: String,
    val androidVersion: String,
    val detail: String
)

class RuntimeProbe(private val runner: CommandRunner = CommandRunner()) {
    fun inspect(): RuntimeHealth {
        val shell = "/system/bin/sh"
        val result = runCatching {
            runner.run(CommandRequest(shell, listOf("-c", "uname -m"), timeoutMillis = 3_000))
        }.getOrNull()

        return RuntimeHealth(
            ready = result?.succeeded == true,
            architecture = result?.stdout?.trim().takeUnless { it.isNullOrBlank() }
                ?: Build.SUPPORTED_ABIS.firstOrNull().orEmpty(),
            shellPath = shell,
            androidVersion = "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})",
            detail = if (result?.succeeded == true) "Shell Android disponible" else "Bridge de procesos pendiente"
        )
    }
}
