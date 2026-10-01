# Herramientas de desarrollo estructuradas

Los Tool Packs ya tienen implementaciones reales para una primera tanda de IDs:

- `python.run` y `python.test`
- `node.run` y `npm.run`
- `json.query`
- `sqlite.query` (solo lectura)
- `clang.build`
- `cmake.configure`
- `ninja.build`
- `android.build`

Las herramientas no buscan ejecutables arbitrariamente. Usan únicamente rutas presentes en `ToolContext.executables`, alimentadas por `ExecutableDiscovery`.

Las rutas de entrada/salida pasan por `safeWorkspaceFile`. Los argumentos adicionales se transmiten como listas, no como cadenas de shell.

Esto reduce la necesidad de dar `UNRESTRICTED_SHELL` a los agentes y hace que los permisos sean auditables.
