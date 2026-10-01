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
        ToolCapability("runtime.executables", ToolFamily.PROCESS, "Detectar herramientas del runtime", CapabilityStatus.BUILT_IN),
        ToolCapability("model.list", ToolFamily.CONNECTOR, "Listar modelos GGUF locales", CapabilityStatus.BUILT_IN),
        ToolCapability("model.activate", ToolFamily.CONNECTOR, "Activar modelo local", CapabilityStatus.BUILT_IN),
        ToolCapability("tool.catalog", ToolFamily.CONNECTOR, "Consultar catálogo de capacidades", CapabilityStatus.BUILT_IN),
        ToolCapability("toolpack.list", ToolFamily.CONNECTOR, "Listar packs incluidos", CapabilityStatus.BUILT_IN),
        ToolCapability("toolpack.inspect", ToolFamily.CONNECTOR, "Inspeccionar requisitos de un pack", CapabilityStatus.BUILT_IN),
        ToolCapability("package.list", ToolFamily.SANDBOX, "Listar paquetes instalados", CapabilityStatus.BUILT_IN),
        ToolCapability("package.install", ToolFamily.SANDBOX, "Instalar paquetes aprobados", CapabilityStatus.BUILT_IN),

        ToolCapability("http.get", ToolFamily.NETWORK, "HTTP(S) limitado por política", CapabilityStatus.BUILT_IN),

        ToolCapability("archive.zip", ToolFamily.ARCHIVE, "Comprimir workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("archive.unzip", ToolFamily.ARCHIVE, "Extraer ZIP bloqueando Zip Slip", CapabilityStatus.BUILT_IN),
        ToolCapability("artifact.zip", ToolFamily.ARCHIVE, "Crear ZIP exportable en artifacts", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.create", ToolFamily.ARCHIVE, "Crear checkpoint antes de cambios", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.list", ToolFamily.ARCHIVE, "Listar checkpoints", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.restore", ToolFamily.ARCHIVE, "Restaurar checkpoint", CapabilityStatus.BUILT_IN),

        ToolCapability("git.*", ToolFamily.VERSION_CONTROL, "Git local cuando el runtime tenga el binario", CapabilityStatus.READY_TO_ADAPT, "git/git"),
        ToolCapability("code.ast.parse", ToolFamily.CODE_INTELLIGENCE, "AST con Tree-sitter cuando hay gramática", CapabilityStatus.BUILT_IN, "tree-sitter/tree-sitter"),
        ToolCapability("python.run", ToolFamily.CODE_INTELLIGENCE, "Ejecutar script/código Python", CapabilityStatus.BUILT_IN),
        ToolCapability("python.test", ToolFamily.CODE_INTELLIGENCE, "Ejecutar pytest", CapabilityStatus.BUILT_IN),
        ToolCapability("node.run", ToolFamily.CODE_INTELLIGENCE, "Ejecutar JS/TS con Node", CapabilityStatus.BUILT_IN),
        ToolCapability("npm.run", ToolFamily.CODE_INTELLIGENCE, "Ejecutar script npm", CapabilityStatus.BUILT_IN),
        ToolCapability("json.query", ToolFamily.CODE_INTELLIGENCE, "Consultar JSON local", CapabilityStatus.BUILT_IN),
        ToolCapability("sqlite.query", ToolFamily.CODE_INTELLIGENCE, "Consulta SQLite de solo lectura", CapabilityStatus.BUILT_IN),
        ToolCapability("clang.build", ToolFamily.CODE_INTELLIGENCE, "Compilar C/C++ con Clang", CapabilityStatus.BUILT_IN),
        ToolCapability("cmake.configure", ToolFamily.CODE_INTELLIGENCE, "Configurar proyecto CMake", CapabilityStatus.BUILT_IN),
        ToolCapability("ninja.build", ToolFamily.CODE_INTELLIGENCE, "Construir con Ninja", CapabilityStatus.BUILT_IN),
        ToolCapability("android.build", ToolFamily.CODE_INTELLIGENCE, "Ejecutar tarea Gradle Android", CapabilityStatus.BUILT_IN),
        ToolCapability("media.probe", ToolFamily.CODE_INTELLIGENCE, "Inspeccionar audio/video", CapabilityStatus.BUILT_IN),
        ToolCapability("media.convert", ToolFamily.CODE_INTELLIGENCE, "Convertir audio/video con FFmpeg", CapabilityStatus.BUILT_IN),
        ToolCapability("image.convert", ToolFamily.CODE_INTELLIGENCE, "Transformar imágenes con ImageMagick", CapabilityStatus.BUILT_IN),

        ToolCapability("android.device_info", ToolFamily.ANDROID, "Información del dispositivo", CapabilityStatus.BUILT_IN),
        ToolCapability("android.battery", ToolFamily.ANDROID, "Estado de batería", CapabilityStatus.BUILT_IN),
        ToolCapability("android.memory", ToolFamily.ANDROID, "RAM disponible/total", CapabilityStatus.BUILT_IN),
        ToolCapability("android.storage", ToolFamily.ANDROID, "Almacenamiento del runtime", CapabilityStatus.BUILT_IN),
        ToolCapability("android.thermal", ToolFamily.ANDROID, "Estado térmico del dispositivo", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.read", ToolFamily.ANDROID, "Leer portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.write", ToolFamily.ANDROID, "Escribir portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.api.*", ToolFamily.ANDROID, "Más APIs del teléfono", CapabilityStatus.READY_TO_ADAPT, "termux/termux-api"),
        ToolCapability("android.shizuku.*", ToolFamily.ANDROID, "Backend privilegiado vía Shizuku", CapabilityStatus.OPTIONAL_BACKEND, "RikkaApps/Shizuku"),
        ToolCapability("android.root.*", ToolFamily.ANDROID, "Backend root opcional", CapabilityStatus.OPTIONAL_BACKEND, "topjohnwu/libsu"),

        ToolCapability("sandbox.*", ToolFamily.SANDBOX, "Linux PRoot administrado por proyecto", CapabilityStatus.BUILT_IN, "termux/proot-distro"),

        ToolCapability("mcp.*", ToolFamily.CONNECTOR, "Herramientas MCP dinámicas", CapabilityStatus.READY_TO_ADAPT, "modelcontextprotocol/kotlin-sdk"),
        ToolCapability("browser.lightpanda.*", ToolFamily.CONNECTOR, "Navegador headless por MCP/CDP", CapabilityStatus.READY_TO_ADAPT, "lightpanda-io/browser"),
        ToolCapability("ssh.*", ToolFamily.CONNECTOR, "Delegación a otra máquina", CapabilityStatus.PLANNED)
    )
}
