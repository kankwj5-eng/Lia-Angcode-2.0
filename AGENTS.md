# AGENTS.md

Estas reglas aplican a cualquier agente que trabaje en este repositorio.

## Principios

1. Mantener el proyecto independiente de Lía.
2. No copiar Termux completo sin una razón técnica documentada.
3. Separar UI, runtime, agentes, herramientas y conectores.
4. Preferir interfaces pequeñas y reemplazables entre módulos.
5. No dar acceso indiscriminado al almacenamiento o al sistema Android.
6. Toda ejecución de herramientas debe pasar por el Tool Broker.
7. Los procesos largos deben ser observables, cancelables y registrar salida.
8. Los agentes lógicos no requieren modelos separados; compartir el modelo cuando ahorre RAM.
9. Antes de cambios destructivos, crear checkpoint o commit.
10. Todo artefacto generado debe terminar en un directorio de salida explícito.

## Seguridad

- PRoot no se considera una frontera de seguridad suficiente por sí sola.
- El Tool Broker debe validar rutas, permisos, tiempo, red y procesos.
- Ninguna herramienta descargada de Internet recibe acceso completo automáticamente.
- Shizuku/root serán capacidades opcionales y explícitas.
- Nunca incluir secretos, tokens, claves de firma o modelos grandes en Git.

## Estilo de arquitectura

- Android UI: Kotlin + Jetpack Compose.
- Runtime: interfaces independientes del frontend.
- Comunicación interna: estructuras tipadas; evitar parsing informal de texto cuando sea posible.
- Conectores: MCP cuando encaje; adaptadores propios cuando sea más eficiente.
- Estado: persistente por proyecto/celda, no global por defecto.

## Flujo de cambios

- Una tarea = una rama o worktree cuando pueda tocar varias partes.
- Compilar/testear antes de fusionar.
- Commits pequeños y descriptivos.
- Documentar decisiones arquitectónicas importantes en `docs/`.
