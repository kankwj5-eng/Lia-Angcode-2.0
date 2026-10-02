package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.CommandRequest
import com.kankwj.angcode.runtime.CommandRunner
import java.io.File

class LlamaCliModelGateway(
    private val executable: File,
    private val model: File,
    private val runner: CommandRunner = CommandRunner(),
    private val temperature: Double = 0.2,
    private val contextSize: Int = 4_096,
    private val threads: Int = 4,
    private val batchSize: Int = 256,
    private val timeoutMillis: Long = 5 * 60_000L
) : ModelGateway {
    init {
        require(executable.isFile && executable.canExecute()) { "llama-cli no está disponible" }
        require(model.isFile) { "Modelo GGUF no encontrado" }
    }

    override fun complete(request: ModelRequest): ModelResponse {
        val result = runner.run(
            CommandRequest(
                executable = executable.absolutePath,
                arguments = listOf(
                    "-m", model.absolutePath,
                    "-p", ModelToolProtocol.prompt(request),
                    "-n", request.maxOutputTokens.coerceIn(32, 4096).toString(),
                    "--temp", temperature.coerceIn(0.0, 2.0).toString(),
                    "-c", contextSize.coerceIn(512, 32_768).toString(),
                    "-t", threads.coerceIn(1, 16).toString(),
                    "-b", batchSize.coerceIn(32, 2_048).toString()
                ),
                workingDirectory = model.parentFile,
                timeoutMillis = timeoutMillis,
                environment = runtimeEnvironment(executable)
            )
        )

        if (!result.succeeded) {
            return ModelResponse(
                text = "Error de inferencia local: " +
                    result.stderr.ifBlank { "exit=" + result.exitCode }
            )
        }

        return ModelToolProtocol.parse(result.stdout)
    }

    private fun runtimeEnvironment(executable: File): Map<String, String> {
        val bin = executable.parentFile ?: return emptyMap()
        val prefix = bin.parentFile ?: return emptyMap()
        val filesDir = prefix.parentFile ?: return emptyMap()

        return mapOf(
            "PREFIX" to prefix.absolutePath,
            "HOME" to File(filesDir, "home").apply { mkdirs() }.absolutePath,
            "TMPDIR" to File(prefix, "tmp").apply { mkdirs() }.absolutePath,
            "PATH" to (bin.absolutePath + ":" + System.getenv("PATH").orEmpty()),
            "LD_LIBRARY_PATH" to File(prefix, "lib").absolutePath,
            "LANG" to "C.UTF-8"
        )
    }
}
