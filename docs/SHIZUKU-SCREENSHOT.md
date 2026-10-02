# Captura de pantalla con Shizuku

La herramienta `android.shizuku.screenshot` usa el UserService privilegiado para ejecutar `screencap -p`.

El flujo binario no pasa por strings AIDL:

1. AngCode crea un archivo en `artifacts/`.
2. Abre un `ParcelFileDescriptor` de escritura.
3. El UserService copia stdout binario de `screencap -p` al descriptor.
4. AngCode valida los 8 bytes de firma PNG.
5. Solo devuelve al agente la ruta relativa y el tamaño.

Requiere `SHIZUKU_PRIVILEGED` + `WORKSPACE_WRITE`.
