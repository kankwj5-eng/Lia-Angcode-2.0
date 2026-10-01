# Instalador del runtime

`RuntimeBootstrapInstaller` adapta el patrón de instalación de `TermuxInstaller` del snapshot upstream usado por el proyecto, pero añade verificación SHA-256 antes de tocar el runtime instalado.

Flujo:

1. copiar el ZIP a cache y calcular SHA-256;
2. rechazarlo si no coincide con el hash esperado;
3. extraer a `files/usr-staging`;
4. rechazar rutas absolutas o Zip Slip;
5. recopilar `SYMLINKS.txt`;
6. aplicar permisos de ejecución a binarios/libexec/métodos apt;
7. recrear symlinks;
8. mover el runtime anterior a backup;
9. activar el staging con rename;
10. borrar el backup solo después de activar correctamente.

El prefix final es:

`/data/data/com.kankwj.angcode/files/usr`

(en dispositivos Android puede aparecer internamente como la ruta equivalente bajo `/data/user/0`; el alias `/data/data` sigue siendo el path compilado para el fork).

Después de instalar, `ExecutableDiscovery.forApp()` detecta automáticamente herramientas bajo `files/usr/bin`.
