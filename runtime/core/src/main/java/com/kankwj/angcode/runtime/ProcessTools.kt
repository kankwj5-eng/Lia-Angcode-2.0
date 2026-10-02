package com.kankwj.angcode.runtime

import java.io.BufferedWriter
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

data class ManagedProcessSnapshot(
    val id: String,
    val running: Boolean,
    val exitCode: Int?,
    val stdout: String,
    val stderr: String,
    val stdinOpen: Boolean
)

class ProcessRegistry {
    private data class Entry(
        val process: Process,
        val stdin: BufferedWriter?,
        val stdout: StringBuilder = StringBuilder(),
        val stderr: StringBuilder = StringBuilder()
    )

    private val entries = ConcurrentHashMap<String, Entry>()

    fun start(
        request: CommandRequest,
        keepStdinOpen: Boolean = false
    ): String {
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
            }
            .start()

        val writer = process.outputStream.bufferedWriter()
        if (request.stdin != null) {
            writer.write(request.stdin)
            writer.flush()
        }

        val retainedWriter = if (keepStdinOpen) {
            writer
        } else {
            writer.close()
            null
        }

        val id = UUID.randomUUID().toString()
        val entry = Entry(process = process, stdin = retainedWriter)
        entries[id] = entry
        collect(process.inputStream, entry.stdout)
        collect(process.errorStream, entry.stderr)
        return id
    }

    fun write(
        id: String,
        input: String,
        newline: Boolean = false
    ): Boolean {
        val entry = entries[id] ?: return false
        if (!entry.process.isAlive) return false
        val writer = entry.stdin ?: return false

        return runCatching {
            synchronized(writer) {
                writer.write(input)
                if (newline) writer.newLine()
                writer.flush()
            }
            true
        }.getOrDefault(false)
    }

    fun closeInput(id: String): Boolean {
        val entry = entries[id] ?: return false
        val writer = entry.stdin ?: return true
        return runCatching {
            synchronized(writer) {
                writer.close()
            }
            true
        }.getOrDefault(false)
    }

    fun snapshot(id: String): ManagedProcessSnapshot? {
        val entry = entries[id] ?: return null
        val running = entry.process.isAlive
        val exitCode = if (running) null else runCatching {
            entry.process.exitValue()
        }.getOrNull()

        return ManagedProcessSnapshot(
            id = id,
            running = running,
            exitCode = exitCode,
            stdout = synchronized(entry.stdout) {
                entry.stdout.tail(80_000)
            },
            stderr = synchronized(entry.stderr) {
                entry.stderr.tail(80_000)
            },
            stdinOpen = running && entry.stdin != null
        )
    }

    fun list(): List<ManagedProcessSnapshot> =
        entries.keys.mapNotNull(::snapshot).sortedBy { it.id }

    fun listSnapshots(): List<ManagedProcessSnapshot> =
        entries.keys.mapNotNull(::snapshot)

    fun stop(id: String): Boolean {
        val entry = entries[id] ?: return false
        runCatching { entry.stdin?.close() }

        if (entry.process.isAlive) {
            entry.process.destroy()
            if (entry.process.isAlive) {
                entry.process.destroyForcibly()
            }
        }
        return true
    }

    private fun collect(
        stream: java.io.InputStream,
        target: StringBuilder
    ) {
        Thread {
            stream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    synchronized(target) {
                        target.appendLine(line)
                        if (target.length > 250_000) {
                            target.delete(0, target.length - 200_000)
                        }
                    }
                }
            }
        }.apply {
            isDaemon = true
            start()
        }
    }

    private fun StringBuilder.tail(maxChars: Int): String =
        if (length <= maxChars) toString()
        else substring(length - maxChars)
}

