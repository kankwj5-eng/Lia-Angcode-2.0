# Sandbox y userland

## Qué NO vamos a hacer

No se instalará el bootstrap oficial de Termux dentro de `com.kankwj.angcode`.

Los paquetes Termux se compilan con un `$PREFIX` que contiene el package id. El bootstrap oficial usa por defecto:

`/data/data/com.termux/files/usr`

AngCode usa:

`com.kankwj.angcode`

Por eso copiar el ZIP oficial sin recompilar produciría un entorno parcialmente roto.

## Arquitectura

AngCode separa tres conceptos:

1. **Runner Android** — procesos y PTY nativos.
2. **Sandbox Linux** — PRoot/rootfs descargable y desechable.
3. **Termux-compatible toolchain** — futuro conjunto de paquetes compilados específicamente para el prefix de AngCode.

Esta separación permite empezar a trabajar sin hacer que AngCode suplante el package id de Termux y sin impedir que ambos estén instalados en el mismo teléfono.

## Niveles de ejecución

### Agente

Por defecto puede usar herramientas registradas y ejecutables aprobados. No recibe `sh -c` arbitrario.

### Terminal controlada por el usuario

Puede recibir el permiso `UNRESTRICTED_SHELL` durante una sesión explícita.

### Sandbox

El backend PRoot recibe un workspace montado como `/workspace`. Otros mounts deben declararse individualmente.

## Próximo paso del runtime

Empaquetar/compilar un binario PRoot compatible por ABI y preparar un rootfs mínimo verificable. La descarga deberá usar hash SHA-256 y manifiesto firmado o fijado en el código.
