package com.kankwj.angcode.agents

import java.io.File
import java.util.UUID

enum class ProjectKind {
    ANDROID,
    WEB_NODE,
    PYTHON,
    NATIVE,
    RUST,
    GO,
    GENERIC
}

enum class MissionCapability {
    INSPECT,
    EDIT_CODE,
    TEST,
    BUILD,
    WEB_RESEARCH,
    BROWSER_AUTOMATION,
    VERSION_CONTROL,
    MULTIMEDIA,
    ANDROID_DEVICE,
    LINUX_SANDBOX
}

data class ProjectProfile(
    val kind: ProjectKind,
    val signals: List<String>,
    val suggestedToolPacks: List<String>
)

data class MissionAnalysis(
    val mission: String,
    val project: ProjectProfile,
    val capabilities: Set<MissionCapability>,
    val recommendedToolPacks: List<String>,
    val plan: MissionPlan
)

class ProjectProfiler {
    fun profile(workspace: File): ProjectProfile {
        val files = workspace.listFiles().orEmpty().associateBy { it.name }

        fun has(name: String) = name in files
        fun hasExtension(extension: String): Boolean =
            workspace.walkTopDown()
                .maxDepth(4)
                .any { it.isFile && it.extension.equals(extension, ignoreCase = true) }

        val signals = mutableListOf<String>()

        val kind = when {
            (
                has("settings.gradle") ||
                    has("settings.gradle.kts") ||
                    has("build.gradle") ||
                    has("build.gradle.kts")
            ) && workspace.walkTopDown()
                .maxDepth(5)
                .any { it.name == "AndroidManifest.xml" } -> {
                signals += "Gradle/Android"
                ProjectKind.ANDROID
            }
            has("package.json") -> {
                signals += "package.json"
                ProjectKind.WEB_NODE
            }
            has("pyproject.toml") || has("requirements.txt") || hasExtension("py") -> {
                signals += "Python"
                ProjectKind.PYTHON
            }
            has("CMakeLists.txt") || has("meson.build") ||
                hasExtension("c") || hasExtension("cpp") || hasExtension("cc") -> {
                signals += "C/C++"
                ProjectKind.NATIVE
            }
            has("Cargo.toml") -> {
                signals += "Cargo.toml"
                ProjectKind.RUST
            }
            has("go.mod") -> {
                signals += "go.mod"
                ProjectKind.GO
            }
            else -> {
                signals += "Sin framework dominante"
                ProjectKind.GENERIC
            }
        }

        val packs = when (kind) {
            ProjectKind.ANDROID -> listOf("core-dev", "android-dev")
            ProjectKind.WEB_NODE -> listOf("core-dev", "web-node")
            ProjectKind.PYTHON -> listOf("core-dev", "python-data")
            ProjectKind.NATIVE -> listOf("core-dev", "native-dev")
            ProjectKind.RUST -> listOf("core-dev")
            ProjectKind.GO -> listOf("core-dev")
            ProjectKind.GENERIC -> listOf("core-dev")
        }

        return ProjectProfile(
            kind = kind,
            signals = signals,
            suggestedToolPacks = packs
        )
    }
}

