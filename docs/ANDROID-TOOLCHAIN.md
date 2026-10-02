# Android toolchain ARM

El perfil `full` del runtime construye paquetes de Termux adaptados al prefix de AngCode:

- openjdk-21
- gradle
- aapt / aapt2
- d8 / r8
- apksigner
- android-tools (adb / fastboot)
- ecj / android.jar
- clang / cmake / ninja

Esto evita depender de binarios x86 del SDK de escritorio.

Herramientas estructuradas:

- `android.toolchain`
- `android.apk.badging`
- `android.apk.verify`
- `android.adb.devices`
- `android.adb.connect`
- `android.adb.logcat`
- `android.adb.install`

ADB requiere `ADB_REMOTE`; conectar a una IP privada requiere además `PRIVATE_NETWORK`. El worker normal no recibe esos permisos automáticamente.

Para el propio teléfono, Shizuku sigue siendo el camino preferido para logcat e instalación APK cuando está autorizado.
