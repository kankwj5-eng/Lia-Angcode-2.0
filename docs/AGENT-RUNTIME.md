# Agent Runtime

El runtime mantiene un ciclo acotado:

```text
TASK -> PLAN -> MODEL -> TOOL_CALL -> OBSERVATION -> MODEL -> FINAL
```

Cada paso queda registrado en memoria estructurada.

- `maxSteps` evita bucles infinitos.
- `planningInterval` fuerza replanificación periódica.
- `memoryWindowChars` limita el contexto reconstruido.
- toda herramienta cruza `ToolBroker`;
- los validadores pueden rechazar una respuesta final.

Un worker especializado se puede envolver con `ManagedAgentTool` y registrar
en el mismo broker. Así el Director puede llamar `agent.coder`,
`agent.tester`, `browser.goto`, `file.read` o `process.exec` sin crear
una vía lateral de ejecución.

Próximos pasos: schemas tipados de tools, presupuestos de tiempo/tokens,
checkpoints persistentes, paralelismo limitado por scheduler, MCP client real
y asociación entre AgentCell y sesiones Lightpanda.
