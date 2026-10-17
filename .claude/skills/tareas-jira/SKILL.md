---
name: tareas-jira
description: Usar cuando se pida la siguiente tarea, tomar o cerrar una tarea del tablero de financeapp-bk ("siguiente tarea", "qué sigue", "toma la tarea X", "toma la FIN-n", "cierra la tarea"), cuando se retome un change ya aprobado en una sesión nueva ("aplica fin-n-slug", "aplica FIN-n", "retoma FIN-n"), cuando llegue un encargo suelto para este repo que haya que convertir en tarea, o cuando aparezca un pendiente que haya que registrar. Cubre el ciclo completo: issue de Jira → change de OpenSpec → aprobación → implementación → archive → entrega → cierre. No usar en otros repositorios.
---

# Tareas en Jira — financeapp-bk

El tablero vive en Jira y es la fuente de verdad de **qué falta**. Cada issue se resuelve a través
de un change de OpenSpec: `openspec/specs/` dice qué hace el sistema y el change archivado guarda el
porqué. El flujo completo, sin detalles de herramienta, está en `AGENTS.md` (sección *Flujo de
trabajo*); este skill es su versión operativa para Claude Code.

Este archivo es el núcleo: el tablero, los estados, `jira.ps1` y por dónde entrar. Cada etapa del
ciclo está en su referencia y se lee al llegar a ella, no antes: la sesión de propuesta no carga la
entrega y la de implementación no carga la propuesta.

## Por dónde entrar

| Entrada | Leer | Pasos |
|---|---|---|
| «siguiente tarea», «qué sigue», «toma la FIN-n», un encargo suelto | `references/proponer.md` | 1 Consumir · 2 Tomar · 3 Proponer · 4 Aprobar |
| «aplica fin-<n>-<slug>», «aplica FIN-n», «retoma FIN-n» | `references/aplicar.md` | Retomar · 5 Implementar · 6 Verificar · 7 Archivar · 10 Descubrimientos |
| Los pasos 6 y 7 en verde | `references/entregar.md` | 8 Entregar · 9 Cerrar |
| Hay que crear un issue (encargo suelto, descubrimiento, el issue `Frontend` de *El contrato*) o decidir sus dependencias | `references/crear-issue.md` | |
| Un issue queda obsoleto o sustituido (paso 11) | `references/descartar.md` | |
| `jira.ps1` o el MCP fallan, o una escritura expira | `references/jira-falla.md` | |
| Aparece una `FA-n` o una etiqueta `notion-FA-n` | `references/notion.md` | |

## Dónde está el tablero

Proyecto `FIN`: https://zaos93.atlassian.net/browse/FIN

Todas las llamadas al MCP `atlassian` llevan `cloudId` = `d1af94ae-8a43-4c17-b205-f6376c06f68d`.
Cada issue tiene una clave `FIN-n`. Úsala al referirte a una tarea en el chat, en la rama y en los
mensajes de commit.

El tablero es compartido con el front, que vive en su propio repo, `financeapp-fr`. Tiene dos capas:

| Capa | Qué es | Reglas |
|---|---|---|
| **Epic** | Un tema de la app (Finanzas compartidas, Asistente y Telegram…). Cubre los dos repos | **Sin componente.** Guarda el objetivo, `## Decisiones`, `## Fuera de alcance`, `## Flujo` y `## Pedidos`. Lo mantiene el skill `crear-tareas` de la raíz `1-finanzas`: **este repo lo lee y no edita su descripción** |
| **Tarea** (`Historia`, `Error` o `Tarea`) | Un repo, un resultado | **Componente y `parent` obligatorios.** Nombra las decisiones del epic por su clave, no las copia |

El componente de cada tarea dice dónde se resuelve: `Backend` es este repo, `Frontend` el otro.
**Este skill solo toma y modifica tareas con componente `Backend`** —un enlace `Blocks` desde o
hacia un issue que crea o tiene en curso no cuenta como modificar, ver `references/crear-issue.md`—,
y toda tarea que cree lleva el componente y el epic puestos: sin componente no aparece en la consulta
de ningún repo, y sin epic la raíz no la ve al refinar el tema.

| Concepto | En Jira |
|---|---|
| Tipo | `Historia` (feature), `Error` (bug), `Tarea` (el resto, con etiqueta `deuda-tecnica`, `infra`, `cobertura` o `documentacion`) |
| Prioridad | `High`, `Medium`, `Low` |
| Tema | Epic, como `parent` de la tarea |
| Dependencia | Enlace `Blocks` y línea en `## Depende de`, también entre epics y entre repos |

Estados, con el id de la transición que lleva a cada uno:

