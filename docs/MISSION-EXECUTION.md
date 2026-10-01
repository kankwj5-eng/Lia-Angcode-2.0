# Ejecución local de misiones

El dashboard puede ejecutar una misión cuando existen:

- proyecto activo o `_scratch`;
- modelo GGUF activo;
- `llama-cli` descubierto en el runtime.

Antes de ejecutar se crea automáticamente un checkpoint `mission-start`.

El contexto del worker local recibe:

- lectura/escritura del workspace;
- procesos estructurados;
- red HTTP;
- Android Bridge básico.

No recibe automáticamente:

- `UNRESTRICTED_SHELL`;
- `PACKAGE_MANAGE`;
- `SANDBOX_MANAGE`;
- `WORKTREE_MANAGE`;
- root/Shizuku;
- gestión del modelo.

La primera versión usa un modelo para toda la misión y roles lógicos. Esto reduce RAM. El scheduler multiworker se activará encima de este camino cuando los recursos del teléfono lo permitan.
