package com.kankwj.angcode.runtime

enum class ToolFamily {
    FILES,
    PROCESS,
    NETWORK,
    ARCHIVE,
    VERSION_CONTROL,
    CODE_INTELLIGENCE,
    ANDROID,
    SANDBOX,
    CONNECTOR
}

data class ToolCapability(
    val id: String,
    val family: ToolFamily,
    val description: String,
    val status: CapabilityStatus,
    val upstream: String? = null
)

enum class CapabilityStatus {
    BUILT_IN,
    READY_TO_ADAPT,
    OPTIONAL_BACKEND,
    PLANNED
}

object ToolCatalog {
    val capabilities = listOf(
        ToolCapability("workspace.list", ToolFamily.FILES, "Listar archivos", CapabilityStatus.BUILT_IN),
        ToolCapability("workspace.search", ToolFamily.FILES, "Buscar texto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.read", ToolFamily.FILES, "Leer texto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.write", ToolFamily.FILES, "Escribir texto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.patch", ToolFamily.FILES, "Parche exacto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.sha256", ToolFamily.FILES, "Hash SHA-256", CapabilityStatus.BUILT_IN),
        ToolCapability("process.exec", ToolFamily.PROCESS, "Ejecutar proceso corto", CapabilityStatus.BUILT_IN),
        ToolCapability("process.start", ToolFamily.PROCESS, "Iniciar proceso administrado", CapabilityStatus.BUILT_IN),
        ToolCapability("process.logs", ToolFamily.PROCESS, "Leer logs de un proceso", CapabilityStatus.BUILT_IN),
        ToolCapability("process.list", ToolFamily.PROCESS, "Listar procesos activos", CapabilityStatus.BUILT_IN),
        ToolCapability("process.stop", ToolFamily.PROCESS, "Detener proceso administrado", CapabilityStatus.BUILT_IN),
        ToolCapability("http.get", ToolFamily.NETWORK, "HTTP limitado", CapabilityStatus.BUILT_IN),
        ToolCapability("archive.zip", ToolFamily.ARCHIVE, "Comprimir workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("archive.unzip", ToolFamily.ARCHIVE, "Extraer ZIP seguro", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.create", ToolFamily.ARCHIVE, "Crear checkpoint del workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.list", ToolFamily.ARCHIVE, "Listar checkpoints", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.restore", ToolFamily.ARCHIVE, "Restaurar checkpoint", CapabilityStatus.BUILT_IN),
        ToolCapability("git.*", ToolFamily.VERSION_CONTROL, "Git dentro del sandbox", CapabilityStatus.READY_TO_ADAPT, "git/git"),
        ToolCapability("code.ast.*", ToolFamily.CODE_INTELLIGENCE, "AST incremental", CapabilityStatus.READY_TO_ADAPT, "tree-sitter/tree-sitter"),
        ToolCapability("android.api.*", ToolFamily.ANDROID, "Sensores y APIs del teléfono", CapabilityStatus.READY_TO_ADAPT, "termux/termux-api"),
        ToolCapability("android.shizuku.*", ToolFamily.ANDROID, "APIs con privilegios ADB", CapabilityStatus.OPTIONAL_BACKEND, "RikkaApps/Shizuku"),
        ToolCapability("android.root.*", ToolFamily.ANDROID, "Backend root opcional", CapabilityStatus.OPTIONAL_BACKEND, "topjohnwu/libsu"),
        ToolCapability("sandbox.proot.*", ToolFamily.SANDBOX, "Linux PRoot", CapabilityStatus.READY_TO_ADAPT, "termux/proot-distro"),
        ToolCapability("mcp.*", ToolFamily.CONNECTOR, "Herramientas MCP", CapabilityStatus.PLANNED)
    )
}
