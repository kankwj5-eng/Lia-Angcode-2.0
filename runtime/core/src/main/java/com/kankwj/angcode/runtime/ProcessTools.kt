package com.kankwj.angcode.runtime

import java.io.File
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.thread

data class ManagedProcessSnapshot(
    val id: String,
    val command: List<String>,
    val startedAt: Instant,
    val alive: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String
)

private class CappedLog(private val maxChars: Int = 200_000) {
    private val buffer = StringBuilder()

    @Synchronized
    fun append(text: String) {
        buffer.append(text)
        val overflow = buffer.length - maxChars
        if (overflow > 0) buffer.delete(0, overflow)
    }

    @Synchronized
    fun snapshot(): String = buffer.toString()
}

private data class ManagedProcess(
    val id: String,
    val command: List<String>,
    val process: Process,
    val startedAt: Instant,
    val stdout: CappedLog,
    val stderr: CappedLog
)

class ManagedProcessRegistry(
    private val policy: ExecutionPolicy = ExecutionPolicy.androidBase()
) {
    private val processes = ConcurrentHashMap<String, ManagedProcess>()

    fun start(
        executable: String,
        arguments: List<String>,
        context: ToolContext
    ): ToolResponse {
        val decision = policy.check(executable, context, arguments)
        if (!decision.allowed) return ToolResponse(false, decision.reason)

        val command = buildList {
            add(executable)
            addAll(arguments)
        }
        val process = ProcessBuilder(command)
            .directory(context.workspace)
            .redirectErrorStream(false)
            .start()
        process.outputStream.close()

        val managed = ManagedProcess(
            id = UUID.randomUUID().toString(),
            command = command,
            process = process,
            startedAt = Instant.now(),
            stdout = CappedLog(),
            stderr = CappedLog()
        )
        processes[managed.id] = managed

        pump(managed.process.inputStream, managed.stdout)
        pump(managed.process.errorStream, managed.stderr)

        return ToolResponse(
            true,
            managed.id,
            mapOf("pid" to runCatching { process.pid().toString() }.getOrDefault(""))
        )
    }

    fun snapshot(id: String): ManagedProcessSnapshot? {
        val managed = processes[id] ?: return null
        val alive = managed.process.isAlive
        val exit = if (alive) null else runCatching { managed.process.exitValue() }.getOrNull()
        return ManagedProcessSnapshot(
            id = managed.id,
            command = managed.command,
            startedAt = managed.startedAt,
            alive = alive,
            exitCode = exit,
            stdout = managed.stdout.snapshot(),
            stderr = managed.stderr.snapshot()
        )
    }

    fun list(): List<ManagedProcessSnapshot> =
        processes.keys.mapNotNull(::snapshot).sortedByDescending { it.startedAt }

    fun stop(id: String, force: Boolean): Boolean {
        val managed = processes[id] ?: return false
        if (!managed.process.isAlive) return true
        if (force) managed.process.destroyForcibly() else managed.process.destroy()
        return true
    }

    private fun pump(input: java.io.InputStream, log: CappedLog) {
        thread(name = "angcode-process-log", isDaemon = true) {
            input.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    log.append(line)
                    log.append("\n")
                }
            }
        }
    }
}

class ProcessStartTool(private val registry: ManagedProcessRegistry) : AgentTool {
    override val id = "process.start"
    override val description = "Inicia un proceso que puede seguir ejecutándose mientras otros agentes trabajan."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val executable = call.arguments["executable"] ?: return ToolResponse(false, "Falta executable")
        val args = call.arguments["args"]?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty()
        return registry.start(executable, args, context)
    }
}

class ProcessLogsTool(private val registry: ManagedProcessRegistry) : AgentTool {
    override val id = "process.logs"
    override val description = "Devuelve stdout/stderr acumulado y estado de un proceso."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val id = call.arguments["id"] ?: return ToolResponse(false, "Falta id")
        val snapshot = registry.snapshot(id) ?: return ToolResponse(false, "Proceso no encontrado")
        val output = buildString {
            if (snapshot.stdout.isNotBlank()) append(snapshot.stdout)
            if (snapshot.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n--- stderr ---\n")
                append(snapshot.stderr)
            }
        }
        return ToolResponse(
            true,
            output,
            mapOf(
                "alive" to snapshot.alive.toString(),
                "exitCode" to (snapshot.exitCode?.toString() ?: ""),
                "command" to snapshot.command.joinToString(" ")
            )
        )
    }
}

class ProcessListTool(private val registry: ManagedProcessRegistry) : AgentTool {
    override val id = "process.list"
    override val description = "Lista los procesos iniciados por AngCode."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val entries = registry.list()
        val output = entries.joinToString("\n") {
            it.id + "\t" + (if (it.alive) "running" else "done") + "\t" + it.command.joinToString(" ")
        }
        return ToolResponse(true, output, mapOf("count" to entries.size.toString()))
    }
}

class ProcessStopTool(private val registry: ManagedProcessRegistry) : AgentTool {
    override val id = "process.stop"
    override val description = "Detiene un proceso administrado por AngCode."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val id = call.arguments["id"] ?: return ToolResponse(false, "Falta id")
        val force = call.arguments["force"]?.toBooleanStrictOrNull() ?: false
        return if (registry.stop(id, force)) {
            ToolResponse(true, "Solicitud de detención enviada")
        } else {
            ToolResponse(false, "Proceso no encontrado")
        }
    }
}