| Estado | Transición | Significa |
|---|---|---|
| `pendiente` | 11 | Espera una próxima etapa o una decisión del usuario. Estado inicial al crear |
| `Por hacer` | 21 | Bien definida y sin decisiones pendientes: lista para tomarse |
| `En curso` | 31 | Lo tiene este repo o el otro |
| `Por revisar` | 51 | Entregado, espera la revisión del usuario |
| `Listo` | 41 | Cerrado por el usuario, o descartado (con la etiqueta `descartada`) |

No hay sprints: `Por hacer` no lleva etiqueta. Las tareas que todavía tienen `sprint-actual` se
dejan como están.

Cada campo de Jira llega con todo su objeto REST (URLs, íconos, el issue enlazado completo): se
piden solo los campos que dice cada paso. `createJiraIssue`, `editJiraIssue`,
`transitionJiraIssue` y `addCommentToJiraIssue` del MCP devuelven el issue o el comentario enteros:
toda escritura va con `jira.ps1`, y el MCP queda para leer descripciones en markdown
(`getJiraIssue`).

### `jira.ps1`

`1-finanzas/.claude/scripts/jira.ps1` habla con la API REST de Jira: devuelve una línea por issue
(`clave | tipo | estado | prioridad | componente | epic | [etiquetas] | summary | bloqueada por … |
bloquea …`, solo con los campos pedidos) y una línea `ok` por escritura. Se llama con
`pwsh -NoProfile -File .claude/scripts/jira.ps1 …` desde la raíz y con
`pwsh -NoProfile -File ../.claude/scripts/jira.ps1 …` desde un repo. `-Archivo` acepta el markdown
(`.md`) tal cual: lo convierte a ADF con `md2adf.ps1`, que vive al lado.

| Para | Argumentos |
|---|---|
| Estado de pocas claves | `estado FIN-59 FIN-60` |
| Buscar, o revisar dependencias | `buscar '<JQL>' -Campos summary,status -Max 5`, solo con los campos del propósito; sin `-Campos` trae `summary`, `status`, `components`, `parent` e `issuelinks` |
| Transicionar | `transicion FIN-60 21` |
| Agregar o quitar etiquetas, conservando las demás | `etiquetas FIN-60 -Agregar descartada` / `-Quitar descartada` |
| Reescribir una descripción | `descripcion FIN-60 -Archivo <carpeta>/FIN-60.md` |
| Comentar | `comentar FIN-60 -Archivo <carpeta>/comentario.md` |
| Enlazar dos issues existentes | `enlazar FIN-59 FIN-60`: FIN-59 bloquea a FIN-60 |
| Crear issues, enlazarlos y reescribir epics en una llamada | `crear-lote <carpeta>/lote.md`, primero con `-Simular`. El formato está donde se crea: paso 6 de `crear-tareas` y `references/crear-issue.md` de cada repo |
| Comprobar issues creados o editados | `verificar FIN-126 FIN-127`: componente, epic, casillas, enlaces y si `## Depende de` coincide con los `is blocked by`. Marca `ERROR` lo que falla |

El markdown que entiende `md2adf.ps1`: `## `, `> `, `- [ ]`, `- [x]`, `- `, párrafos, `código`,
`**negrita**` y URLs; nada más. Un error de Jira sale como
`HTTP <código> en <método> <ruta>. <mensaje de Jira>` y se corrige como cualquier error. Si el
script dice que faltan `JIRA_EMAIL` o `JIRA_API_TOKEN` (variables de entorno de usuario), o falla por
otra cosa que no es un HTTP de Jira, se sigue con el MCP como dice `references/jira-falla.md` y se
avisa en chat.

Los archivos que se le pasan (`lote.md`, comentarios, mensaje de commit, cuerpo del PR) se escriben
en el scratchpad de la sesión, fuera del repo, y no hace falta borrarlos.

## Prohibiciones

- **Nunca** pasar un issue a `Listo` salvo para descartarlo (paso 11). Cerrar es del usuario.
- **Nunca** borrar un issue. Lo obsoleto se descarta con el paso 11, que es reversible y localizable.
- **Nunca** modificar issues distintos del que está en curso. Tres excepciones: crear nuevos;
  enlazar `Blocks` un issue nuevo, o el que está en curso, con uno existente, también del otro
  repo (`references/crear-issue.md`); y las operaciones de tablero que el usuario pida explícitamente —repriorizar un
  conjunto, descartar lo obsoleto, reordenar las tareas de un tema—, que se ejecutan y se reportan
  una por una.
- **Nunca** escribir que un issue «no bloquea» a otro ni omitir un enlace por suponerlo: si no está
  claro, se pregunta.
- **Nunca** crear un epic ni editar su descripción: los mantiene `crear-tareas` desde la raíz.
- **Nunca** crear una tarea sin componente o sin epic (`parent`).
- **Nunca** escribir en Notion: es histórico de solo lectura.
- **Nunca** inventar el resultado de una verificación que no se corrió.
- **Nunca** reintentar una escritura que expiró sin leer antes si se aplicó, ni implementar un
  issue que no se pudo leer (`references/jira-falla.md`).
