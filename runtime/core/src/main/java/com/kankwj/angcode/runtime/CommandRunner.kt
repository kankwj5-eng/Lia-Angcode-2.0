package com.kankwj.angcode.runtime

import java.io.File
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class CommandRequest(
    val executable: String,
    val arguments: List<String> = emptyList(),
    val workingDirectory: File? = null,
    val stdin: String? = null,
    val timeoutMillis: Long = 30_000,
    val environment: Map<String, String> = emptyMap(),
    val maxCapturedCharsPerStream: Int = 1_000_000
)

data class CommandResult(
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val timedOut: Boolean,
    val durationMillis: Long,
    val stdoutTruncated: Boolean = false,
    val stderrTruncated: Boolean = false
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

class CommandRunner {
    fun run(request: CommandRequest): CommandResult {
        require(request.timeoutMillis > 0L) { "timeoutMillis debe ser mayor que cero" }
        require(request.maxCapturedCharsPerStream in 4_096..8_000_000) {
            "maxCapturedCharsPerStream fuera de rango"
        }
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

        val stdoutThread = StreamCollector(
            process.inputStream,
            request.maxCapturedCharsPerStream
        )
        val stderrThread = StreamCollector(
            process.errorStream,
            request.maxCapturedCharsPerStream
        )
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
            durationMillis = (System.nanoTime() - started) / 1_000_000,
            stdoutTruncated = stdoutThread.truncated,
            stderrTruncated = stderrThread.truncated
        )
    }

    private class StreamCollector(
        private val stream: java.io.InputStream,
        private val maxChars: Int
    ) : Thread() {
        @Volatile
        var value: String = ""
            private set

        private val wasTruncated = AtomicBoolean(false)
        val truncated: Boolean get() = wasTruncated.get()

        override fun run() {
            val captured = StringBuilder(minOf(maxChars, 64 * 1024))
            stream.bufferedReader().use { reader ->
                val buffer = CharArray(8 * 1024)
                while (true) {
                    val read = reader.read(buffer)
                    if (read <= 0) break

                    val remaining = maxChars - captured.length
                    if (remaining > 0) {
                        captured.append(buffer, 0, minOf(read, remaining))
                    }
                    if (read > remaining.coerceAtLeast(0)) {
                        wasTruncated.set(true)
                    }
                }
            }
            value = captured.toString()
        }
    }
}
