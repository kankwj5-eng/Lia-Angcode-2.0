package com.kankwj.angcode.runtime

class ToolCatalogTool : AgentTool {
    override val id = "tool.catalog"
    override val description = "Enumera capacidades conocidas, familia, estado y upstream."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val familyFilter = call.arguments["family"]
            ?.uppercase()
            ?.let { raw -> runCatching { ToolFamily.valueOf(raw) }.getOrNull() }

        val statusFilter = call.arguments["status"]
            ?.uppercase()
            ?.let { raw -> runCatching { CapabilityStatus.valueOf(raw) }.getOrNull() }

        val filtered = ToolCatalog.capabilities.filter { capability ->
            (familyFilter == null || capability.family == familyFilter) &&
                (statusFilter == null || capability.status == statusFilter)
        }

        val output = filtered.joinToString("\n") { capability ->
            buildString {
                append(capability.id)
                append("\t")
                append(capability.family.name)
                append("\t")
                append(capability.status.name)
                append("\t")
                append(capability.description)
                capability.upstream?.let {
                    append("\tupstream=")
                    append(it)
                }
            }
        }

        return ToolResponse(
            true,
            output,
            mapOf("count" to filtered.size.toString())
        )
    }
}

class ToolPackListTool(
    private val repository: ToolPackAssetRepository
) : AgentTool {
    override val id = "toolpack.list"
    override val description = "Lista Tool Packs empaquetados con AngCode."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val packs = repository.loadAll()
        val output = packs.joinToString("\n") { pack ->
            pack.id + "\t" + pack.name + "\t" + pack.version + "\t" +
                pack.runtime.name + "\tpackages=" + pack.packages.size + "\ttools=" + pack.exportedTools.size
        }
        return ToolResponse(true, output, mapOf("count" to packs.size.toString()))
    }
}

class ToolPackInspectTool(
    private val repository: ToolPackAssetRepository
) : AgentTool {
    override val id = "toolpack.inspect"
    override val description = "Describe paquetes, herramientas, permisos, runtime y arquitecturas de un Tool Pack."
    override val requiredPermissions = emptySet<ToolPermission>()

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val id = call.arguments["id"] ?: return ToolResponse(false, "Falta id")
        val pack = repository.load(id) ?: return ToolResponse(false, "Tool Pack no encontrado: " + id)

        val output = buildString {
            appendLine("id=" + pack.id)
            appendLine("name=" + pack.name)
            appendLine("version=" + pack.version)
            appendLine("runtime=" + pack.runtime.name)
            appendLine("architectures=" + pack.architectures.joinToString(","))
            appendLine("permissions=" + pack.requiredPermissions.joinToString(",") { it.name })
            appendLine("packages=" + pack.packages.joinToString(","))
            appendLine("tools=" + pack.exportedTools.joinToString(","))
            pack.upstream?.let { upstream ->
                appendLine("upstream=" + upstream.name)
                appendLine("license=" + upstream.license)
                appendLine("source=" + upstream.source)
            }
            if (pack.description.isNotBlank()) append("description=" + pack.description)
        }.trimEnd()

        return ToolResponse(true, output)
    }
}
