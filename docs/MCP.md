# MCP

AngCode usa MCP como bus extensible de herramientas.

## Backends

### OfficialMcpHttpClient

Backend principal basado en el SDK oficial Kotlin:

- `io.modelcontextprotocol:kotlin-sdk-client:0.15.0`
- transporte Streamable HTTP;
- negociación/compatibilidad de protocolo gestionada por el SDK;
- herramientas remotas convertidas dinámicamente a `AgentTool`.

### LegacyMcpClientPort

Adaptador para el cliente HTTP manual que ya existía en el proyecto. Se conserva para compatibilidad con servidores de la generación 2025 que dependan de sesión/handshake antiguo.

## Permisos

Las herramientas MCP requieren `MCP_EXTERNAL` además de `NETWORK`.

Cuando el servidor vive en localhost/red privada, el caller debe añadir también `PRIVATE_NETWORK`.

## Lightpanda

Lightpanda puede exponerse como MCP local. Una vez que el binario esté disponible dentro de PRoot, AngCode podrá:

1. iniciar Lightpanda como proceso administrado;
2. conectar `OfficialMcpHttpClient` a su endpoint;
3. descubrir herramientas;
4. registrarlas automáticamente con namespace `browser.*`.

Esto mantiene el navegador agentico desacoplado del Director y del modelo.
