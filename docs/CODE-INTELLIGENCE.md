# Inteligencia de código

AngCode expone una API estable al agente y permite cambiar el backend.

## Ahora

`code.search` busca texto.

`workspace.search` ofrece una variante con límites configurables.

`code.symbols` genera un outline ligero de clases, funciones, interfaces y otros símbolos comunes sin depender de un binario externo. Es deliberadamente conservador: sirve para navegación rápida, no pretende ser un parser completo.

## Tree-sitter

El siguiente backend de `code.symbols` y futuras herramientas `code.ast.*` será Tree-sitter (`tree-sitter/tree-sitter`, MIT).

La ventaja es importante para un teléfono:

- el modelo pide estructura en vez de archivos completos;
- se reducen tokens;
- los parches pueden apuntar a nodos/símbolos;
- el índice puede actualizarse incrementalmente.

El Tool Broker mantendrá los mismos IDs aunque cambie el backend.
