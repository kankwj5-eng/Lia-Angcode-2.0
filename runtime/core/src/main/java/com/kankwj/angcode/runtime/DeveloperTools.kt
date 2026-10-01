package com.kankwj.angcode.runtime

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.io.File

private val SAFE_TASK = Regex("^[A-Za-z0-9_.:-]{1,100}$")
private val SAFE_TARGET = Regex("^[A-Za-z0-9_./:+-]{1,200}$")

private fun runRegistered(
    context: ToolContext,
    executableId: String,
    arguments: List<String>,
    timeoutMillis: Long = 120_000
): ToolResponse {
    val executable = context.executables[executableId]
        ?: return ToolResponse(
            false,
            "Ejecutable no disponible: " + executableId,
            mapOf("backend" to "unavailable", "executableId" to executableId)
        )

    val file = File(executable)
    if (!file.isFile || !file.canExecute()) {
        return ToolResponse(false, "Ejecutable inválido/no ejecutable: " + executableId)
    }

    val result = CommandRunner().run(
        CommandRequest(
            executable = file.absolutePath,
            arguments = arguments,
            workingDirectory = context.workspace,
            timeoutMillis = timeoutMillis
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
            "executableId" to executableId
        )
    )
}

private fun separatedArgs(raw: String?): List<String> =
    raw?.split('\u001F')?.filter { it.isNotEmpty() }.orEmpty()

class PythonRunTool : AgentTool {
    override val id = "python.run"
    override val description = "Ejecuta un archivo Python del workspace o código corto con -c."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"]
        val code = call.arguments["code"]
        if ((path == null) == (code == null)) {
            return ToolResponse(false, "Especifica exactamente uno: path o code")
        }

        val args = mutableListOf<String>()
        if (path != null) {
            val script = safeWorkspaceFile(context.workspace, path)
            if (!script.isFile) return ToolResponse(false, "Script no encontrado: " + path)
            args += script.absolutePath
        } else {
            if (code!!.length > 100_000) return ToolResponse(false, "Código demasiado grande")
            args += listOf("-c", code)
        }
        args += separatedArgs(call.arguments["args"])
        return runRegistered(context, "python", args)
    }
}

class PythonTestTool : AgentTool {
    override val id = "python.test"
    override val description = "Ejecuta pytest como módulo de Python."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val args = mutableListOf("-m", "pytest")
        call.arguments["path"]?.let { path ->
            val target = safeWorkspaceFile(context.workspace, path)
            if (!target.exists()) return ToolResponse(false, "Ruta de tests no encontrada")
            args += target.absolutePath
        }
        args += separatedArgs(call.arguments["args"])
        return runRegistered(context, "python", args, 10 * 60_000L)
    }
}

class NodeRunTool : AgentTool {
    override val id = "node.run"
    override val description = "Ejecuta un archivo JavaScript del workspace o código corto con -e."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"]
        val code = call.arguments["code"]
        if ((path == null) == (code == null)) {
            return ToolResponse(false, "Especifica exactamente uno: path o code")
        }

        val args = mutableListOf<String>()
        if (path != null) {
            val script = safeWorkspaceFile(context.workspace, path)
            if (!script.isFile) return ToolResponse(false, "Script no encontrado: " + path)
            args += script.absolutePath
        } else {
            if (code!!.length > 100_000) return ToolResponse(false, "Código demasiado grande")
            args += listOf("-e", code)
        }
        args += separatedArgs(call.arguments["args"])
        return runRegistered(context, "node", args)
    }
}

class NpmRunTool : AgentTool {
    override val id = "npm.run"
    override val description = "Ejecuta un script definido en package.json."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val script = call.arguments["script"]?.takeIf { SAFE_TASK.matches(it) }
            ?: return ToolResponse(false, "script faltante o inválido")
        if (!File(context.workspace, "package.json").isFile) {
            return ToolResponse(false, "package.json no encontrado en la raíz del workspace")
        }

        val args = mutableListOf("run", script)
        val extra = separatedArgs(call.arguments["args"])
        if (extra.isNotEmpty()) {
            args += "--"
            args += extra
        }
        return runRegistered(context, "npm", args, 10 * 60_000L)
    }
}

class JsonQueryTool : AgentTool {
    override val id = "json.query"
    override val description = "Consulta JSON con una ruta simple como user.items.0.name."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val query = call.arguments["query"].orEmpty()
        val file = safeWorkspaceFile(context.workspace, path)
        if (!file.isFile) return ToolResponse(false, "JSON no encontrado")
        if (file.length() > 5_000_000) return ToolResponse(false, "JSON demasiado grande")

        var current: JsonElement = runCatching { Json.parseToJsonElement(file.readText()) }
            .getOrElse { return ToolResponse(false, "JSON inválido: " + (it.message ?: "")) }

        if (query.isNotBlank()) {
            for (token in query.split('.').filter { it.isNotEmpty() }) {
                current = when (current) {
                    is JsonObject -> current[token]
                        ?: return ToolResponse(false, "Clave no encontrada: " + token)
                    is JsonArray -> {
                        val index = token.toIntOrNull()
                            ?: return ToolResponse(false, "Índice inválido: " + token)
                        current.getOrNull(index)
                            ?: return ToolResponse(false, "Índice fuera de rango: " + token)
                    }
                    else -> return ToolResponse(false, "No se puede continuar la ruta en: " + token)
                }
            }
        }

