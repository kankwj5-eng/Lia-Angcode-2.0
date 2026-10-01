# Agentic Android DevBox

> Nombre interno provisional. Este repositorio es un proyecto independiente de Lía.

Plataforma de desarrollo agentica para Android construida alrededor de un runtime tipo Termux: IA local, sandboxes Linux, herramientas, navegador, compilación, agentes paralelos y exportación de artefactos desde el teléfono.

## Objetivo

Convertir un teléfono Android en una estación de trabajo de IA capaz de:

- ejecutar comandos y procesos de forma controlada;
- trabajar por proyectos y carpetas aisladas;
- usar Git, Python, Node, Java, Clang y herramientas Android;
- levantar entornos PRoot/OCI cuando una tarea requiera Linux tradicional;
- coordinar múltiples agentes lógicos con workspaces separados;
- navegar y automatizar páginas web;
- compilar, probar y exportar APKs y otros artefactos;
- extender capacidades mediante Tool Packs y conectores/MCP;
- mantener registros, checkpoints, permisos y límites de recursos.

## Diseño

La interfaz será una aplicación Android moderna, no una terminal disfrazada.

Dirección visual inicial:

- negro profundo y paneles grafito;
- blanco de alto contraste;
- naranja como color de acción;
- iconografía clara y técnica;
- dashboard de proyectos, agentes, herramientas y artefactos;
- terminal integrada como herramienta secundaria.

## Arquitectura inicial

```text
Android UI / Director
        |
   Agent Runtime
        |
   Tool Broker
   /    |     \
Native  MCP   Android Bridge
Termux        API / Shizuku
   |
PRoot / OCI (cuando sea necesario)
```

Consulta [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) y [docs/ROADMAP.md](docs/ROADMAP.md).

## Estado

🧱 **Fase 0 — Base limpia**

El repositorio se inicializó desde cero. El código de Termux no se importa de golpe: primero se separarán el runtime necesario, la UI nueva y la capa agentica para mantener una base entendible y mantenible.

## Estructura

```text
android/       Aplicación Android y UI
runtime/       Ejecución, sesiones, procesos y sandbox
agents/        Director, scheduler y agentes
connectors/    MCP y conectores externos
toolpacks/     Paquetes instalables de herramientas
docs/          Arquitectura, decisiones y roadmap
scripts/       Automatización del repositorio
```

## Licencias

La base de Termux que se reutilice está bajo GPLv3-only. Todo código derivado de Termux deberá conservar los avisos y obligaciones correspondientes. Las dependencias adicionales mantendrán sus licencias originales.

---

Proyecto en construcción.
