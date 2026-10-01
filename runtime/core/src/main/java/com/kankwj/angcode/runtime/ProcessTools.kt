package com.kankwj.angcode.runtime

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ManagedProcessSnapshot(
    val id: String,
    val running: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String
)

class ProcessRegistry {
    private data class Entry(
        val process: Process,
        val stdout: StringBuilder = StringBuilder(),
        val stderr: StringBuilder = StringBuilder()
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun start(request: CommandRequest): String {
        val command = buildList {
            add(request.executable)
            addAll(request.arguments)
        }
        val process = ProcessBuilder(command)
            .apply { request.workingDirectory?.let { directory(it) } }
            .start()

        if (request.stdin != null) {
            process.outputStream.bufferedWriter().use { it.write(request.stdin) }
        } else {
            process.outputStream.close()
        }

        val id = UUID.randomUUID().toString()
        val entry = Entry(process)
        entries[id] = entry
        collect(process.inputStream, entry.stdout)
        collect(process.errorStream, entry.stderr)
        return id
    }

    fun snapshot(id: String): ManagedProcessSnapshot? {
        val entry = entries[id] ?: return null
        val running = entry.process.isAlive
        val exitCode = if (running) null else runCatching { entry.process.exitValue() }.getOrNull()
        return ManagedProcessSnapshot(
            id = id,
            running = running,
            exitCode = exitCode,
            stdout = synchronized(entry.stdout) { entry.stdout.tail(80_000) },
            stderr = synchronized(entry.stderr) { entry.stderr.tail(80_000) }
        )
    }

    fun list(): List<ManagedProcessSnapshot> =
        entries.keys.mapNotNull(::snapshot).sortedBy { it.id }

    fun listSnapshots(): List<ManagedProcessSnapshot> =
        entries.keys.mapNotNull(::snapshot)

    fun stop(id: String): Boolean {
        val entry = entries[id] ?: return false
        if (entry.process.isAlive) {
            entry.process.destroy()
            if (entry.process.isAlive) entry.process.destroyForcibly()
        }
        return true
    }

    private fun collect(stream: java.io.InputStream, target: StringBuilder) {
        Thread {
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(target) {
                        target.appendLine(line)
                        if (target.length > 250_000) target.delete(0, target.length - 200_000)
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun StringBuilder.tail(maxChars: Int): String =
        if (length <= maxChars) toString() else substring(length - maxChars)
}

class ProcessStartTool(
    private val registry: ProcessRegistry,
    private val policy: ExecutionPolicy
) : AgentTool {
    override val id = "process.start"
    override val description = "Inicia un proceso largo y devuelve un processId."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val executable = call.arguments["executable"] ?: return ToolResponse(false, "Falta executable")
        val args = call.arguments["args"]?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty()
        val decision = policy.check(executable, context, args)
        if (!decision.allowed) return ToolResponse(false, decision.reason)

        val id = registry.start(
            CommandRequest(
                executable = executable,
                arguments = args,
                workingDirectory = context.workspace,
                stdin = call.arguments["stdin"]
            )
        )
        return ToolResponse(true, id, mapOf("processId" to id))
    }
}

class ProcessLogsTool(private val registry: ProcessRegistry) : AgentTool {
    override val id = "process.logs"
    override val description = "Consulta salida y estado de un proceso iniciado por process.start."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val id = call.arguments["processId"] ?: return ToolResponse(false, "Falta processId")
        val s = registry.snapshot(id) ?: return ToolResponse(false, "Proceso no encontrado")
        val output = buildString {
            appendLine("running=${s.running} exitCode=${s.exitCode ?: "-"}")
            if (s.stdout.isNotBlank()) {
                appendLine("--- stdout ---")
                append(s.stdout)
            }
            if (s.stderr.isNotBlank()) {
                appendLine("--- stderr ---")
                append(s.stderr)
            }
        }.trimEnd()
        return ToolResponse(true, output, mapOf("running" to s.running.toString()))
    }
}

class ProcessListTool(private val registry: ProcessRegistry) : AgentTool {
    override val id = "process.list"
    override val description = "Lista procesos iniciados por AngCode."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val items = registry.list()
        return ToolResponse(
            true,
            items.joinToString("\n") { "${it.id}\trunning=${it.running}\texit=${it.exitCode ?: "-"}" },
            mapOf("count" to items.size.toString())
        )
    }
}

class ProcessStopTool(private val registry: ProcessRegistry) : AgentTool {
    override val id = "process.stop"
    override val description = "Detiene un proceso iniciado por process.start."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val id = call.arguments["processId"] ?: return ToolResponse(false, "Falta processId")
        return if (registry.stop(id)) ToolResponse(true, "Proceso detenido: $id")
        else ToolResponse(false, "Proceso no encontrado: $id")
    }
}
