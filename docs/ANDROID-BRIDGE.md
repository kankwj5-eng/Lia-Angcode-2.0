# Android Bridge

El Android Bridge tendrá varios niveles. El Director debe preferir siempre el nivel de menor privilegio que resuelva la tarea.

## APP

Funciona dentro de la aplicación normal. Ya existe soporte inicial para:

- `android.device.info`
- `android.battery`
- `android.clipboard.read`
- `android.clipboard.write`

Las funciones del portapapeles requieren permisos lógicos propios en el Tool Broker aunque Android no use un permiso de manifiesto clásico para cada acceso.

## SHIZUKU

Backend futuro basado en `RikkaApps/Shizuku` (Apache-2.0). Permitirá operaciones autorizadas con privilegios ADB sin convertir root en requisito.

Casos posibles:

- package manager;
- activity manager;
- determinadas operaciones de shell;
- inspección avanzada del dispositivo.

## ROOT

Backend futuro basado en `topjohnwu/libsu` (Apache-2.0).

Será opcional y nunca se habilitará silenciosamente.

## Inspiración Termux:API

`termux/termux-api` demuestra una superficie amplia de APIs Android accesibles desde herramientas. AngCode reutilizará ideas y, cuando corresponda legal/técnicamente, componentes, pero ofrecerá contratos propios del Tool Broker en vez de exponer una colección de comandos sin control central.
