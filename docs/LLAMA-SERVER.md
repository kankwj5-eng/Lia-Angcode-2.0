# llama-server persistente

AngCode prefiere `llama-server` cuando está disponible.

`LlamaServerManager`:

- inicia el servidor solo en `127.0.0.1`;
- selecciona un puerto local libre;
- espera `GET /health` hasta que el modelo esté cargado;
- reutiliza el mismo proceso mientras el GGUF activo no cambie;
- conserva logs del proceso;
- detiene el proceso cuando cambia de modelo o se solicita explícitamente.

`LlamaServerModelGateway` usa `POST /v1/chat/completions` con `response_format=json_object`.

El protocolo lógico sigue siendo el mismo:

- `{"type":"tool", ...}`
- `{"type":"final", ...}`

Si `llama-server` no arranca y existe `llama-cli`, el dashboard cae automáticamente al gateway CLI.
