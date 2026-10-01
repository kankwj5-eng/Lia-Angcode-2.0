package com.kankwj.angcode.agents

import com.kankwj.angcode.runtime.AgentTool
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.ToolResponse

class ManagedAgentTool(
    private val workerName: String,
    override val description: String,
    private val executor: (String, ToolContext) -> String
) : AgentTool {
    override val id = "agent." + workerName
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val task = call.arguments["task"]
            ?: return ToolResponse(false, "Falta task para " + id)

        return runCatching {
            ToolResponse(
                ok = true,
                output = executor(task, context),
                metadata = mapOf("managedAgent" to workerName)
            )
        }.getOrElse {
            ToolResponse(false, it.message ?: "Fallo del agente " + workerName)
        }
    }
}
