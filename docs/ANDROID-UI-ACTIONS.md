# Android UI actions

AngCode incorpora acciones Android visibles detrás del permiso lógico `ANDROID_UI_ACTION`:

- `android.open_url`
- `android.share_text`
- `android.launch_app`

Estas herramientas siempre abren una interfaz visible del sistema o de otra app. No se conceden al worker normal de una misión.

El propósito es permitir flujos explícitos como “abre el resultado en el navegador” o “comparte este archivo/texto” sin recurrir a shell ni privilegios Shizuku.

Los URLs quedan limitados a HTTP/HTTPS y los package names se validan antes de crear intents.
