package com.kankwj.angcode.runtime

class MediaProbeTool : AgentTool {
    override val id = "media.probe"
    override val description = "Obtiene metadatos de audio/video mediante ffprobe en JSON."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val input = safeWorkspaceFile(context.workspace, path)
        if (!input.isFile) return ToolResponse(false, "Archivo multimedia no encontrado")

        return runMediaExecutable(
            context,
            "ffprobe",
            listOf(
                "-v", "error",
                "-show_format",
                "-show_streams",
                "-of", "json",
                input.absolutePath
            ),
            60_000
        )
    }
}

class MediaConvertTool : AgentTool {
    override val id = "media.convert"
    override val description = "Convierte audio/video con FFmpeg dentro del workspace."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val inputPath = call.arguments["input"] ?: return ToolResponse(false, "Falta input")
        val outputPath = call.arguments["output"] ?: return ToolResponse(false, "Falta output")
        val input = safeWorkspaceFile(context.workspace, inputPath)
        val output = safeWorkspaceFile(context.workspace, outputPath)
        if (!input.isFile) return ToolResponse(false, "Input no encontrado")
        output.parentFile?.mkdirs()

        val args = mutableListOf("-y", "-i", input.absolutePath)
        args += call.arguments["args"]?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty()
        args += output.absolutePath

        val response = runMediaExecutable(context, "ffmpeg", args, 20 * 60_000L)
        return response.copy(metadata = response.metadata + mapOf("artifact" to outputPath))
    }
}

class ImageConvertTool : AgentTool {
    override val id = "image.convert"
    override val description = "Transforma imágenes mediante ImageMagick/magick."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val inputPath = call.arguments["input"] ?: return ToolResponse(false, "Falta input")
        val outputPath = call.arguments["output"] ?: return ToolResponse(false, "Falta output")
        val input = safeWorkspaceFile(context.workspace, inputPath)
        val output = safeWorkspaceFile(context.workspace, outputPath)
        if (!input.isFile) return ToolResponse(false, "Input no encontrado")
        output.parentFile?.mkdirs()

        val args = mutableListOf(input.absolutePath)
        args += call.arguments["args"]?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty()
        args += output.absolutePath

        val response = runMediaExecutable(context, "magick", args, 5 * 60_000L)
        return response.copy(metadata = response.metadata + mapOf("artifact" to outputPath))
    }
}

private fun runMediaExecutable(
    context: ToolContext,
    executableId: String,
    arguments: List<String>,
    timeoutMillis: Long
): ToolResponse {
    val executable = context.executables[executableId]
        ?: return ToolResponse(false, "Ejecutable no disponible: " + executableId)

    val result = CommandRunner().run(
        CommandRequest(
            executable = executable,
            arguments = arguments,
            workingDirectory = context.workspace,
            timeoutMillis = timeoutMillis
        )
    )

    return ToolResponse(
        result.succeeded,
        buildString {
            if (result.stdout.isNotBlank()) append(result.stdout.trimEnd())
            if (result.stderr.isNotBlank()) {
                if (isNotEmpty()) append("\n")
                append(result.stderr.trimEnd())
            }
        },
        mapOf(
            "exitCode" to result.exitCode.toString(),
            "durationMs" to result.durationMillis.toString(),
            "executableId" to executableId
        )
    )
}
