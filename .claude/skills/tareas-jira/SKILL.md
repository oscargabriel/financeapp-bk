---
name: tareas-jira
description: Usar cuando se pida la siguiente tarea, tomar o cerrar una tarea del tablero de financeapp-bk ("siguiente tarea", "qué sigue", "toma la tarea X", "toma la FIN-n", "cierra la tarea"), cuando llegue un encargo suelto para este repo que haya que convertir en tarea, o cuando aparezca un pendiente que haya que registrar. Cubre el ciclo completo: issue de Jira → change de OpenSpec → aprobación → implementación → archive → entrega → cierre. No usar en otros repositorios.
---

# Tareas en Jira — financeapp-bk

El tablero vive en Jira y es la fuente de verdad de **qué falta**. Cada issue se resuelve a través
de un change de OpenSpec: `openspec/specs/` dice qué hace el sistema y el change archivado guarda el
porqué. El flujo completo, sin detalles de herramienta, está en `AGENTS.md` (sección *Flujo de
trabajo*); este skill es su versión operativa para Claude Code.

## Dónde está el tablero

Proyecto `FIN`: https://zaos93.atlassian.net/browse/FIN

Todas las llamadas al MCP `atlassian` llevan `cloudId` = `d1af94ae-8a43-4c17-b205-f6376c06f68d`.
Cada issue tiene una clave `FIN-n`. Úsala al referirte a una tarea en el chat, en la rama y en los
mensajes de commit.

El tablero es compartido con el front, que vive en su propio repo, `financeapp-fr`. El componente
de cada issue dice dónde se resuelve: `Backend` es este repo, `Frontend` el otro. Los epics son las
etapas, también con su componente. **Este skill solo toma y modifica issues con componente
`Backend`**, y todo issue que cree lleva el componente puesto: sin él no aparece en la consulta de
ningún repo.

| Concepto | En Jira |
|---|---|
| Tipo | `Historia` (feature), `Error` (bug), `Tarea` (el resto, con etiqueta `deuda-tecnica`, `infra`, `cobertura` o `documentacion`) |
| Prioridad | `High`, `Medium`, `Low` |
| Etapa | Epic, como `parent` del issue |
| Dependencia | Enlace `Blocks` |

Estados, con el id de la transición que lleva a cada uno:

| Estado | Transición | Significa |
|---|---|---|
| `pendiente` | 11 | Backlog: falta refinar o es para después. Estado inicial al crear |
| `Por hacer` | 21 | Listo para tomarse; lleva la etiqueta `sprint-actual` |
| `En curso` | 31 | Lo tiene este repo o el otro |
| `Por revisar` | 51 | Entregado, espera la revisión del usuario |
| `Listo` | 41 | Cerrado por el usuario, o descartado (con la etiqueta `descartada`) |

### El histórico de Notion

Hasta el 11-10-2026 el tablero estuvo en Notion, con claves `FA-n`. Las `FA-n` que aparecen en el
código, en `AGENTS.md` y en los changes archivados apuntan allí. Las tareas que seguían abiertas se
migraron con la etiqueta `notion-FA-n` (JQL `labels = notion-FA-42`). El servidor `notion` está
declarado en `.mcp.json` pero apagado, para no cargarlo en cada sesión: para leer el contexto de una
`FA-n` entregada, pedir al usuario que lo active desde `/mcp` y usar `notion-search` o
`notion-fetch`. En Notion no se escribe nada.

## El ciclo

### 1. Consumir

`searchJiraIssuesUsingJql` con
`project = FIN AND component = Backend AND status = "Por hacer" ORDER BY priority DESC, key ASC`,
`maxResults` 5, campos `summary`, `issuetype`, `priority`, `labels`. Mostrar las candidatas con
clave, título, tipo y prioridad. Si el usuario pidió "la siguiente", tomar la primera; si no,
esperar a que elija.

La clave desempata dentro de un mismo nivel de prioridad, y hace falta porque la mayoría de los
issues comparten prioridad: sin ella, "la siguiente" no es una pregunta con una sola respuesta.

### Qué significa cada prioridad

