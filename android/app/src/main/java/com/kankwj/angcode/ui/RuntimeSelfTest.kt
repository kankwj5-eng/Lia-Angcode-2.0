package com.kankwj.angcode.ui

import android.content.Context
import com.kankwj.angcode.runtime.ToolBroker
import com.kankwj.angcode.runtime.ToolCall
import com.kankwj.angcode.runtime.ToolContext
import com.kankwj.angcode.runtime.ToolPermission
import com.kankwj.angcode.runtime.WorkspaceManager
import com.kankwj.angcode.runtime.registerCoreTools

data class SelfTestStep(
    val name: String,
    val ok: Boolean,
    val detail: String
)

data class RuntimeSelfTestResult(
    val passed: Boolean,
    val steps: List<SelfTestStep>
)

class RuntimeSelfTest(
    context: Context
) {
    private val appContext = context.applicationContext

    fun run(): RuntimeSelfTestResult {
        val workspace = WorkspaceManager(appContext)
            .createWorkspace("_selftest")
        workspace.deleteRecursively()
        workspace.mkdirs()

        val broker = ToolBroker().registerCoreTools()
        val toolContext = ToolContext(
            workspace = workspace,
            grantedPermissions = setOf(
                ToolPermission.WORKSPACE_READ,
                ToolPermission.WORKSPACE_WRITE,
                ToolPermission.PROCESS_EXECUTE,
                ToolPermission.ARTIFACT_WRITE
            )
        )

        val steps = mutableListOf<SelfTestStep>()

        fun execute(
            name: String,
            call: ToolCall,
            validator: (String) -> Boolean = { true }
        ) {
            val response = runCatching {
                broker.execute(call, toolContext)
            }.getOrElse { error ->
                steps += SelfTestStep(
                    name,
                    false,
                    error.message ?: error::class.java.simpleName
                )
                return
            }

            steps += SelfTestStep(
                name = name,
                ok = response.ok && validator(response.output),
                detail = if (response.ok) {
                    response.output.take(240).ifBlank { "OK" }
                } else {
                    response.output.take(240)
                }
            )
        }

        try {
            execute(
                "Escritura workspace",
                ToolCall(
                    "file.write",
                    mapOf(
                        "path" to "hello.txt",
                        "content" to "angcode-self-test"
                    )
                )
            )

            execute(
                "Lectura workspace",
                ToolCall(
                    "file.read",
                    mapOf("path" to "hello.txt")
                )
            ) { it == "angcode-self-test" }

            execute(
                "SHA-256",
                ToolCall(
                    "file.sha256",
                    mapOf("path" to "hello.txt")
                )
            ) { it.matches(Regex("^[0-9a-f]{64}$")) }

            execute(
                "Checkpoint",
                ToolCall(
                    "checkpoint.create",
                    mapOf("label" to "selftest")
                )
            )

            execute(
                "ZIP artifact",
                ToolCall(
                    "artifact.zip",
                    mapOf(
                        "path" to "hello.txt",
                        "name" to "selftest"
                    )
                )
            )

            execute(
                "Proceso Android",
                ToolCall(
                    "process.exec",
                    mapOf(
                        "executable" to "/system/bin/toybox",
                        "args" to listOf(
                            "echo",
                            "angcode-process-ok"
                        ).joinToString("\u001F")
                    )
                )
            ) { it.contains("angcode-process-ok") }

            execute(
                "Catálogo de tools",
                ToolCall("tool.catalog")
            ) { it.contains("file.read") && it.contains("process.exec") }
        } finally {
            workspace.deleteRecursively()
        }

        return RuntimeSelfTestResult(
            passed = steps.isNotEmpty() && steps.all(SelfTestStep::ok),
            steps = steps
        )
    }
}
