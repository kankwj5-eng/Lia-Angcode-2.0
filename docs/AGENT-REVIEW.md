# Revisión y merge de agentes

Una celda no se fusiona automáticamente por terminar una tarea.

El Director dispone de:

- `git.review` — obtiene stat + diff entre la rama de la celda y una base.
- `git.merge` — fusiona la rama sin abrir editor.

`git.merge` requiere `WORKTREE_MANAGE` además de lectura/escritura.

Flujo recomendado:

1. worker modifica y prueba en su worktree;
2. worker crea commit;
3. reviewer usa `git.review`;
4. tester vuelve a ejecutar pruebas si corresponde;
5. Director llama `git.merge`;
6. solo después se retira worktree/sandbox.

Esto mantiene separación entre producir cambios y aceptarlos.