| Nivel | Cuándo | Cuántas esperar |
|---|---|---|
| `High` | Sale primero: un bug, una corrección urgente, o que el usuario diga que esa va antes que las demás. | Pocas; lo normal es una |
| `Medium` | **El valor por defecto de todo issue nuevo.** El trabajo ordinario de las etapas. | La mayoría |
| `Low` | Accesorio. Se hace cuando no queda nada en `Medium`. | Las que sobran |

`Highest` y `Lowest` existen en Jira y no se usan, a propósito: un nivel nuevo se infla igual que el
anterior si no hay una regla de uso, y `High` con la regla escrita ya cubre el caso de "esta va
primero". Si la mayoría de los issues tomables acaba en `High`, la prioridad dejó de discriminar: se
recalibra, no se agregan niveles.

`getJiraIssue` del elegido, con `responseContentFormat: "markdown"`, para leer `## Contexto`,
`## Criterios de aceptación` y sus enlaces (`issuelinks`).

**Si los criterios no permiten decidir cuándo el issue está hecho, no lo tomes.** Deja la duda en un
comentario con `addCommentToJiraIssue`, mantén el estado en `Por hacer` y dilo en chat.

#### Dependencias entre issues

El orden que da la consulta —prioridad, luego clave— no conoce las ataduras reales. Un issue declara
lo que tiene que estar cerrado antes con enlaces `is blocked by`, y lo repite como texto en su
`## Depende de`:

```markdown
## Depende de
- FIN-44 — confirma los parámetros de TLS que este archivo tiene que escribir
```

**Antes de tomar un issue, revisar los dos.** Si alguno de los que lo bloquean no está en
`Por revisar` o `Listo`, no lo tomes: dilo en chat y ofrece el que lo desbloquea. Una dependencia
escrita como "FA-n (entregada, queda en Notion)" ya está cumplida.

#### Un issue que no es de este repo

