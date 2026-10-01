# Runtime

Capa responsable de ejecutar trabajo real.

Debe encapsular:

- bootstrap;
- shell;
- procesos;
- pseudo-terminales cuando hagan falta;
- filesystem de workspaces;
- PRoot/OCI;
- límites de recursos;
- importación/exportación;
- logs estructurados.

El objetivo es reutilizar únicamente las partes necesarias del runtime Termux en vez de arrastrar su interfaz completa.
