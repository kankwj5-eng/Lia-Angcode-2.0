# Modelos locales

AngCode gestiona modelos GGUF dentro de `files/models`.

## Flujo

1. En Ajustes, pulsar **Importar modelo GGUF**.
2. El archivo se copia a almacenamiento privado.
3. Se valida la cabecera `GGUF`.
4. Se calcula SHA-256 mientras se copia.
5. El usuario puede marcar uno como activo.
6. El Tool Pack `local-llm` instala `llama-cpp`.
7. `ExecutableDiscovery` detecta `llama-cli` y `llama-server`.

La primera implementación usa `LlamaCliModelGateway`. Cada respuesta debe ser JSON de tipo `tool` o `final`; cualquier acción solicitada vuelve al Tool Broker y conserva sus permisos.