class ProcessStartTool(
    private val registry: ProcessRegistry,
    private val policy: ExecutionPolicy
) : AgentTool {
    override val id = "process.start"
    override val description =
        "Inicia un proceso largo y devuelve un processId; interactive=true mantiene stdin abierto."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val executable = call.arguments["executable"]
            ?: return ToolResponse(false, "Falta executable")
        val args = call.arguments["args"]
            ?.split('\u001F')
            ?.filter { it.isNotEmpty() }
            .orEmpty()
        val decision = policy.check(executable, context, args)
        if (!decision.allowed) {
            return ToolResponse(false, decision.reason)
        }

        val interactive = call.arguments["interactive"]
            ?.toBooleanStrictOrNull()
            ?: false

        val id = registry.start(
            CommandRequest(
                executable = executable,
                arguments = args,
                workingDirectory = context.workspace,
                stdin = call.arguments["stdin"]
            ),
            keepStdinOpen = interactive
        )

        return ToolResponse(
            true,
            id,
            mapOf(
                "processId" to id,
                "interactive" to interactive.toString()
            )
        )
    }
}

class ProcessWriteTool(
    private val registry: ProcessRegistry
) : AgentTool {
    override val id = "process.write"
    override val description =
        "Escribe stdin en un proceso interactivo iniciado por process.start."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val id = call.arguments["processId"]
            ?: return ToolResponse(false, "Falta processId")
        val input = call.arguments["input"]
            ?: return ToolResponse(false, "Falta input")
        if (input.length > 100_000) {
            return ToolResponse(false, "Entrada demasiado grande")
        }

        val newline = call.arguments["newline"]
            ?.toBooleanStrictOrNull()
            ?: false

        return if (registry.write(id, input, newline)) {
            ToolResponse(true, "stdin enviado")
        } else {
            ToolResponse(
                false,
                "Proceso no encontrado, terminado o sin stdin interactivo"
            )
        }
    }
}

class ProcessCloseInputTool(
    private val registry: ProcessRegistry
) : AgentTool {
    override val id = "process.stdin.close"
    override val description =
        "Cierra stdin de un proceso interactivo sin detenerlo forzosamente."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val id = call.arguments["processId"]
            ?: return ToolResponse(false, "Falta processId")
        return if (registry.closeInput(id)) {
            ToolResponse(true, "stdin cerrado")
        } else {
            ToolResponse(false, "Proceso no encontrado")
        }
    }
}

class ProcessLogsTool(
    private val registry: ProcessRegistry
) : AgentTool {
    override val id = "process.logs"
    override val description =
        "Consulta salida y estado de un proceso iniciado por process.start."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val id = call.arguments["processId"]
            ?: return ToolResponse(false, "Falta processId")
        val s = registry.snapshot(id)
            ?: return ToolResponse(false, "Proceso no encontrado")

        val output = buildString {
            appendLine(
                "running=" + s.running +
                    " exitCode=" + (s.exitCode ?: "-") +
                    " stdinOpen=" + s.stdinOpen
            )
            if (s.stdout.isNotBlank()) {
                appendLine("--- stdout ---")
                append(s.stdout)
            }
            if (s.stderr.isNotBlank()) {
                appendLine("--- stderr ---")
                append(s.stderr)
            }
        }.trimEnd()

        return ToolResponse(
            true,
            output,
            mapOf(
                "running" to s.running.toString(),
                "stdinOpen" to s.stdinOpen.toString()
            )
        )
    }
}

class ProcessListTool(
    private val registry: ProcessRegistry
) : AgentTool {
    override val id = "process.list"
    override val description =
        "Lista procesos iniciados por AngCode."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val items = registry.list()
        return ToolResponse(
            true,
            items.joinToString("\n") {
                it.id +
                    "\trunning=" + it.running +
                    "\texit=" + (it.exitCode ?: "-") +
                    "\tstdin=" + it.stdinOpen
            },
            mapOf("count" to items.size.toString())
        )
    }
}

class ProcessStopTool(
    private val registry: ProcessRegistry
) : AgentTool {
    override val id = "process.stop"
    override val description =
        "Detiene un proceso iniciado por process.start."
    override val requiredPermissions =
        setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(
        call: ToolCall,
        context: ToolContext
    ): ToolResponse {
        val id = call.arguments["processId"]
            ?: return ToolResponse(false, "Falta processId")
        return if (registry.stop(id)) {
            ToolResponse(true, "Proceso detenido: " + id)
        } else {
            ToolResponse(false, "Proceso no encontrado: " + id)
        }
    }
}
