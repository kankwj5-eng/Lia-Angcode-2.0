# Provisionamiento de celdas

`AgentCellProvisioner` conecta el Director con los recursos reales.

Una solicitud de celda puede crear:

1. rama `angcode/<celda>`;
2. worktree Git separado;
3. sandbox PRoot opcional;
4. `ToolContext` cuya raíz es el worktree;
5. permisos reducidos según el rol.

Los permisos del hijo nunca superan los permisos ya concedidos al contexto padre.

Ejemplo:

- Coder: lectura + escritura + procesos.
- Researcher: lectura + red + MCP.
- Tester: lectura + procesos.
- Browser: lectura + red + MCP.

El Director conserva permisos administrativos como `WORKTREE_MANAGE` y `SANDBOX_MANAGE`; esos permisos no se entregan automáticamente a los workers.

Si falla la creación del sandbox después del worktree, el provisioner intenta retirar el worktree para no dejar una celda a medias.
