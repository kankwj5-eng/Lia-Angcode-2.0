# Tool Pack runtime

Los manifiestos de `toolpacks/samples/` se empaquetan como assets de la APK de desarrollo.

`ToolPackAssetRepository` los descubre y convierte a `ToolPackManifest`.

`ToolPackEvaluator` responde cuatro preguntas antes de activar un pack:

1. ¿La arquitectura del teléfono es compatible?
2. ¿El backend requerido (Android nativo o PRoot) está listo?
3. ¿Qué paquetes del userland faltan?
4. ¿Qué herramientas del Tool Broker faltan?

Esto permite que el Director elija un pack sin instalar todo el universo de herramientas en cada teléfono.

Ejemplo: un proyecto web puede pedir `web-node`; un proyecto Android puede pedir `android-dev`. Los paquetes pesados se instalan solo cuando el usuario realmente los necesita.
