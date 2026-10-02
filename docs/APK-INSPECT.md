# Inspección APK

`android.apk.inspect` usa `PackageManager.getPackageArchiveInfo` sobre un APK dentro del workspace.

Devuelve:

- package name;
- versionName/versionCode;
- minSdk/targetSdk;
- número de activities/services/receivers/providers;
- permisos solicitados;
- tamaño del APK.

No instala ni ejecuta el paquete.
