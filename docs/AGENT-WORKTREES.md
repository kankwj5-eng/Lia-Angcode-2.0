# Worktrees por agente

Los agentes que escriben código pueden trabajar en árboles Git separados.

Herramientas:

- `git.worktree.list`
- `git.worktree.create`
- `git.worktree.remove`

Crear o retirar worktrees exige `WORKTREE_MANAGE`.

Los worktrees administrados se guardan como hermanos del workspace principal bajo:

`.angcode-worktrees/<proyecto>/<celda>/`

y cada celda recibe por defecto una rama:

`angcode/<celda>`

Después de crear el worktree, la respuesta incluye `worktree` y `branch` en metadata. El Director puede construir un nuevo `ToolContext` cuya raíz sea ese worktree y entregar ese contexto únicamente al agente correspondiente.

`AgentCell` ya dispone de campos opcionales `workspacePath`, `branch` y `sandbox` para enlazar la celda con su entorno real.
