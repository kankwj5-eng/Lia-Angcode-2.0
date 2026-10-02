# Grants elevados por misión

Los permisos sensibles de interacción externa están apagados por defecto y se aprueban desde el panel de la misión.

Grants efímeros disponibles:

- portapapeles;
- acciones Android visibles;
- Shizuku avanzado;
- ADB remoto;
- SSH remoto.

La selección vive solo en el estado de esa misión. `LocalMissionExecutor` vuelve a filtrar el set contra una allowlist; la UI no puede inyectar permisos administrativos no autorizados.

Nunca aparecen en esta lista:

- `UNRESTRICTED_SHELL`;
- `PACKAGE_MANAGE`;
- `SANDBOX_MANAGE`;
- `MODEL_MANAGE`;
- `WORKTREE_MANAGE` (este último es interno del Director).

Los perfiles de workers usan `constrainedTo()`, por lo que una capacidad elevada solo llega al rol si **también** fue concedida por el contexto padre.
