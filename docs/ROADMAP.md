# Roadmap

Estado: núcleo funcional en construcción avanzada. Los checks reflejan código integrado y compilado en CI; no sustituyen la validación final en un teléfono físico.

## Fase 0 — Fundación
- [x] Repositorio público limpio
- [x] Arquitectura inicial
- [x] Reglas para agentes
- [x] Separación de módulos
- [x] Auditoría selectiva de Termux (bootstrap, ejecución, paths, servicios y estrategia)
- [x] Package/applicationId: `com.kankwj.angcode`

## Fase 1 — Android Shell
- [x] Proyecto Android/Gradle moderno
- [x] Jetpack Compose
- [x] Tema Obsidian Orange
- [x] Dashboard
- [x] Navegación principal
- [x] Gestor de proyectos + proyecto activo
- [x] Gestor/exportador de artefactos
- [x] Ajustes de runtime/modelo/conectores

## Fase 2 — Runtime
- [x] Bootstrap forked para el prefix de AngCode
- [x] Instalador staging + SHA-256 + symlinks + rollback
- [x] Ejecución estructurada de comandos
- [x] Procesos observables/cancelables
- [x] Procesos persistentes con stdin interactivo
- [x] Sistema de archivos de workspace
- [x] Importar/exportar
- [x] Logs estructurados
- [x] Descubrimiento de ejecutables
- [x] Build reproducible por ABI/perfil
- [x] Soporte para publicar runtime verificado en GitHub Releases
- [x] Descarga/instalación de Runtime Releases desde la app
- [ ] Publicar y validar la primera Release runtime `dev/aarch64` en un teléfono

## Fase 3 — Tool Broker
- [x] Registro de herramientas
- [x] Contratos de entrada/salida
- [x] Permisos por herramienta
- [x] Timeouts/límites
- [x] Auditoría redactada
- [x] Tool Packs
- [x] Instalación de paquetes separada de PROCESS_EXECUTE
- [x] Catálogo consultable por agentes
- [x] MCP dinámico

## Fase 4 — Agentes
- [x] Director
- [x] Planner local determinista
- [x] Scheduler sensible a RAM/batería/temperatura
- [x] Event Bus
- [x] Celdas
- [x] Memoria de ejecución
- [x] Checkpoints
- [x] Worktrees Git
- [x] Review + merge
- [x] Multiworker con inferencia compartida
- [x] Cancelación cooperativa
- [x] Historial versionado y reanudación
- [x] Servicio foreground para misiones largas
- [x] Aislamiento automático de trabajo mutable cuando Git lo permite

## Fase 5 — Desarrollo real
- [x] Git
- [x] Python / pytest
- [x] Node / npm
- [x] Clang/CMake/Ninja
- [x] Java/Gradle
- [x] Toolchain Android ARM
- [x] Compilar APK
- [x] Inspeccionar/verificar APK
- [x] ADB devices/connect/install/logcat
- [x] SQLite / JSON
- [x] FFmpeg / ImageMagick
- [x] SSH exec/upload/download
- [ ] Validación end-to-end de build APK dentro del runtime publicado en teléfono real

## Fase 6 — Browser
- [x] Navegador visible WebView para el usuario
- [x] Lightpanda oficial verificado por SHA-256
- [x] Lightpanda dentro de PRoot aislado
- [x] MCP local en 127.0.0.1
- [x] Registro dinámico `browser.*`
- [x] DOM/acciones según herramientas MCP expuestas por Lightpanda
- [x] Argumentos MCP estructurados (objetos/listas/bool/números)
- [x] Instalación/inicio/parada desde UI
- [ ] Persistir screenshots/downloads MCP directamente como artifacts cuando el servidor devuelva contenido binario
- [ ] Handoff opcional de una sesión agentica a una vista visible equivalente

## Fase 7 — Conectores
- [x] Cliente MCP oficial Kotlin + fallback legacy
- [x] Servidores MCP locales
- [x] GitHub read-only con token cifrado y permiso dedicado
- [x] SSH con known_hosts estricto
- [x] Bridge remoto genérico mediante SSH
- [ ] Escrituras GitHub de alto riesgo detrás de permisos/confirmaciones separadas

## Fase 8 — Android avanzado
- [x] Android Bridge básico
- [x] Batería/RAM/almacenamiento/térmica/red/sensores
- [x] Portapapeles y acciones visibles
- [x] Inspección APK
- [x] Shizuku opcional
- [x] Screenshot privilegiado Shizuku a artifacts
- [ ] Expandir equivalentes de Termux:API donde aporten valor
- [ ] Root/libsu opcional
- [ ] Watchers/automatizaciones locales de larga duración

## Modelo local
- [x] Importación GGUF
- [x] Validación cabecera + SHA-256
- [x] Modelo activo
- [x] Tool Pack `local-llm`
- [x] `llama-cli` fallback
- [x] `llama-server` persistente
- [x] Protocolo JSON tool/final
- [x] Tool calls siempre atraviesan Tool Broker
- [x] Un servidor/modelo compartido por workers lógicos

## Criterio de MVP

### Implementado en código
- [x] Crear/importar proyecto
- [x] Ejecutar comandos dentro de workspace
- [x] Mostrar procesos/logs
- [x] Modelo local con herramientas estructuradas
- [x] Producir/exportar artefactos
- [x] Historial + checkpoints
- [x] Reanudar misión incompleta
- [x] Navegador agentico
- [x] Runtime instalable/verificable

### Gate de entrega
- [ ] Runtime `dev/aarch64` termina CI y se publica
- [ ] Instalar runtime desde AngCode en ARM64 real
- [ ] Importar GGUF pequeño y completar misión con tool call real
- [ ] Compilar un proyecto de prueba y exportar su APK
- [ ] Ejecutar navegación Lightpanda real en el teléfono
- [ ] Regression pass final
- [ ] Capa estética final: mascota gatito animada sobre el chat
