# MCP

AngCode usa MCP como una capa de conectores, no como sustituto del Tool Broker.

## Flujo

```text
Modelo
  -> Tool Broker
  -> McpProxyTool
  -> McpHttpClient
  -> servidor MCP
```

El cliente actual implementa:

- `initialize`;
- notificación `notifications/initialized`;
- `tools/list`;
- `tools/call`;
- captura y reutilización de `Mcp-Session-Id`;
- cierre de sesión HTTP;
- respuestas JSON y el caso simple de Streamable HTTP/SSE.

Los tools descubiertos se registran dinámicamente en `ToolBroker` mediante
`connectMcpHttp()`.

## Lightpanda

Cuando el proceso Lightpanda esté corriendo dentro del userland PRoot:

```kotlin
val client = McpHttpClient("http://127.0.0.1:9223/mcp")
val info = broker.connectMcpHttp(client, namespace = "browser")
```

El Director verá entonces tools como:

- `browser.goto`
- `browser.markdown`
- `browser.extract`
- `browser.click`
- `browser.fill`
- `browser.screenshot`
- `browser.session_new`

Cada conexión conserva su `Mcp-Session-Id`. Una AgentCell puede usar una
conexión distinta para tener página, cookies y memoria aisladas.

## Seguridad

El proxy exige `NETWORK` y `PRIVATE_NETWORK` por defecto porque un MCP
local normalmente vive en localhost. La llamada sigue pasando por
`ToolBroker`; conectar un servidor MCP no le da permisos automáticos al
modelo.
