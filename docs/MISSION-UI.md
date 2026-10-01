# Interfaz de misiones

El dashboard ya no muestra una misión de ejemplo fija.

Flujo actual:

1. el usuario pulsa **Nueva misión**;
2. escribe el objetivo;
3. AngCode toma el proyecto activo o crea `_scratch`;
4. `MissionAnalyzer` detecta tipo de proyecto y capacidades;
5. la UI muestra Tool Packs recomendados;
6. se presenta el plan y los agentes derivados.

Al importar una carpeta desde Proyectos, esa carpeta pasa a ser el proyecto activo automáticamente mediante `ActiveProjectStore`.

Esta fase todavía no ejecuta el plan con un LLM: prepara un plan real y determinista que la siguiente capa del Director podrá provisionar y ejecutar.
