# Catálogo de herramientas

AngCode no trata las herramientas como comandos sueltos. Cada capacidad tiene un ID estable, permisos y un backend.

## Integradas ahora

- `workspace.list` — lista archivos.
- `workspace.search` — busca texto sin depender de ripgrep.
- `file.read` — lectura con límite de tamaño.
- `file.write` — escritura confinada al workspace.
- `file.patch` — reemplazo exacto y no ambiguo.
- `file.sha256` — verificación de artefactos.
- `process.exec` — proceso estructurado sujeto a ExecutionPolicy.
- `archive.zip` — empaquetar resultados.
- `archive.unzip` — extracción protegida contra Zip Slip.
- `http.get` — HTTP/HTTPS con límite de tamaño y bloqueo de redes privadas por defecto.

## Siguientes backends

### Termux:API
Repositorio upstream: `termux/termux-api`.

Nos interesa por sus patrones para exponer batería, portapapeles, notificaciones, sensores, cámara, audio y otras APIs Android como herramientas estructuradas. AngCode integrará APIs equivalentes dentro de su propio Android Bridge; no queremos exigir una segunda app Termux:API para lo básico.

### PRoot / PRoot-Distro
Repositorios upstream: `proot-me/proot`, `termux/proot-distro`.

Será el backend de Linux aislado para Python, Node, compiladores y utilidades que esperan un userland GNU/Linux.

### Shizuku
Repositorio upstream: `RikkaApps/Shizuku`, Apache-2.0.

Backend opcional para operaciones que Android normal no concede pero ADB/Shizuku sí puede proporcionar. Debe activarse expresamente.

### libsu
Repositorio upstream: `topjohnwu/libsu`, Apache-2.0.

Backend opcional únicamente en teléfonos con root y con consentimiento explícito. Nunca será requisito del producto.

### Tree-sitter
Repositorio upstream: `tree-sitter/tree-sitter`, MIT.

Permitirá herramientas como búsqueda de símbolos, funciones/clases, navegación estructural y parches basados en AST. Esto reduce tokens porque el modelo puede pedir exactamente la estructura que necesita.

## Herramientas deseadas cuando exista el userland

- `git.status`, `git.diff`, `git.commit`, `git.worktree.*`
- `code.symbols`, `code.references`, `code.ast.query`
- `python.run`, `node.run`
- `android.gradle.build`, `android.apk.inspect`, `android.logcat`
- `browser.read`, `browser.click`, `browser.screenshot`, `browser.download`
- `mcp.list`, `mcp.call`
- `ssh.exec`
- `artifact.export`

## Regla de seguridad

Una herramienta no se vuelve segura solo por estar dentro de un contenedor. El Tool Broker sigue siendo la frontera lógica de permisos, rutas, red, tiempo y recursos.