El filtro de la consulta no protege cuando el usuario nombra el issue por su clave ("toma la
FIN-n"), y el usuario puede equivocarse de número. **Antes de tomar cualquier issue, comprobar que su
componente sea `Backend`.** Si es `Frontend`, o no tiene componente, no se toma ni se toca: ni
estado, ni comentario, ni rama. Decirlo en chat —el issue es del front y se resuelve desde
`financeapp-fr`— y ofrecer las candidatas de backend. Aunque el usuario insista, desde aquí no se
resuelve: si en realidad necesita algo del API, lo que corresponde es un issue nuevo con componente
`Backend`, no tomar el del front.

#### Un encargo que llega suelto

Si el usuario pide algo para este repo sin clave —un mensaje, lo que salió de una conversación—,
primero se registra como issue en `Por hacer` con la etiqueta `sprint-actual` y componente
`Backend` (ver *Crear un issue*): el texto **tal como llegó** bajo `## Contexto` y los criterios que
se puedan deducir bajo `## Criterios de aceptación`. Lo que no se pueda deducir se pregunta antes de
seguir. Después se toma como cualquier otro.

### 2. Tomar

`transitionJiraIssue` con `transition: {"id": "31"}` → `En curso`. Solo un issue de backend en curso
a la vez: si
`project = FIN AND component = Backend AND status = "En curso"` devuelve otro, avisar y preguntar
antes de tomar el nuevo.

Crear la rama desde `dev` en este paso, no al entregar: el change vive en la rama desde el primer
archivo.

```powershell
git checkout dev; git pull --ff-only; git checkout -b feature/fin-<n>-<slug>
```

### 3. Proponer

El change se llama igual que la rama sin el prefijo: `fin-<n>-<slug>`.

1. Si el área a tocar no es evidente o el issue deja dudas de alcance, `/opsx:explore` (skill
   `openspec-explore`) para leer el código y aclarar antes de escribir nada.
2. `/opsx:propose fin-<n>-<slug>` (skill `openspec-propose`) con el `## Contexto` y los
   `## Criterios de aceptación` del issue como entrada. Cada criterio termina como requisito con
   sus escenarios en la delta de spec, o queda explicado en `proposal.md` si no es comportamiento.
3. **Lo que se aclare en el chat se escribe en los artefactos.** Un alcance que solo se dijo
   conversando —como el de FA-55, que no estaba en la tarea— no existe para el siguiente que lea.
4. `openspec validate fin-<n>-<slug>` en verde.

Las reglas de cada artefacto están en `openspec/config.yaml`; no se repiten aquí.

### 4. Aprobar

Mostrar en el chat un resumen de la propuesta —el porqué, los requisitos con sus escenarios, lo que
queda fuera de alcance y la lista de tareas— con la ruta del change, y **esperar la aprobación
explícita del usuario**. Los ajustes que pida se hacen con `/opsx:update` (skill
`openspec-update-change`) y se vuelve a mostrar lo que cambió.

Única excepción: que el usuario haya aprobado de antemano en el mismo pedido ("propón e implementa
sin parar"). Aun así el resumen se muestra.

Esta es la única espera del ciclo. La entrega del paso 8 sigue sin esperar revisión.

### 5. Implementar

`/opsx:apply` (skill `openspec-apply-change`) recorre `tasks.md` y marca cada casilla. La disciplina
dentro de cada tarea no la da OpenSpec, la dan estas skills:

- `java-testing` para el ciclo RED-GREEN-REFACTOR y la ubicación de los tests por capa hexagonal.
- Si el issue agrega o cambia endpoints, el request de `bruno/` con su bloque `runtime.assertions`
  se escribe **en el mismo ciclo RED**, fallando antes de que exista el endpoint. Ver la memoria
  `convencion-coleccion-bruno`.
- Si apply revela que el diseño no alcanza, se para y se corrige el change con `/opsx:update`; no
  se implementa algo distinto de lo aprobado sin decirlo.

**Antes de escribir código, cargar con `Skill` las skills de dominio que correspondan** a lo que el
issue toca. Sus descripciones se disparan con "diseñar" o "configurar", no con "implementar FIN-n",
así que sin este paso no se leen: el constructor manual en todos los beans (corregido en el PR #16)
salió de ignorar `java-architect`, que pedía `@AllArgsConstructor` desde antes del primer commit.

| Si el issue toca… | Cargar |
|---|---|
| Un puerto, caso de uso, adapter, controlador, record de dominio o DTO | `java-architect` |
| `ErrorCodes`, `BadRequestException`, `WebExceptionHandler` o errores en la cadena reactiva | `java-exceptions` |
| `SecurityConfig`, JWT, Basic, CORS o rutas protegidas | `java-security` |
| `LoggingFilter`, `ReactorMdcHook`, MDC o niveles de log | `java-logging` |
| Un bug, un test que falla sin causa evidente o un pipeline `Mono`/`Flux` que no se comporta | `java-debugging` |
| Cualquier archivo de `bruno/` | `bruno-cli` |

Si el issue no toca nada de la tabla, no se carga ninguna; si toca varias filas, se cargan todas.
Las reglas de la skill ceden ante `AGENTS.md` y `.claude/rules/` cuando chocan.

### 6. Verificar

`.\gradlew.bat build` — la suite completa más el umbral de cobertura, que `test` no exige. No
necesita Docker ni base.

`pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos`. El script comprueba
que el puerto esté libre y que la base sea la local antes de levantar la app; si se detiene, ver
`verificar`. Va **siempre**, no solo cuando el issue toca endpoints. Es la única capa que ejerce
el SQL de las vistas y la base real, así que un issue que cambie una consulta, el esquema o el
escenario de datos sin pasar por aquí se cierra sin verificar. Si `bru` no está en el PATH, eso es un bloqueo que se reporta, no un paso que se omite.

Este proyecto **no tiene checkstyle ni linter**: no reportes un paso de lint que no corrió.

**Sin verde no se pasa al paso 7.** No se cierra un issue con tests rojos ni con una corrida de
`bru` que no se ejecutó. Todas las casillas de `tasks.md` tienen que estar marcadas.

### 7. Archivar

`/opsx:archive fin-<n>-<slug>` (skill `openspec-archive-change`) **en la misma rama**, antes del PR:
la delta se fusiona en `openspec/specs/` y el change pasa a
`openspec/changes/archive/<fecha>-fin-<n>-<slug>/`. Se archiva dentro del PR porque aquí el merge a
`dev` es inmediato; esperar al merge, como recomienda OpenSpec, no protegería nada.

Revisar el diff de `openspec/specs/`: es lo que va a leer el siguiente. Si la delta toca una
capacidad que describía una memoria de Claude (`contrato-alta-lote`, por ejemplo), esa memoria se
borra en este paso y su índice se actualiza: la spec la reemplaza.

### 8. Entregar

Con los pasos 6 y 7 hechos, sin preguntar ni esperar revisión:

1. Si el issue tocó `bruno/`, leer en `bruno-personal/` los requests que su `tasks.md` mandó
   replicar y compararlos con los de `bruno/` en ruta, cuerpo y autenticación. Esa carpeta está en
   `.gitignore`: el PR no la muestra y nadie más va a notar que falta. No se ejecutan, porque
   apuntan a Neon.
2. `git add` **solo** de los archivos del issue, incluido todo `openspec/` que tocó: el working tree
   suele tener cambios del usuario que no son de la entrega.
3. Commit con la clave en el título (`FIN-<n> …`) y push.
4. `gh pr create --base dev`. El título empieza por la clave. El cuerpo empieza por la ruta del
   change archivado y el porqué de su `proposal.md`, sigue con los requisitos que cambiaron en
   `openspec/specs/` y cierra con la evidencia del paso 6: quien revise lee primero qué debía hacer
   el cambio y después el código.
5. `pwsh -NoProfile -File .claude/scripts/merge-pr-dev.ps1 <número>`: comprueba que la base sea
   `dev`, mergea con merge commit, vuelve a `dev` con `pull --ff-only` y aparta y repone los
   cambios locales que estorben al checkout. No usar `gh pr merge` directo: el permiso del proyecto
   autoriza el script, no el comando suelto.

El usuario revisa los PRs después en GitHub. Esto reemplaza en este repo al skill global `entregar`,
que nunca mergea. Los PRs contra `main` **no** se mergean nunca: el script los rechaza.

### 9. Cerrar

`transitionJiraIssue` con `transition: {"id": "51"}` → `Por revisar`.

Después `addCommentToJiraIssue` con el cierre, en este orden:

- **Evidencia** — conteo de tests de la suite y resultado de `bru run`, con números reales tomados
  de la salida, nunca estimados.
- **Commit** — sha corto del commit del issue, y el número y enlace del PR.
- **Resumen** — la ruta del change archivado, qué capacidades de `openspec/specs/` cambiaron, qué
  quedó fuera y **qué skills se cargaron en el paso 5**. El detalle vive en el change; el comentario
  no lo repite.

Reportar en chat el enlace del issue.

### 10. Descubrimientos — un issue, un resultado

**Nunca agrandes el issue en curso.** Si durante la implementación aparece algo que sus criterios de
aceptación no cubren —parametrizar los mensajes del bot, un filtro de seguridad contra inyección,
cualquier pieza que sea un feature en sí misma— se crea un issue nuevo, no se mete en este.

El discriminador es una sola pregunta:

> **¿Puedo dejar este issue en verde y cerrado sin eso?**

- **Sí** → issue nuevo en `pendiente` (ver *Crear un issue*): componente del repo donde se resuelve,
  tipo correspondiente, prioridad `Medium` salvo que sea un bug o una urgencia, el epic si alguno
  encaja, y bajo `## Contexto` **por qué surgió y en qué issue apareció**.
- **No** —el código no compila, la suite no pasa, el endpoint queda roto sin eso— es parte del issue
  actual, y se dice explícitamente en el comentario de cierre.

Decir siempre en chat qué issues se crearon. Que una etapa crezca mientras se implementa es la regla
funcionando, no un desvío.

Esto cubre la regla del CLAUDE.md global sobre registrar mocks y stubs acordados: el issue en Jira
es el registro. En la memoria solo va la decisión de diseño, si la hubo.

### 11. Cuando un issue queda obsoleto

Un issue muere de dos formas: **otro lo sustituye**, o **una decisión posterior vuelve sus criterios
irrealizables**. Jira no tiene un estado de descarte: se marca con la etiqueta `descartada` y la
transición a `Listo`, que lo saca de las consultas activas sin borrarlo. Sigue buscable
(`labels = descartada`), enlazable y se revierte con la transición de vuelta.

Hazlo por tu cuenta, sin pedir permiso, y dilo en chat. Antes, deja un comentario con
`addCommentToJiraIssue`:

```markdown
## Descartada

**Fecha** — DD-MM-AAAA.
**Qué la sustituye** — la clave del issue que la reemplaza, o la decisión que la dejó sin sentido,
con su fecha y dónde está escrita.
**Dónde quedó cubierto lo que pedía** — el archivo, el test o el request de Bruno concreto. Si no
quedó cubierto en ninguna parte, decirlo con esas palabras: es un hueco, no un issue muerto.
```

Los tres campos son obligatorios. Sin el tercero, descartar un issue equivale a perder trabajo
pendiente sin que nadie lo note. Después, `editJiraIssue` para agregar la etiqueta `descartada`
(conservando las que tenga) y `transitionJiraIssue` con `{"id": "41"}`.

**El único caso que no descartas tú** es un issue en el que el usuario ya invirtió decisiones y
quiere revisar: si dudas, deja el estado y pregunta.

Se descarta directamente, en vez de proponerlo en chat, porque un aviso que vive en una
conversación se pierde al cerrarla mientras el issue sigue en el tablero: un aviso que solo se dice
una vez no es un estado.

## Crear un issue

La descripción va en ADF, no en markdown: con `contentFormat: "markdown"` Jira escapa `- [ ]` y los
criterios quedan como texto en vez de casillas.

1. Escribir la descripción en un archivo temporal, precedida de una línea `=== <clave>` (por ejemplo
   `=== nuevo`), con las secciones `## Contexto`, `## Depende de` (si hay) y
   `## Criterios de aceptación` con casillas `- [ ]`. El último criterio es la verificación del
   repo: tests de la capa que toca, `gradlew build` y `bru run` en verde, y si toca endpoints,
   `contrato-api.md` y `bruno/` al día.
2. `& .claude/skills/tareas-jira/md2adf.ps1 -Path <archivo> -OutDir <carpeta>` deja un
   `<clave>.json`. Soporta `## `, `> `, `- [ ]`, `- [x]`, `- `, párrafos, `código`, `**negrita**` y
   URLs; nada más.
3. `createJiraIssue`: `projectKey: "FIN"`, `issueTypeName`, `summary`, `parent` (clave del epic, si
   hay), `description` = contenido del `.json`, `contentFormat: "adf"`, `additional_fields` =
   `{"components": [{"name": "Backend"}], "priority": {"name": "Medium"}, "labels": [...]}`. Sin
   `transition` queda en `pendiente`; con `transition: {"id": "21"}` y la etiqueta `sprint-actual`,
   en `Por hacer`.
4. Si depende de otro, `createIssueLink` con `type: "Blocks"`, `inwardIssue` = el que bloquea,
   `outwardIssue` = el nuevo.

Los epics vigentes salen de `project = FIN AND issuetype = Epic AND statusCategory != Done`.

## Prohibiciones

- **Nunca** pasar un issue a `Listo` salvo para descartarlo (paso 11). Cerrar es del usuario.
- **Nunca** borrar un issue. Lo obsoleto se descarta con el paso 11, que es reversible y localizable.
- **Nunca** modificar issues distintos del que está en curso. Dos excepciones: crear nuevos, y las
  operaciones de tablero que el usuario pida explícitamente —repriorizar un conjunto, descartar lo
  obsoleto, reordenar una etapa—, que se ejecutan y se reportan una por una.
- **Nunca** escribir en Notion: es histórico de solo lectura.
- **Nunca** inventar el resultado de una verificación que no se corrió.

## Cuando Jira falla

Si el servidor MCP `atlassian` no responde o no está autenticado (`/mcp`), detener el ciclo y
decirlo. No implementar a ciegas un issue que no se pudo leer, y no guardar la evidencia en un
archivo local para sincronizarla después.

Si la escritura falla **después** de implementar, no revertir el código: reportar el fallo junto con
el contenido exacto que se iba a escribir, para pegarlo a mano.

### Un timeout no es un fallo

Una escritura que expira **puede haberse aplicado igual**: el servidor sigue procesando después de
que la llamada se rinde. Reintentarla a ciegas duplica el comentario o el issue.

Ante un timeout en cualquier escritura, **leer el issue con `getJiraIssue` antes de reintentar** (con
`comment` en los campos si era un comentario; por JQL sobre `summary` si era una creación), y decidir
con lo que diga: si ya está, no hay nada que reintentar; si quedó a medias, se completa con
`editJiraIssue`.
