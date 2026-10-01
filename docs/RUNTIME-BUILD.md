# Build reproducible del runtime AngCode

AngCode no reutiliza el bootstrap binario oficial de Termux porque ese bootstrap está compilado para el package/prefix de `com.termux`.

El build oficial de `termux-packages` documenta como variables seguras para forks:

- `TERMUX__NAME`
- `TERMUX_APP__PACKAGE_NAME`
- `TERMUX_APP__DATA_DIR`
- `TERMUX__ROOTFS`
- `TERMUX__PREFIX`

El script `scripts/build-angcode-bootstrap.sh` fija:

```text
TERMUX__NAME=AngCode
TERMUX_APP__PACKAGE_NAME=com.kankwj.angcode
TERMUX_APP__DATA_DIR=/data/data/com.kankwj.angcode
```

Las rutas derivadas, incluido `TERMUX_PREFIX`, son validadas antes de compilar.

## Upstream fijado

Por reproducibilidad, el workflow no compila `master` flotante. Usa un commit concreto de `termux/termux-packages` y lo registra en el manifiesto generado.

## Perfiles

### minimal

Bootstrap base +:

- proot
- git
- curl
- openssh

### dev

Añade:

- Python
- Node.js LTS
- Clang
- CMake
- Ninja
- Make
- SQLite
- jq

### full

Añade multimedia y análisis:

- FFmpeg
- ImageMagick
- Tree-sitter

## Arquitecturas

El workflow soporta:

- aarch64
- arm
- x86_64
- i686

Para teléfonos actuales el primer objetivo es `aarch64`.

## Salidas

Cada ejecución manual produce:

- `angcode-bootstrap-<profile>-<arch>.zip`
- SHA-256
- manifiesto de procedencia/build

El APK no debe confiar únicamente en el nombre del archivo: el instalador del runtime verificará hash antes de extraer.
