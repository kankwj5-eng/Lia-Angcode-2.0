# Arquitectura inicial

## Capas

### 1. Android Shell
Interfaz principal, navegación, proyectos, permisos, artefactos y visualización de actividad.

### 2. Director
Convierte una misión del usuario en tareas, selecciona agentes lógicos y coordina resultados.

### 3. Scheduler
Decide qué trabajo puede ejecutarse según RAM, CPU, batería, temperatura y dependencias.

### 4. Agent Runtime
Mantiene contexto, memoria de trabajo, estado, eventos y herramientas disponibles para cada agente.

### 5. Tool Broker
Única puerta de ejecución. Valida permisos y convierte llamadas estructuradas en acciones reales.

### 6. Runtime Termux
Procesos nativos, shell, Git, Python, Node, compiladores y utilidades.

### 7. Sandbox Layer
PRoot/OCI para tareas que necesiten distribuciones Linux o glibc.

### 8. Android Bridge
APIs Android normales; Shizuku y root solo como niveles opcionales.

### 9. Browser Worker
Navegación automatizada con modo headless y modo visible/tomar-control.

### 10. Connector Layer
MCP, GitHub, SSH y futuros servicios.

## Celdas

Cada agente trabajador recibe una celda lógica:

```text
cell/
├── workspace/
├── memory/
├── logs/
├── processes/
├── artifacts/
└── permissions.json
```

Una celda puede corresponder a un `git worktree` separado cuando varios agentes modifiquen el mismo proyecto.

## Eventos

El runtime debe ser dirigido por eventos, no por conversación únicamente.

Ejemplos:

- `task.created`
- `process.started`
- `build.failed`
- `source.updated`
- `artifact.created`
- `browser.downloaded`
- `agent.completed`

## Tool Broker

Ejemplos de herramientas públicas al modelo:

```text
file.read
file.write
file.patch
file.search
shell.run
process.start
process.logs
process.stop
git.status
git.diff
git.commit
browser.open
browser.read
browser.click
browser.screenshot
android.build
artifact.export
```

La implementación interna puede cambiar sin cambiar el contrato presentado al modelo.

## Recursos móviles

No asumir recursos de PC. El scheduler debe:

- limitar concurrencia;
- poder pausar tareas;
- evitar cargar varios LLM si un solo modelo puede multiplexar roles;
- detectar memoria disponible;
- reducir trabajo en batería baja o alta temperatura;
- permitir que compilación y modelo no compitan innecesariamente.

## UI

La terminal es una herramienta secundaria. La pantalla principal debe mostrar proyectos, misión, tareas, agentes, estado y artefactos.

Tema inicial: negro/grafito, blanco y naranja.
