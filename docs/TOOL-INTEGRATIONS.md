# Auditoría de herramientas externas

## Lightpanda Browser

El código suministrado confirma que Lightpanda está escrito principalmente en
Zig y expone CDP, WebDriver BiDi y MCP. Su MCP ofrece sesiones independientes
para varios agentes y herramientas de navegación, extracción, screenshots,
formularios, cookies, consola y evaluación JavaScript.

### Integración Android

El upstream indica que no hay build Android nativo. El binario Linux aarch64
requiere glibc, mientras Android usa Bionic. Por eso AngCode lo ejecutará como
proceso separado dentro de un userland glibc:

```text
AngCode -> Tool Broker -> PRoot -> Debian/Ubuntu -> Lightpanda -> MCP/CDP
```

Cada AgentCell podrá recibir un `Mcp-Session-Id` distinto para no mezclar
página, cookies ni memoria de navegación.

La telemetría se desactiva por defecto con
`LIGHTPANDA_DISABLE_TELEMETRY=true`.

### Licencia

Lightpanda usa AGPL-3.0-only. No se copiará su fuente dentro del núcleo de
AngCode. Se mantendrá como componente opcional separado, conservando origen y
licencia. Cualquier futura redistribución conjunta tendrá que cumplir las
obligaciones aplicables de AGPL.

## smolagents

El código suministrado usa Apache-2.0 y Python 3.10+.

AngCode adopta de forma nativa en Kotlin las ideas útiles:

- bucle multi-step;
- máximo de pasos;
- planificación periódica;
- memoria explícita de acciones/observaciones;
- agentes administrados invocables como herramientas;
- eventos por paso;
- validación de respuesta final;
- tool calling estructurado.

No hace falta que Python coordine el sistema principal. smolagents completo
podrá ser un Tool Pack opcional dentro de un sandbox.

Un intérprete Python con módulos prohibidos no se considera por sí solo una
frontera de seguridad. La frontera sigue siendo:
permisos -> Tool Broker -> política de ejecución -> sandbox.
