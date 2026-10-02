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
        ToolCapability("audit.tail", ToolFamily.CONNECTOR, "Inspeccionar auditoría reciente", CapabilityStatus.BUILT_IN),
        ToolCapability("toolpack.list", ToolFamily.CONNECTOR, "Listar packs incluidos", CapabilityStatus.BUILT_IN),
        ToolCapability("toolpack.inspect", ToolFamily.CONNECTOR, "Inspeccionar requisitos de un pack", CapabilityStatus.BUILT_IN),
        ToolCapability("package.list", ToolFamily.SANDBOX, "Listar paquetes instalados", CapabilityStatus.BUILT_IN),
        ToolCapability("package.install", ToolFamily.SANDBOX, "Instalar paquetes aprobados", CapabilityStatus.BUILT_IN),

        ToolCapability("http.get", ToolFamily.NETWORK, "HTTP(S) limitado por política", CapabilityStatus.BUILT_IN),
        ToolCapability("preview.probe", ToolFamily.NETWORK, "Inspeccionar preview localhost", CapabilityStatus.BUILT_IN),
        ToolCapability("preview.scan", ToolFamily.NETWORK, "Detectar servidores de desarrollo localhost", CapabilityStatus.BUILT_IN),

        ToolCapability("archive.zip", ToolFamily.ARCHIVE, "Comprimir workspace", CapabilityStatus.BUILT_IN),
        ToolCapability("archive.unzip", ToolFamily.ARCHIVE, "Extraer ZIP bloqueando Zip Slip", CapabilityStatus.BUILT_IN),
        ToolCapability("artifact.zip", ToolFamily.ARCHIVE, "Crear ZIP exportable en artifacts", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.create", ToolFamily.ARCHIVE, "Crear checkpoint antes de cambios", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.list", ToolFamily.ARCHIVE, "Listar checkpoints", CapabilityStatus.BUILT_IN),
        ToolCapability("checkpoint.restore", ToolFamily.ARCHIVE, "Restaurar checkpoint", CapabilityStatus.BUILT_IN),

        ToolCapability("git.*", ToolFamily.VERSION_CONTROL, "Git local estructurado", CapabilityStatus.BUILT_IN, "git/git"),
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
        ToolCapability("android.toolchain", ToolFamily.CODE_INTELLIGENCE, "Inspeccionar toolchain Android ARM", CapabilityStatus.BUILT_IN),
        ToolCapability("android.apk.badging", ToolFamily.CODE_INTELLIGENCE, "Inspeccionar badging/manifest APK", CapabilityStatus.BUILT_IN),
        ToolCapability("android.apk.verify", ToolFamily.CODE_INTELLIGENCE, "Verificar firma APK", CapabilityStatus.BUILT_IN),
        ToolCapability("android.adb.devices", ToolFamily.ANDROID, "Listar dispositivos ADB", CapabilityStatus.BUILT_IN),
        ToolCapability("android.adb.connect", ToolFamily.ANDROID, "Conectar ADB remoto", CapabilityStatus.BUILT_IN),
        ToolCapability("android.adb.logcat", ToolFamily.ANDROID, "Leer logcat por ADB", CapabilityStatus.BUILT_IN),
        ToolCapability("android.adb.install", ToolFamily.ANDROID, "Instalar APK por ADB", CapabilityStatus.BUILT_IN),
        ToolCapability("media.probe", ToolFamily.CODE_INTELLIGENCE, "Inspeccionar audio/video", CapabilityStatus.BUILT_IN),
        ToolCapability("media.convert", ToolFamily.CODE_INTELLIGENCE, "Convertir audio/video con FFmpeg", CapabilityStatus.BUILT_IN),
        ToolCapability("image.convert", ToolFamily.CODE_INTELLIGENCE, "Transformar imágenes con ImageMagick", CapabilityStatus.BUILT_IN),

        ToolCapability("android.device_info", ToolFamily.ANDROID, "Información del dispositivo", CapabilityStatus.BUILT_IN),
        ToolCapability("android.battery", ToolFamily.ANDROID, "Estado de batería", CapabilityStatus.BUILT_IN),
        ToolCapability("android.memory", ToolFamily.ANDROID, "RAM disponible/total", CapabilityStatus.BUILT_IN),
        ToolCapability("android.storage", ToolFamily.ANDROID, "Almacenamiento del runtime", CapabilityStatus.BUILT_IN),
        ToolCapability("android.thermal", ToolFamily.ANDROID, "Estado térmico del dispositivo", CapabilityStatus.BUILT_IN),
        ToolCapability("android.apk.inspect", ToolFamily.ANDROID, "Inspeccionar metadata APK sin instalar", CapabilityStatus.BUILT_IN),
        ToolCapability("android.network", ToolFamily.ANDROID, "Conectividad y transporte de red", CapabilityStatus.BUILT_IN),
        ToolCapability("android.sensors.list", ToolFamily.ANDROID, "Sensores físicos disponibles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.read", ToolFamily.ANDROID, "Leer portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.clipboard.write", ToolFamily.ANDROID, "Escribir portapapeles", CapabilityStatus.BUILT_IN),
        ToolCapability("android.open_url", ToolFamily.ANDROID, "Abrir URL de forma visible", CapabilityStatus.BUILT_IN),
        ToolCapability("android.share_text", ToolFamily.ANDROID, "Compartir texto con selector Android", CapabilityStatus.BUILT_IN),
        ToolCapability("android.launch_app", ToolFamily.ANDROID, "Abrir app por package", CapabilityStatus.BUILT_IN),
        ToolCapability("android.api.*", ToolFamily.ANDROID, "Más APIs del teléfono", CapabilityStatus.READY_TO_ADAPT, "termux/termux-api"),
        ToolCapability("android.shizuku.*", ToolFamily.ANDROID, "Backend privilegiado vía Shizuku", CapabilityStatus.OPTIONAL_BACKEND, "RikkaApps/Shizuku"),
        ToolCapability("android.shizuku.screenshot", ToolFamily.ANDROID, "Captura PNG privilegiada a artifacts", CapabilityStatus.OPTIONAL_BACKEND, "RikkaApps/Shizuku"),
        ToolCapability("android.root.*", ToolFamily.ANDROID, "Backend root opcional", CapabilityStatus.OPTIONAL_BACKEND, "topjohnwu/libsu"),

        ToolCapability("sandbox.*", ToolFamily.SANDBOX, "Linux PRoot administrado por proyecto", CapabilityStatus.BUILT_IN, "termux/proot-distro"),

        ToolCapability("mcp.*", ToolFamily.CONNECTOR, "Herramientas MCP dinámicas", CapabilityStatus.BUILT_IN, "modelcontextprotocol/kotlin-sdk"),
        ToolCapability("browser.*", ToolFamily.CONNECTOR, "Navegador headless Lightpanda por MCP", CapabilityStatus.BUILT_IN, "lightpanda-io/browser"),
        ToolCapability("ssh.exec", ToolFamily.CONNECTOR, "Ejecutar comando remoto con host key estricta", CapabilityStatus.BUILT_IN, "openssh/openssh-portable"),
        ToolCapability("ssh.upload", ToolFamily.CONNECTOR, "Subir archivo por SCP", CapabilityStatus.BUILT_IN, "openssh/openssh-portable"),
        ToolCapability("ssh.download", ToolFamily.CONNECTOR, "Descargar archivo por SCP", CapabilityStatus.BUILT_IN, "openssh/openssh-portable"),
        ToolCapability("github.repo", ToolFamily.CONNECTOR, "Metadata de repositorio GitHub", CapabilityStatus.BUILT_IN, "github/rest-api-description"),
        ToolCapability("github.issues", ToolFamily.CONNECTOR, "Issues GitHub", CapabilityStatus.BUILT_IN, "github/rest-api-description"),
        ToolCapability("github.pulls", ToolFamily.CONNECTOR, "Pull requests GitHub", CapabilityStatus.BUILT_IN, "github/rest-api-description"),
        ToolCapability("github.branches", ToolFamily.CONNECTOR, "Ramas GitHub", CapabilityStatus.BUILT_IN, "github/rest-api-description"),
        ToolCapability("github.actions", ToolFamily.CONNECTOR, "GitHub Actions recientes", CapabilityStatus.BUILT_IN, "github/rest-api-description")
    )
}
