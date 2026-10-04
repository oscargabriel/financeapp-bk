# Proposal

Origen: FA-57. El encargo llegó en conversación el 04-10-2026, después de cerrar FA-55, como
"migrar mis flujos a SDD, donde las tareas no vienen tanto del prompt sino de archivos o de una
aplicación".

## Why

El flujo de trabajo —tablero de Notion, superpowers y la configuración de Claude Code— resuelve la
ejecución (TDD, verificación, entrega) pero no deja una documentación viva de lo que el sistema
hace. Ese conocimiento está repartido entre `CLAUDE.md` (escrito como instrucciones para Claude), la
memoria de Claude (que el usuario no lee), specs fechadas en `docs/superpowers/specs/` y el chat; el
usuario termina preguntando. En FA-55 el alcance real se dijo en el chat y nunca llegó a la tarea.

Además todo vive en formatos de Claude. En el trabajo del usuario cada programa dura menos de un
mes y puede llegar Kiro u otro LLM: el flujo tiene que sobrevivir a un cambio de herramienta.

## What Changes

- OpenSpec se inicializa en el repo: `openspec/` con `config.yaml`, `specs/` y `changes/`, y sus
  skills y comandos para Claude Code.
- La guía del proyecto pasa de `CLAUDE.md` a `AGENTS.md`, el archivo que lee casi cualquier agente,
  con una sección nueva del flujo SDD. `CLAUDE.md` la importa y conserva solo lo propio de Claude.
- `openspec/config.yaml` lleva el contexto del proyecto y reglas por artefacto y por operación.
- superpowers se desactiva **solo en este repo** durante la prueba.
- El skill `tareas-notion` orquesta el ciclo completo: tarea → change → aprobación → apply →
  verificar → archive → entregar → cerrar.

## Capabilities

### New Capabilities

Ninguna: el cambio es de herramientas y proceso, no altera el comportamiento del sistema
(`skip_specs: true`).

### Modified Capabilities

Ninguna.

## Impact

- Archivos nuevos: `AGENTS.md`, `openspec/`, `.claude/skills/openspec-*`, `.claude/commands/opsx/`.
- Archivos modificados: `CLAUDE.md`, `.claude/settings.json`, `.claude/skills/tareas-notion/SKILL.md`.
- Nada de `src/`, `bruno/` ni de la imagen de Cloud Run: el `.dockerignore` es una lista de lo
  permitido y no incluye nada de esto.

## Fuera de alcance

- Escribir specs de las capacidades existentes. Crecen con cada change que las toque.
- Mover a specs el contenido de las memorias de Claude. Pasa a medida que un change toque esa
  capacidad.
- Llevar el flujo a otros repositorios o al trabajo. Se decide después de probarlo aquí.
- Hooks de Claude Code que hagan cumplir el flujo. Solo si en la práctica se saltan pasos.
- El perfil ampliado de OpenSpec (`/opsx:verify`, `/opsx:onboard`). Es configuración global de la
  máquina; se evalúa después.
