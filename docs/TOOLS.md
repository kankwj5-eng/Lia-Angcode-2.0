# Herramientas del agente

AngCode expone capacidades pequeñas y componibles. Un agente recibe herramientas concretas, no acceso total al teléfono.

## Archivos y código
- `workspace.list`
- `file.read`
- `file.write`
- `file.patch`
- `file.delete`
- `file.sha256`
- `code.search`

## Procesos
- `process.exec`
- `process.start`
- `process.logs`
- `process.stop`

La shell arbitraria sigue separada detrás de `UNRESTRICTED_SHELL`.

## Git
- `git.status`
- `git.diff`
- `git.log`
- `git.add`
- `git.commit`

Git se resuelve desde el registro de ejecutables del runtime; Android no se asume que lo tenga preinstalado.

## Red
- `http.get`

Las direcciones loopback, link-local y red privada requieren además `PRIVATE_NETWORK`.

## Artefactos
- `artifact.zip`

## Android
- `android.device_info`

## Siguiente expansión

Tool Packs previstos: Android SDK/Gradle, Python, Node/TypeScript, clang/CMake/Ninja, SQLite/jq/ripgrep, ffmpeg/ImageMagick, SSH/SFTP, MCP y browser worker automatizado.
