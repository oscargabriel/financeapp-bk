# CLAUDE.md

@AGENTS.md

Lo de arriba es la guía del proyecto, común a cualquier agente. Aquí va solo lo propio de Claude Code.
Una regla nueva sobre el código, la arquitectura o el flujo va en `AGENTS.md`, no aquí.

## OpenSpec en Claude Code

`openspec init` instaló sus skills en `.claude/skills/openspec-*` y sus comandos en
`.claude/commands/opsx/`: `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:update`,
`/opsx:sync` y `/opsx:archive`. **No se editan**: `openspec update` los regenera y pisa cualquier
cambio. Lo propio del proyecto va en `openspec/config.yaml`, en `AGENTS.md` y en el skill
`tareas-notion`, que `openspec update` no toca.

## superpowers está apagado en este repo

`.claude/settings.json` lo desactiva con `enabledPlugins` mientras se prueba el flujo con OpenSpec:
los dos traen su propio paso de diseño —brainstorming y writing-plans frente a explore, propose y
tasks— y con los dos activos habría dos dueños de la misma decisión. En este repo eso reemplaza la
regla global de hacer brainstorming antes de una feature: el diseño se hace en el change.

La disciplina de ejecución no se pierde, la cubren skills propias que no dependen del plugin:
`java-testing` (TDD por capa), `java-debugging`, `verificar` y las de dominio de la tabla del paso 5
de `tareas-notion`.

## Tareas en Notion

El servidor MCP `notion` está declarado en `.mcp.json` con alcance de proyecto: solo existe aquí.
Requiere `claude mcp login notion` una vez por máquina. El ciclo completo —consumir la tarea, pasarla
por un change de OpenSpec, entregarla y cerrarla— está en el skill `tareas-notion`, que tiene los IDs
de las bases. El diseño original del tablero está en
`docs/superpowers/specs/2026-09-11-notion-tareas-design.md`.

**Reparto con la memoria persistente:** Notion guarda lo accionable, `openspec/specs/` lo que el
sistema hace, y el `design.md` de cada change el porqué. La memoria de Claude queda para cómo
trabajar con el usuario y para decisiones viejas que todavía no tienen spec: cuando un change toque
una capacidad descrita en una memoria, su contenido pasa a la spec y la memoria se borra.

Esto adapta la regla del CLAUDE.md global que pide registrar cada mock o stub acordado en una
memoria tipo `project`: aquí se cumple creando la tarea en Notion. Es deliberado, no un olvido.
