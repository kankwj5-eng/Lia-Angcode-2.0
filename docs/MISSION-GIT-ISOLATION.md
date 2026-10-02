# Aislamiento Git por misión

Las misiones que modifican código intentan trabajar en un worktree aislado.

Condiciones para activarlo:

- Git descubierto en el runtime;
- permiso administrativo `WORKTREE_MANAGE` en el contexto del Director;
- workspace reconocido como repositorio Git;
- árbol principal limpio.

Si hay cambios locales sin commit, AngCode **no** crea un worktree desde HEAD porque eso excluiría esos cambios. Continúa sobre el workspace normal y conserva el checkpoint de inicio.

Cuando el aislamiento está activo:

- Research/Browser leen el workspace principal.
- Coder/Tester/Builder usan el mismo worktree de misión.
- el LLM nunca recibe `WORKTREE_MANAGE`;
- al terminar con éxito se hace staging + commit técnico local;
- el Director obtiene `git.review`;
- solo entonces ejecuta `git.merge`;
- el worktree se retira después de un merge correcto.

Si la misión falla, el worktree se conserva para inspección/recuperación.
