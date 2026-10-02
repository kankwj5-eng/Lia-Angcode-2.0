package com.kankwj.angcode.runtime

import android.content.Context
import java.io.File

data class DiscoveredExecutable(
    val id: String,
    val path: String,
    val sourceRoot: String
)

class ExecutableDiscovery(
    private val roots: List<File>
) {
    fun discover(): List<DiscoveredExecutable> {
        val result = linkedMapOf<String, DiscoveredExecutable>()

        for ((id, candidates) in DEFAULT_EXECUTABLES) {
            for (root in roots) {
                val match = candidates
                    .asSequence()
                    .map { File(root, it) }
                    .firstOrNull { it.isFile && it.canExecute() }
                    ?: continue

                result[id] = DiscoveredExecutable(
                    id = id,
                    path = match.absolutePath,
                    sourceRoot = root.absolutePath
                )
                break
            }
        }

        return result.values.toList()
    }

    fun asMap(): Map<String, String> =
        discover().associate { it.id to it.path }

    companion object {
        val DEFAULT_EXECUTABLES = linkedMapOf(
            "git" to listOf("git"),
            "python" to listOf("python3", "python"),
            "pip" to listOf("pip3", "pip"),
            "node" to listOf("node"),
            "npm" to listOf("npm"),
            "pnpm" to listOf("pnpm"),
            "java" to listOf("java"),
            "javac" to listOf("javac"),
            "gradle" to listOf("gradle"),
            "ecj" to listOf("ecj"),
            "clang" to listOf("clang"),
            "aapt" to listOf("aapt"),
            "aapt2" to listOf("aapt2"),
            "d8" to listOf("d8"),
            "r8" to listOf("r8"),
            "apksigner" to listOf("apksigner"),
            "adb" to listOf("adb"),
            "fastboot" to listOf("fastboot"),
            "zipalign" to listOf("zipalign"),
            "cmake" to listOf("cmake"),
            "ninja" to listOf("ninja"),
            "rg" to listOf("rg", "ripgrep"),
            "jq" to listOf("jq"),
            "sqlite3" to listOf("sqlite3"),
            "ffmpeg" to listOf("ffmpeg"),
            "ffprobe" to listOf("ffprobe"),
            "magick" to listOf("magick", "convert"),
            "ssh" to listOf("ssh"),
            "scp" to listOf("scp"),
            "ssh-keyscan" to listOf("ssh-keyscan"),
            "ssh-keygen" to listOf("ssh-keygen"),
            "curl" to listOf("curl"),
            "apt" to listOf("apt"),
            "apt-get" to listOf("apt-get"),
            "dpkg-query" to listOf("dpkg-query"),
            "proot" to listOf("proot"),
            "proot-distro" to listOf("proot-distro", "pd"),
            "bash" to listOf("bash"),
            "make" to listOf("make"),
            "tar" to listOf("tar"),
            "unzip" to listOf("unzip"),
            "llama-cli" to listOf("llama-cli"),
            "llama-server" to listOf("llama-server"),
            "tree-sitter" to listOf("tree-sitter")
        )

        fun forApp(context: Context): ExecutableDiscovery {
            val files = context.applicationContext.filesDir
            val roots = listOf(
                File(files, "runtime/usr/bin"),
                File(files, "usr/bin"),
                File(files, "bin"),
                File(files, "toolpacks/bin")
            )
            return ExecutableDiscovery(roots)
        }
    }
}

class RuntimeExecutablesTool : AgentTool {
    override val id = "runtime.executables"
    override val description = "Lista ejecutables descubiertos y registrados para la sesión."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val entries = context.executables.entries.sortedBy { it.key }
        return ToolResponse(
            ok = true,
            output = entries.joinToString("\n") { "${it.key}\t${it.value}" },
            metadata = mapOf("count" to entries.size.toString())
        )
    }
}
