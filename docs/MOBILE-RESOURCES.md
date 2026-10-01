# Recursos móviles

El runtime puede consultar recursos del teléfono sin depender de Termux:API:

- `android.battery`
- `android.memory`
- `android.storage`
- `android.thermal`

Estas mediciones se usarán para alimentar el scheduler de agentes: reducir concurrencia cuando baja la RAM, la batería está baja o Android informa presión térmica.

# Multimedia

El pack `media` ya tiene wrappers estructurados:

- `media.probe` → ffprobe
- `media.convert` → ffmpeg
- `image.convert` → ImageMagick

Los archivos de entrada y salida se confinan al workspace y los binarios deben haber sido descubiertos por el runtime.
