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

## Configuración compartida con la raíz

La configuración de agentes de este repo —`.claude/`, `AGENTS.md`, `openspec/config.yaml` y
`.mcp.json`— también se mantiene desde la raíz `1-finanzas`, para que lo que comparte con
`financeapp-fr` (tablero, estados, campos de Jira, corte de sesión) quede igual en los dos. Un
cambio de ese tipo se hace allí; uno propio de este repo, aquí. Los cambios hechos desde la raíz
llegan sin commit.

`jira.ps1`, `md2adf.ps1` y `entregar.ps1` viven en `1-finanzas/.claude/scripts/` y se llaman con
`../.claude/scripts/…`: este repo los necesita con la raíz al lado.

`.claude/settings.json` apaga los plugins sincronizados de claude.ai (`engineering`,
`cowork-plugin-management`), los conectores de claude.ai, Artifact y `NotebookEdit`: no se usan aquí
y sus descripciones pesaban en cada turno. Se reactivan quitando la clave.

## OpenSpec

`openspec init` instaló sus skills en `.claude/skills/openspec-*` y sus comandos en
`.claude/commands/opsx/`: `/opsx:explore`, `/opsx:propose`, `/opsx:apply`, `/opsx:update`,
`/opsx:sync` y `/opsx:archive`. **No se editan**: `openspec update` los regenera y pisa cualquier
cambio. Lo propio del proyecto va en `openspec/config.yaml`, en `AGENTS.md` y en `tareas-jira`.

## superpowers está apagado en este repo

`.claude/settings.json` lo desactiva con `enabledPlugins` mientras se prueba el flujo con OpenSpec:
los dos traen su propio paso de diseño y habría dos dueños de la misma decisión. En este repo eso
reemplaza la regla global de hacer brainstorming antes de una feature: el diseño se hace en el
change. La ejecución la cubren las skills del proyecto: `java-testing` (TDD por capa),
`java-debugging`, `verificar` y las de dominio que cada grupo de `tasks.md` nombra (tabla de
`references/proponer.md` de `tareas-jira`).

## Tareas en Jira

El servidor MCP `atlassian` está declarado en `.mcp.json` con alcance de proyecto: solo existe
aquí. Requiere autenticarse una vez por máquina desde `/mcp`. El ciclo completo —consumir el issue,
pasarlo por un change de OpenSpec, entregarlo y cerrarlo— está en el skill `tareas-jira`: un núcleo
con el `cloudId`, los estados y una tabla de entrada, y cada etapa en `references/` (`proponer.md`,
`aplicar.md`, `entregar.md`), que se lee al llegar a ella. Una tarea se lleva en dos sesiones: la
propuesta termina en la aprobación, y la implementación arranca en una sesión nueva con
`aplica fin-<n>-<slug>` (*Retomar un change aprobado* en `aplicar.md`). Un change largo se aplica en
tramos de tres grupos, cada uno en su sesión.

El servidor `notion` sigue declarado en `.mcp.json` solo para leer el histórico de las tareas
`FA-n`, pero no está en `enabledMcpjsonServers`: se activa desde `/mcp` cuando hace falta, y
`.claude/settings.json` niega sus herramientas de escritura. El diseño original de ese tablero está
en `docs/superpowers/specs/2026-09-11-notion-tareas-design.md`.

**Reparto con la memoria persistente:** Jira guarda lo accionable, `openspec/specs/` lo que el
sistema hace, y el `design.md` de cada change el porqué. La memoria de Claude queda para cómo
trabajar con el usuario y para decisiones viejas que todavía no tienen spec: cuando un change toque
una capacidad descrita en una memoria, su contenido pasa a la spec y la memoria se borra.

Un mock o stub acordado se registra como issue en Jira (paso 10 de `tareas-jira`), no en una
memoria.
