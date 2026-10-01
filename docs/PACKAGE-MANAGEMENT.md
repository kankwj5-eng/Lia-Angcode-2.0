# Gestión de paquetes

AngCode separa ejecutar procesos de instalar software.

`package.install` requiere simultáneamente:

- `PROCESS_EXECUTE`
- `NETWORK`
- `PACKAGE_MANAGE`

Esto evita que un agente que solo necesita compilar pueda instalar paquetes arbitrariamente.

El backend actual detecta `apt-get` o `apt` en `ToolContext.executables`. Si no existe, la herramienta devuelve un estado de backend no disponible en lugar de intentar modificar Android.

`package.list` usa `dpkg-query` si el userland lo proporciona.

La lista de ejecutables descubiertos incluye ahora apt, dpkg-query, PRoot, bash, make, tar y unzip además de Git, Python, Node, Java, Clang, CMake, Ninja, ripgrep, jq, SQLite, FFmpeg, SSH y curl.
