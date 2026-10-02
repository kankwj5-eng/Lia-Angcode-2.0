# Roadmap

Estado actualizado: AngCode ya supera el esqueleto de MVP. Esta lista diferencia código implementado de validación pendiente en hardware real.

## Fase 0 — Fundación
- [x] Repositorio público limpio
- [x] Arquitectura modular Android / runtime / agents / connectors
- [x] Reglas para agentes
- [x] Package/applicationId: `com.kankwj.angcode`
- [x] Estrategia Termux fork con prefix propio documentada
- [x] Auditoría de componentes upstream reutilizables

## Fase 1 — Android Shell
- [x] Proyecto Android moderno
- [x] Jetpack Compose
- [x] Tema Obsidian Orange
- [x] Dashboard
- [x] Navegación principal
- [x] Gestor de proyectos / proyecto activo
- [x] Gestor y exportación de artefactos
- [x] Terminal persistente controlada por el usuario
- [x] Ejecución de misión en primer plano
- [x] Foreground service para misiones largas

## Fase 2 — Runtime
- [x] Bootstrap AngCode reproducible basado en termux-packages
- [x] Prefix/package propios
- [x] Instalador staging + rollback + SHA-256
- [x] Descarga de Releases verificadas
- [x] Ejecución de comandos estructurados
- [x] Procesos observables/cancelables
- [x] stdin interactivo administrado
- [x] Workspace privado
- [x] Importar/exportar
- [x] Logs y auditoría
- [ ] Validar bootstrap aarch64 completo en hardware físico
- [ ] Publicar primera Release estable del runtime dev/full

## Fase 3 — Tool Broker
- [x] Registro dinámico de herramientas
- [x] IDs/contratos de entrada/salida
- [x] Permisos por herramienta
- [x] Timeouts y límites
- [x] Auditoría JSONL con redacción
- [x] Tool Packs
- [x] Descubrimiento de capacidades por el agente
- [x] Gestión de paquetes separada por permiso

## Fase 4 — Agentes
- [x] Director
- [x] Analizador/Planner local
- [x] Scheduler por RAM/batería/temperatura
- [x] Event Bus
- [x] Workers/celdas lógicas
- [x] Inferencia compartida para ahorrar RAM
- [x] Memoria por ejecución
- [x] Historial persistente
- [x] Reanudación de misiones incompletas
- [x] Checkpoints
- [x] Worktrees Git por misión
- [x] Review/merge administrado
- [x] Cancelación cooperativa y de procesos

## Fase 5 — Desarrollo real
- [x] Git
- [x] Python / pytest
- [x] Node / npm
- [x] Clang / CMake / Ninja
- [x] Java / Gradle
- [x] AAPT / AAPT2
- [x] D8 / R8
- [x] apksigner
- [x] ADB / fastboot
- [x] Compilar tareas Android por Gradle
- [x] Inspeccionar/verificar APK
- [x] Instalar APK vía Shizuku
- [x] Logcat vía Shizuku
- [x] Instalar/logcat vía ADB con permiso dedicado
- [ ] Validar build Android completo end-to-end en teléfono ARM

## Fase 6 — Browser
- [x] Navegador visible WebView
- [x] Runtime Lightpanda verificado
- [x] Lightpanda dentro de PRoot aislado
- [x] MCP local
- [x] Herramientas `browser.*` para el agente
- [x] Conexión automática a misiones Browser/Web
- [x] Tomar/devolver control mediante superficies separadas
- [ ] Prueba end-to-end en dispositivo con páginas complejas

## Fase 7 — Conectores
- [x] Cliente MCP oficial Kotlin
- [x] Compatibilidad MCP legacy
- [x] Servidores MCP locales
- [x] GitHub read-only con token cifrado en Keystore
- [x] SSH/SCP estricto
- [x] Delegación a otra PC por SSH
- [ ] GitHub write actions como permiso independiente (opcional, post-MVP)

## Fase 8 — Android avanzado
- [x] Android Bridge: batería/RAM/storage/thermal
- [x] Clipboard
- [x] Red/sensores
- [x] APK inspection
- [x] Acciones UI visibles y permission-gated
- [x] Shizuku opcional
- [x] Shizuku: package info / launch / logcat / APK install / screenshot
- [ ] Root/libsu opcional
- [ ] Watchers/automatizaciones persistentes (post-MVP)

## Fase 9 — Modelo local
- [x] Importación GGUF validada
- [x] SHA-256 local
- [x] Modelo activo
- [x] llama.cpp Tool Pack
- [x] llama-cli fallback
- [x] llama-server persistente
- [x] Tool calling JSON → Tool Broker
- [x] Cancelación de inferencia/servidor
- [ ] Perfiles automáticos de contexto/hilos según RAM y SoC

## Fase 10 — Pulido final
- [ ] Chat/actividad refinados
- [ ] Estados visuales de workers
- [ ] Accesibilidad y tamaños de pantalla
- [ ] Mascota oficial: gatito + elementos verdes/turquesa
- [ ] Animación cola/cabeza/manos/pececito
- [ ] Mascota flotante sobre chat durante trabajo
- [ ] QA visual y de rendimiento

## Criterio de MVP

El MVP de código ya cubre:

1. crear/importar proyecto;
2. ejecutar comandos y herramientas dentro de workspace;
3. mostrar/cancelar procesos y logs;
4. ejecutar un modelo GGUF local con herramientas estructuradas;
5. trabajar con agentes, checkpoints y worktrees;
6. usar navegador headless/visible;
7. producir/exportar artefactos;
8. conservar historial/auditoría y reanudar misiones;
9. continuar misiones largas mediante foreground service.

Los bloqueos restantes para declarar una versión instalable estable son validación end-to-end del runtime aarch64 y pruebas en hardware Android real.