        val output = if (current is JsonPrimitive && current.isString) current.content else current.toString()
        return ToolResponse(true, output)
    }
}

class SqliteQueryTool : AgentTool {
    override val id = "sqlite.query"
    override val description = "Ejecuta una consulta SQLite en modo de solo lectura."
    override val requiredPermissions = setOf(ToolPermission.WORKSPACE_READ, ToolPermission.PROCESS_EXECUTE)

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val path = call.arguments["path"] ?: return ToolResponse(false, "Falta path")
        val query = call.arguments["query"]?.trim()?.takeIf { it.isNotEmpty() }
            ?: return ToolResponse(false, "Falta query")
        if (query.length > 100_000) return ToolResponse(false, "Consulta demasiado grande")

        val database = safeWorkspaceFile(context.workspace, path)
        if (!database.isFile) return ToolResponse(false, "Base SQLite no encontrada")

        return runRegistered(
            context,
            "sqlite3",
            listOf("-readonly", "-header", "-column", database.absolutePath, query)
        )
    }
}

class ClangBuildTool : AgentTool {
    override val id = "clang.build"
    override val description = "Compila fuentes C/C++ explícitas a un artefacto del workspace."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val sources = separatedArgs(call.arguments["sources"])
        if (sources.isEmpty()) return ToolResponse(false, "Falta sources")

        val sourcePaths = sources.map { path ->
            val file = safeWorkspaceFile(context.workspace, path)
            if (!file.isFile) return ToolResponse(false, "Fuente no encontrada: " + path)
            file.absolutePath
        }

        val outputPath = call.arguments["output"] ?: "artifacts/native.out"
        val output = safeWorkspaceFile(context.workspace, outputPath)
        output.parentFile?.mkdirs()

        val args = mutableListOf<String>()
        args += sourcePaths
        args += separatedArgs(call.arguments["flags"])
        args += listOf("-o", output.absolutePath)

        val response = runRegistered(context, "clang", args, 10 * 60_000L)
        return response.copy(metadata = response.metadata + mapOf("artifact" to outputPath))
    }
}

class CmakeConfigureTool : AgentTool {
    override val id = "cmake.configure"
    override val description = "Configura CMake usando rutas confinadas al workspace."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val sourcePath = call.arguments["source"].orEmpty()
        val buildPath = call.arguments["build"] ?: "build"
        val source = safeWorkspaceFile(context.workspace, sourcePath)
        val build = safeWorkspaceFile(context.workspace, buildPath).apply { mkdirs() }
        if (!source.isDirectory) return ToolResponse(false, "Directorio source no encontrado")

        val args = mutableListOf("-S", source.absolutePath, "-B", build.absolutePath)
        args += separatedArgs(call.arguments["args"])
        return runRegistered(context, "cmake", args, 10 * 60_000L)
    }
}

class NinjaBuildTool : AgentTool {
    override val id = "ninja.build"
    override val description = "Construye un directorio Ninja del workspace."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val buildPath = call.arguments["build"] ?: "build"
        val build = safeWorkspaceFile(context.workspace, buildPath)
        if (!build.isDirectory) return ToolResponse(false, "Directorio build no encontrado")

        val args = mutableListOf("-C", build.absolutePath)
        call.arguments["target"]?.let { target ->
            if (!SAFE_TARGET.matches(target)) return ToolResponse(false, "Target inválido")
            args += target
        }
        args += separatedArgs(call.arguments["args"])
        return runRegistered(context, "ninja", args, 10 * 60_000L)
    }
}

class AndroidGradleBuildTool : AgentTool {
    override val id = "android.build"
    override val description = "Ejecuta una tarea Gradle/Gradle Wrapper del proyecto Android."
    override val requiredPermissions = setOf(
        ToolPermission.WORKSPACE_READ,
        ToolPermission.WORKSPACE_WRITE,
        ToolPermission.PROCESS_EXECUTE
    )

    override fun invoke(call: ToolCall, context: ToolContext): ToolResponse {
        val task = call.arguments["task"] ?: "assembleDebug"
        if (!SAFE_TASK.matches(task)) return ToolResponse(false, "Tarea Gradle inválida")

        val projectPath = call.arguments["project"].orEmpty()
        val project = safeWorkspaceFile(context.workspace, projectPath)
        if (!project.isDirectory) return ToolResponse(false, "Proyecto Android no encontrado")

        val wrapper = File(project, "gradlew")
        val extra = separatedArgs(call.arguments["args"])

        return if (wrapper.isFile) {
            val executable: String
            val arguments: List<String>
            if (wrapper.canExecute()) {
                executable = wrapper.absolutePath
                arguments = listOf(task, "--no-daemon") + extra
            } else {
                executable = "/system/bin/sh"
                arguments = listOf(wrapper.absolutePath, task, "--no-daemon") + extra
            }
            val result = CommandRunner().run(
                CommandRequest(
                    executable = executable,
                    arguments = arguments,
                    workingDirectory = project,
                    timeoutMillis = 20 * 60_000L
                )
            )
            ToolResponse(
                result.succeeded,
                (result.stdout + if (result.stderr.isBlank()) "" else "\n" + result.stderr).trim(),
                mapOf("exitCode" to result.exitCode.toString(), "backend" to "gradlew")
            )
        } else {
            runRegistered(
                context,
                "gradle",
                listOf("-p", project.absolutePath, task, "--no-daemon") + extra,
                20 * 60_000L
            )
        }
    }
}
