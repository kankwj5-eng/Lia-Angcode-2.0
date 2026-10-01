package com.kankwj.angcode.runtime

import java.io.File

data class SandboxCommand(
    val executable: String,
    val arguments: List<String>,
    val workingDirectory: File
)

interface SandboxBackend {
    val id: String
    val displayName: String
    fun isReady(): Boolean
    fun wrap(command: SandboxCommand): CommandRequest
}

class NativeAndroidBackend : SandboxBackend {
    override val id = "android-native"
    override val displayName = "Android nativo"
    override fun isReady(): Boolean = File("/system/bin/sh").canExecute()

    override fun wrap(command: SandboxCommand): CommandRequest {
        return CommandRequest(
            executable = command.executable,
            arguments = command.arguments,
            workingDirectory = command.workingDirectory
        )
    }
}

data class ProotMount(
    val host: File,
    val guest: String
)

class ProotSandboxBackend(
    private val prootExecutable: File,
    private val rootfs: File,
    private val mounts: List<ProotMount> = emptyList()
) : SandboxBackend {
    override val id = "proot"
    override val displayName = "Linux PRoot"

    override fun isReady(): Boolean =
        prootExecutable.canExecute() && rootfs.isDirectory

    override fun wrap(command: SandboxCommand): CommandRequest {
        require(isReady()) { "Backend PRoot no está instalado" }

        val args = buildList {
            add("-0")
            add("-r")
            add(rootfs.absolutePath)
            add("-b")
            add("/dev")
            add("-b")
            add("/proc")
            add("-b")
            add("${command.workingDirectory.absolutePath}:/workspace")
            mounts.forEach { mount ->
                add("-b")
                add("${mount.host.absolutePath}:${mount.guest}")
            }
            add("-w")
            add("/workspace")
            add(command.executable)
            addAll(command.arguments)
        }

        return CommandRequest(
            executable = prootExecutable.absolutePath,
            arguments = args,
            workingDirectory = command.workingDirectory
        )
    }
}
