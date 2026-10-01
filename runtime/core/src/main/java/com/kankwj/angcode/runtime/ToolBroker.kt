package com.kankwj.angcode.runtime

import java.io.File
import java.util.concurrent.ConcurrentHashMap

enum class ToolPermission {
    WORKSPACE_READ,
    WORKSPACE_WRITE,
    PROCESS_EXECUTE,
    UNRESTRICTED_SHELL,
    NETWORK,
    PRIVATE_NETWORK,
    ANDROID_BRIDGE,
    CLIPBOARD_READ,
    CLIPBOARD_WRITE
}

data class ToolContext(
    val workspace: File,
    val grantedPermissions: Set<ToolPermission>,
    val executables: Map<String, String> = emptyMap()
)

data class ToolCall(
    val toolId: String,
    val arguments: Map<String, String> = emptyMap()
)

data class ToolResponse(
    val ok: Boolean,
    val output: String,
    val metadata: Map<String, String> = emptyMap()
)

interface AgentTool {
    val id: String
    val description: String
    val requiredPermissions: Set<ToolPermission>
    fun invoke(call: ToolCall, context: ToolContext): ToolResponse
}

class ToolBroker {
    private val tools = ConcurrentHashMap<String, AgentTool>()

    fun register(tool: AgentTool) {
        require(tool.id.isNotBlank()) { "Tool id cannot be blank" }
        tools[tool.id] = tool
    }

    fun availableTools(): List<AgentTool> = tools.values.sortedBy { it.id }

    fun execute(call: ToolCall, context: ToolContext): ToolResponse {
        val tool = tools[call.toolId]
            ?: return ToolResponse(false, "Herramienta no registrada: ${call.toolId}")

        val missing = tool.requiredPermissions - context.grantedPermissions
        if (missing.isNotEmpty()) {
            return ToolResponse(false, "Permisos faltantes: ${missing.joinToString()}")
        }

        return runCatching { tool.invoke(call, context) }
            .getOrElse { ToolResponse(false, it.message ?: it::class.java.simpleName) }
    }
}

class SystemCommandTool(
    private val runner: CommandRunner = CommandRunner(),
    private val policy: ExecutionPolicy = ExecutionPolicy.androidBase()
) : AgentTool {
    override val id: String = "process.exec"
    override val description = "Ejecuta un binario permitido con argumentos estructurados dentro del workspace."
    override val requiredPermissions = setOf(ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val executable = call.arguments["executable"]
            ?: return ToolResponse(false, "Falta executable")
        val args = call.arguments["args"]
            ?.split('\u001F')
            ?.filter { it.isNotEmpty() }
            .orEmpty()

        val decision = policy.check(executable, context, args)
        if (!decision.allowed) {
            return ToolResponse(false, decision.reason, mapOf("policy" to "denied"))
        }

        val result = runner.run(
            CommandRequest(
                executable = executable,
                arguments = args,
                workingDirectory = context.workspace
            )
        )

        return ToolResponse(
            ok = result.succeeded,
            output = buildString {
                if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
                if (result.stderr.isNotBlank()) {
                    if (isNotEmpty()) append("\n")
                    append(result.stderr.trimEnd())
                }
            },
            metadata = mapOf(
                "exitCode" to result.exitCode.toString(),
                "durationMs" to result.durationMillis.toString(),
                "timedOut" to result.timedOut.toString(),
                "policy" to "allowed"
            )
        )
    }
}
