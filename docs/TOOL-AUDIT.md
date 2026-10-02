# Auditoría de herramientas

Cada llamada que pasa por `ToolBroker.execute()` genera un evento JSONL en:

`.agent/tool-audit.jsonl`

Se registra timestamp, tool ID, éxito/fallo, duración, tamaño de salida, permisos requeridos/concedidos, argumentos saneados y metadata saneada.

No se persisten valores de claves que parezcan tokens, contraseñas, secretos, autorización, cookies, claves privadas, identidad, contenido, texto, código, prompt, stdin o listas de argumentos.

El archivo rota al superar 5 MiB. `audit.tail` permite revisar eventos recientes dentro del mismo workspace.
