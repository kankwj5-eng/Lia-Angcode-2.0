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
        ToolCapability("workspace.search", ToolFamily.FILES, "Buscar texto general", CapabilityStatus.BUILT_IN),
        ToolCapability("code.search", ToolFamily.CODE_INTELLIGENCE, "Buscar texto dentro de código", CapabilityStatus.BUILT_IN),
        ToolCapability("file.read", ToolFamily.FILES, "Leer texto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.write", ToolFamily.FILES, "Escribir texto", CapabilityStatus.BUILT_IN),
        ToolCapability("file.patch", ToolFamily.FILES, "Parche exacto y no ambiguo", CapabilityStatus.BUILT_IN),
        ToolCapability("file.delete", ToolFamily.FILES, "Eliminar dentro del workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("file.sha256", ToolFamily.FILES, "Hash SHA-256", CapabilityStatus.BUILT_IN),

        ToolCapability("process.exec", ToolFamily.PROCESS, "Ejecutar proceso corto", CapabilityStatus.BUILT_IN),
        ToolCapability("process.start", ToolFamily.PROCESS, "Iniciar proceso administrado", CapabilityStatus.BUILT_IN),
        ToolCapability("process.logs", ToolFamily.PROCESS, "Leer logs de un proceso", CapabilityStatus.BUILT_IN),
        ToolCapability("process.list", ToolFamily.PROCESS, "Listar procesos administrados", CapabilityStatus.BUILT_IN),
        ToolCapability("process.stop", ToolFamily.PROCESS, "Detener proceso administrado", CapabilityStatus.BUILT_IN),

        ToolCapability("http.get", ToolFamily.NETWORK, "HTTP(S) limitado por política", CapabilityStatus.BUILT_IN),

        ToolCapability("archive.zip", ToolFamily.ARCHIVE, "Comprimir workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("archive.unzip", ToolFamily.ARCHIVE, "Extraer ZIP bloqueando Zip Slip", CapabilityStatus.BUILT_IN),
        ToolCapability("artifact.zip", ToolFamily.ARCHIVE, "Crear ZIP exportable en artifacts", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.create", ToolFamily.ARCHIVE, "Crear checkpoint antes de cambios", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.list", ToolFamily.ARCHIVE, "Listar checkpoints", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.restore", ToolFamily.ARCHIVE, "Restaurar checkpoint", CapabilityStatus.BUILT_IN),

        ToolCapability("git.*", ToolFamily.VERSION_CONTROL, "Git local cuando el runtime tenga el binario", CapabilityStatus.READY_TO_ADAPT, "git/git"),
        ToolCapability("code.ast.*", ToolFamily.CODE_INTELLIGENCE, "AST incremental", CapabilityStatus.READY_TO_ADAPT, "tree-sitter/tree-sitter"),

        ToolCapability("android.device_info", ToolFamily.ANDROID, "Información del dispositivo", CapabilityStatus.BUILT_IN),
        ToolCapability("android.battery", ToolFamily.ANDROID, "Estado de batería", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.read", ToolFamily.ANDROID, "Leer portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.write", ToolFamily.ANDROID, "Escribir portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.api.*", ToolFamily.ANDROID, "Más APIs del teléfono", CapabilityStatus.READY_TO_ADAPT, "termux/termux-api"),
        ToolCapability("android.shizuku.*", ToolFamily.ANDROID, "Backend privilegiado vía Shizuku", CapabilityStatus.OPTIONAL_BACKEND, "RikkaApps/Shizuku"),
        ToolCapability("android.root.*", ToolFamily.ANDROID, "Backend root opcional", CapabilityStatus.OPTIONAL_BACKEND, "topjohnwu/libsu"),

        ToolCapability("sandbox.proot.*", ToolFamily.SANDBOX, "Linux PRoot por proyecto", CapabilityStatus.READY_TO_ADAPT, "termux/proot-distro"),

        ToolCapability("mcp.*", ToolFamily.CONNECTOR, "Descubrir y ejecutar herramientas MCP", CapabilityStatus.READY_TO_ADAPT, "modelcontextprotocol"),
        ToolCapability("browser.lightpanda.*", ToolFamily.CONNECTOR, "Navegador headless por MCP/CDP", CapabilityStatus.READY_TO_ADAPT, "lightpanda-io/browser"),
        ToolCapability("ssh.*", ToolFamily.CONNECTOR, "Delegación a otra máquina", CapabilityStatus.PLANNED)
    )
}
