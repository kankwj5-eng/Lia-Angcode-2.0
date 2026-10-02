package com.kankwj.angcode.runtime

import java.io.File
import java.util.concurrent.TimeUnit

data class CommandRequest(
    val executable: String,
    val arguments: List<String> = emptyList(),
    val workingDirectory: File? = null,
    val stdin: String? = null,
    val timeoutMillis: Long = 30_000,
    val environment: Map<String, String> = emptyMap()
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMillis: Long
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

class CommandRunner {
    fun run(request: CommandRequest): CommandResult {
        val started = System.nanoTime()
        val command = buildList {
            add(request.executable)
            addAll(request.arguments)
        }

        val process = ProcessBuilder(command)
            .apply {
                request.workingDirectory?.let { directory(it) }
                if (request.environment.isNotEmpty()) {
                    environment().putAll(request.environment)
                }
                redirectErrorStream(false)
            }
            .start()

        if (request.stdin != null) {
            process.outputStream.bufferedWriter().use { writer ->
                writer.write(request.stdin)
            }
        } else {
            process.outputStream.close()
        }

        val stdoutThread = StreamCollector(process.inputStream)
        val stderrThread = StreamCollector(process.errorStream)
        stdoutThread.start()
        stderrThread.start()

        val completed = try {
            process.waitFor(request.timeoutMillis, TimeUnit.MILLISECONDS)
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
            stdoutThread.interrupt()
            stderrThread.interrupt()
            Thread.currentThread().interrupt()
            throw interrupted
        }

        if (!completed) {
            process.destroyForcibly()
            runCatching { process.waitFor(2, TimeUnit.SECONDS) }
        }

        try {
            stdoutThread.join(2_000)
            stderrThread.join(2_000)
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw interrupted
        }

        return CommandResult(
            exitCode = if (completed) process.exitValue() else -1,
            stdout = stdoutThread.value,
            stderr = stderrThread.value,
            timedOut = !completed,
            durationMillis = (System.nanoTime() - started) / 1_000_000
        )
    }

    private class StreamCollector(private val stream: java.io.InputStream) : Thread() {
        @Volatile var value: String = ""
            private set

        override fun run() {
            value = stream.bufferedReader().use { it.readText() }
        }
    }
}
