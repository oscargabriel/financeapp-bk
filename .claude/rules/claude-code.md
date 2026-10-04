# Claude Code en financeapp-bk

La guía del proyecto es `AGENTS.md`, común a cualquier agente. Este archivo carga junto a ella y
solo agrega lo propio de Claude Code. Una regla nueva sobre el código, la arquitectura o el flujo va
en `AGENTS.md`, no aquí.

## No crear un CLAUDE.md

Claude Code lee `AGENTS.md` directamente **solo mientras no exista un `CLAUDE.md` ni un
`CLAUDE.local.md`** en el repo o por encima; si aparece uno, deja de leer `AGENTS.md`. Por eso:

- No correr `/init` en este repo: genera un `CLAUDE.md`.
- No crear `CLAUDE.local.md`. Lo personal va a `~/.claude/CLAUDE.md` o a otro archivo de
  `.claude/rules/`.
- Requiere Claude Code 2.1.277 o posterior. En `/memory` tiene que aparecer `AGENTS.md`; si no
  aparece, la sesión no lo está leyendo.

## Skills

Las skills de este proyecto viven en `.claude/skills/`, no en `~/.claude/skills/`. Las del catálogo
propio se copiaron de `C:\Users\ozamb\skills-catalogo\` y cada una dice su origen en
`metadata.origen`; si se modifica una, se modifica la copia del repo. **No volver a poner en
`~/.claude/skills/` una skill con el mismo nombre**: la personal tiene precedencia sobre la del
proyecto y taparía la copia.

`java-debugging` incorpora el método de `systematic-debugging` de superpowers 6.4.2 (MIT, ver su
`LICENSE-superpowers`). Al actualizarse superpowers, comparar esa parte con la versión nueva.

## OpenSpec

`openspec init` instaló sus skills en `.claude/skills/openspec-*` y sus comandos en
`.claude/commands/opsx/`: `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:update`,
`/opsx:sync` y `/opsx:archive`. **No se editan**: `openspec update` los regenera y pisa cualquier
cambio. Lo propio del proyecto va en `openspec/config.yaml`, en `AGENTS.md` y en `tareas-notion`.

## superpowers está apagado en este repo

`.claude/settings.json` lo desactiva con `enabledPlugins` mientras se prueba el flujo con OpenSpec:
los dos traen su propio paso de diseño y habría dos dueños de la misma decisión. En este repo eso
reemplaza la regla global de hacer brainstorming antes de una feature: el diseño se hace en el
change. La ejecución la cubren las skills del proyecto: `java-testing` (TDD por capa),
`java-debugging`, `verificar` y las de dominio de la tabla del paso 5 de `tareas-notion`.

## Tareas en Notion

El servidor MCP `notion` está declarado en `.mcp.json` con alcance de proyecto: solo existe aquí.
Requiere `claude mcp login notion` una vez por máquina. El ciclo completo —consumir la tarea,
pasarla por un change de OpenSpec, entregarla y cerrarla— está en el skill `tareas-notion`, que
tiene los IDs de las bases. El diseño original del tablero está en
`docs/superpowers/specs/2026-09-11-notion-tareas-design.md`.

**Reparto con la memoria persistente:** Notion guarda lo accionable, `openspec/specs/` lo que el
sistema hace, y el `design.md` de cada change el porqué. La memoria de Claude queda para cómo
trabajar con el usuario y para decisiones viejas que todavía no tienen spec: cuando un change toque
una capacidad descrita en una memoria, su contenido pasa a la spec y la memoria se borra.

Esto adapta la regla del `~/.claude/CLAUDE.md` global que pide registrar cada mock o stub acordado
en una memoria tipo `project`: aquí se cumple creando la tarea en Notion. Es deliberado, no un
olvido.
