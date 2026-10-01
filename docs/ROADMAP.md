# Roadmap

## Fase 0 — Fundación
- [x] Repositorio público limpio
- [x] Arquitectura inicial
- [x] Reglas para agentes
- [x] Separación de módulos
- [ ] Auditoría del código Termux a reutilizar
- [ ] Decidir package/applicationId definitivo

## Fase 1 — Android Shell
- [ ] Proyecto Android moderno
- [ ] Jetpack Compose
- [ ] Tema Obsidian Orange
- [ ] Dashboard
- [ ] Navegación principal
- [ ] Gestor de proyectos
- [ ] Gestor de artefactos

## Fase 2 — Runtime
- [ ] Bootstrap controlado
- [ ] Ejecución de comandos
- [ ] Procesos observables/cancelables
- [ ] Sistema de archivos de workspace
- [ ] Importar/exportar
- [ ] Logs estructurados

## Fase 3 — Tool Broker
- [ ] Registro de herramientas
- [ ] Esquemas de entrada/salida
- [ ] Permisos por herramienta
- [ ] Timeouts y límites
- [ ] Auditoría
- [ ] Tool Packs

## Fase 4 — Agentes
- [ ] Director
- [ ] Planner
- [ ] Scheduler
- [ ] Event Bus
- [ ] Celdas
- [ ] Memoria por proyecto
- [ ] Checkpoints
- [ ] Worktrees Git

## Fase 5 — Desarrollo real
- [ ] Git
- [ ] Python
- [ ] Node
- [ ] Clang/CMake/Ninja
- [ ] Java/Gradle
- [ ] Android SDK
- [ ] Compilar APK
- [ ] Instalar/probar APK
- [ ] Logcat

## Fase 6 — Browser
- [ ] Automatización headless
- [ ] DOM/acciones
- [ ] Descargas
- [ ] Screenshots
- [ ] Navegador visible
- [ ] Tomar/devolver control

## Fase 7 — Conectores
- [ ] Cliente MCP
- [ ] Servidores MCP locales
- [ ] GitHub
- [ ] SSH
- [ ] Bridge a otra PC/GPU

## Fase 8 — Android avanzado
- [ ] Termux:API compatible/integrado
- [ ] Shizuku opcional
- [ ] Root opcional
- [ ] Automatizaciones y watchers

## Criterio de MVP

El MVP debe poder:

1. crear/importar un proyecto;
2. ejecutar comandos dentro de un workspace;
3. mostrar procesos y logs;
4. permitir que un modelo local use herramientas estructuradas;
5. producir y exportar un artefacto;
6. conservar historial y checkpoint del trabajo.