class MissionAnalyzer(
    private val profiler: ProjectProfiler = ProjectProfiler()
) {
    fun analyze(
        mission: String,
        workspace: File
    ): MissionAnalysis {
        val normalized = mission.lowercase()
        val profile = profiler.profile(workspace)
        val capabilities = linkedSetOf(MissionCapability.INSPECT)

        fun containsAny(vararg terms: String): Boolean =
            terms.any(normalized::contains)

        if (containsAny(
                "corrige", "arregla", "implementa", "crea", "añade", "agrega",
                "modifica", "refactor", "programa", "código", "codigo"
            )
        ) capabilities += MissionCapability.EDIT_CODE

        if (containsAny(
                "test", "prueba", "verifica", "validar", "validación", "crash",
                "bug", "error"
            )
        ) capabilities += MissionCapability.TEST

        if (containsAny(
                "compila", "build", "apk", "aab", "release", "artefacto",
                "artifact", "ejecutable"
            )
        ) capabilities += MissionCapability.BUILD

        if (containsAny(
                "busca", "investiga", "documentación", "documentacion",
                "github", "web", "internet"
            )
        ) capabilities += MissionCapability.WEB_RESEARCH

        if (containsAny(
                "navegador", "browser", "página", "pagina", "sitio",
                "captura", "screenshot"
            )
        ) capabilities += MissionCapability.BROWSER_AUTOMATION

        if (containsAny(
                "git", "commit", "rama", "branch", "merge", "worktree"
            ) || MissionCapability.EDIT_CODE in capabilities
        ) capabilities += MissionCapability.VERSION_CONTROL

        if (containsAny(
                "video", "audio", "imagen", "ffmpeg", "media"
            )
        ) capabilities += MissionCapability.MULTIMEDIA

        if (containsAny(
                "android", "apk", "logcat", "dispositivo", "teléfono", "telefono"
            )
        ) capabilities += MissionCapability.ANDROID_DEVICE

        if (containsAny(
                "linux", "debian", "ubuntu", "proot", "contenedor", "sandbox"
            )
        ) capabilities += MissionCapability.LINUX_SANDBOX

        val packs = linkedSetOf<String>()
        packs += profile.suggestedToolPacks
        if (MissionCapability.MULTIMEDIA in capabilities) packs += "media"
        if (MissionCapability.BROWSER_AUTOMATION in capabilities) packs += "lightpanda-browser"
        if (MissionCapability.ANDROID_DEVICE in capabilities) packs += "android-dev"
        if (profile.kind == ProjectKind.PYTHON) packs += "python-data"
        if (profile.kind == ProjectKind.WEB_NODE) packs += "web-node"
        if (profile.kind == ProjectKind.NATIVE) packs += "native-dev"

        return MissionAnalysis(
            mission = mission,
            project = profile,
            capabilities = capabilities,
            recommendedToolPacks = packs.toList(),
            plan = buildPlan(mission, capabilities)
        )
    }

    private fun buildPlan(
        mission: String,
        capabilities: Set<MissionCapability>
    ): MissionPlan {
        val tasks = mutableListOf<MissionTask>()

        val inspect = MissionTask(
            title = "Inspeccionar proyecto y contexto",
            role = AgentRole.RESEARCHER
        )
        tasks += inspect

        var researchId: String? = null
        if (
            MissionCapability.WEB_RESEARCH in capabilities ||
            MissionCapability.BROWSER_AUTOMATION in capabilities
        ) {
            val research = MissionTask(
                title = "Investigar documentación y referencias",
                role = if (MissionCapability.BROWSER_AUTOMATION in capabilities) {
                    AgentRole.BROWSER
                } else {
                    AgentRole.RESEARCHER
                },
                dependsOn = setOf(inspect.id)
            )
            tasks += research
            researchId = research.id
        }

        var codeId: String? = null
        if (MissionCapability.EDIT_CODE in capabilities) {
            val deps = buildSet {
                add(inspect.id)
                researchId?.let(::add)
            }
            val code = MissionTask(
                title = "Implementar cambios",
                role = AgentRole.CODER,
                dependsOn = deps
            )
            tasks += code
            codeId = code.id
        }

        var testId: String? = null
        if (
            MissionCapability.TEST in capabilities ||
            MissionCapability.EDIT_CODE in capabilities
        ) {
            val test = MissionTask(
                title = "Ejecutar pruebas y validar cambios",
                role = AgentRole.TESTER,
                dependsOn = setOf(codeId ?: inspect.id)
            )
            tasks += test
            testId = test.id
        }

        if (
            MissionCapability.BUILD in capabilities ||
            MissionCapability.ANDROID_DEVICE in capabilities
        ) {
            tasks += MissionTask(
                title = "Construir y verificar artefactos",
                role = AgentRole.BUILDER,
                dependsOn = setOf(testId ?: codeId ?: inspect.id)
            )
        }

        if (tasks.size == 1) {
            tasks += MissionTask(
                title = "Resolver misión",
                role = AgentRole.CODER,
                dependsOn = setOf(inspect.id)
            )
        }

        return MissionPlan(
            id = UUID.randomUUID().toString(),
            title = mission.take(120),
            tasks = tasks
        )
    }
}
