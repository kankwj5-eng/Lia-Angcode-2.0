# Director de misiones

AngCode ya puede analizar una misión localmente antes de cargar un modelo.

`MissionAnalyzer` combina:

- señales del proyecto;
- palabras/objetivos de la misión;
- capacidades requeridas;
- Tool Packs recomendados;
- agentes necesarios;
- dependencias entre tareas.

Tipos de proyecto detectados:

- Android
- Node/Web
- Python
- C/C++
- Rust
- Go
- genérico

Capacidades detectables incluyen edición, pruebas, build, investigación web, navegador, Git, multimedia, Android y sandbox Linux.

`MissionStateMachine` mantiene estado y usa `MissionScheduler` para decidir qué tareas son ejecutables según RAM/batería/temperatura.

Esta capa es determinista y local. Un LLM puede refinar el plan después, pero AngCode no depende del modelo para saber que un APK requiere un builder o que dos tareas tienen dependencias.
