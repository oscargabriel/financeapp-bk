# Tablero de tareas en Notion para financeapp-bk

Fecha: 2026-09-11
Estado: aprobado, en implementación

## Problema

El proyecto avanza por etapas y sus pendientes viven hoy en la memoria persistente de Claude
(`pendientes-etapa-conexion.md`). Eso funciona como bitácora de decisiones pero no como cola de
trabajo: no se puede filtrar, no tiene estados, y no se ve fuera de una sesión de Claude. Se quiere
un tablero en Notion que Claude consuma, implemente y actualice, aislado a este repositorio para no
contaminar otros proyectos donde no se usa Notion.

## Decisiones tomadas

| # | Decisión | Alternativas descartadas |
|---|---|---|
| 1 | Servidor MCP **hospedado** de Notion (`https://mcp.notion.com/mcp`, OAuth) | Self-hosted `@notionhq/notion-mcp-server` con token de integración interna; script propio contra la API REST |
| 2 | Config con alcance **`project`** (`.mcp.json` versionado en la raíz) | `--scope local` (no versionado); `--scope user` (contaminaría otros repos) |
| 3 | Dos niveles: **Etapa → Tarea** | Un nivel plano; tarea = etapa completa; jerarquía de tres niveles |
| 4 | Escritura **autónoma sobre la tarea en curso** | Confirmación previa por escritura; solo estado y comentarios; solo lectura |
| 5 | **Notion = qué falta, memoria = por qué** | Duplicar en ambos; memoria como fuente de verdad |

El aislamiento de datos por integración restringida (compartir solo una página con una integración
interna) se consideró innecesario: los tableros de trabajo del usuario no están en Notion, así que
el OAuth de la cuenta personal no cruza ningún dato ajeno.

## Arquitectura y aislamiento

Archivos creados, todos dentro del repo:

| Archivo | Versionado | Contenido |
|---|---|---|
| `.mcp.json` | sí | servidor `notion`, transport `http`. Sin secretos |
| `.claude/settings.json` | sí | `enabledMcpjsonServers: ["notion"]` + allowlist de herramientas |
| `CLAUDE.md` | sí | convención del proyecto |
| `.claude/skills/tareas-notion/SKILL.md` | sí | el ciclo consumir → implementar → actualizar |

Tres capas de aislamiento, ninguna global:

1. `.mcp.json` vive en la raíz del repo. Claude Code solo lo lee con este directorio como cwd.
2. `enabledMcpjsonServers` va en `.claude/settings.json` del proyecto, nunca en
   `~/.claude/settings.json`. Se usa la allowlist por nombre en vez de
   `enableAllProjectMcpServers`, que aprobaría cualquier servidor que alguien añada después.
3. Las credenciales OAuth las guarda Claude Code por usuario tras `claude mcp login notion`. No
   entran al repo, así que el `.mcp.json` versionado es publicable.

### Limitación conocida de los permisos

Los permisos de Claude Code hacen match por nombre de herramienta, no por argumentos. Archivar una
página en la API de Notion es la misma llamada `notion-update-page` con otro campo, así que
allowlistearla para permitir la escritura autónoma también permite archivar. La regla "Claude nunca
archiva ni descarta una tarea" queda como convención en el skill, no como barrera de permisos.
Aceptado: el tablero es personal y Notion conserva historial y papelera.

`notion-create-database` y `notion-create-view` quedan **fuera** de la allowlist a propósito: son
operaciones de montaje inicial y deben pedir confirmación explícita.

## Modelo de datos

Una página raíz `financeapp-bk` en el workspace personal, con dos bases dentro.

### Base `Etapas`

| Propiedad | Tipo | Notas |
|---|---|---|
| `Nombre` | title | `Etapa 4 — Transacciones` |
| `Número` | number | orden |
| `Estado` | status | `Planeada` · `En curso` · `Cerrada` |
| `Objetivo` | rich text | una frase |
| `Cierre` | date | fecha de cierre |

### Base `Tareas`

| Propiedad | Tipo | Notas |
|---|---|---|
| `Título` | title | imperativo y verificable |
| `Etapa` | relation → Etapas | |
| `Estado` | status | `Backlog` · `Lista` · `En curso` · `Por revisar` · `Hecha` · `Bloqueada` |
| `Tipo` | select | `Feature` · `Cobertura` · `Deuda técnica` · `Bug` · `Infra` · `Documentación` |
| `Prioridad` | select | `Alta` · `Media` · `Baja` |
| `Bloqueada por` | relation → Tareas | autorrelación para dependencias |
| `Evidencia` | rich text | se llena al cerrar: conteo de tests, resultado de `bru run` |
| `Commit` | rich text | sha corto |
| `Creado por` | created_by | nativo; distingue lo que creó Claude de lo que creaste tú |
| `Actualizada` | last_edited_time | nativo |

Semántica de los estados:

- `Backlog` — existe pero no está refinada; Claude no la toma.
- `Lista` — refinada, con criterios de aceptación en el cuerpo. Es la cola de la que se consume.
- `En curso` — Claude la está implementando. Debería haber una sola.
- `Por revisar` — implementada y en verde. Espera tu visto bueno.
- `Hecha` — **solo la mueve el usuario**.
- `Bloqueada` — con el motivo en un comentario.

Cuerpo de cada tarea: `## Contexto` (por qué existe), `## Criterios de aceptación` (checklist
verificable), `## Notas de implementación` (lo escribe Claude al cerrar).

Vistas: un tablero agrupado por `Estado`, y una vista `Siguiente` filtrada a `Estado = Lista`
ordenada por `Prioridad` y luego `Etapa`.

## El ciclo de trabajo

1. **Consumir** — `notion-query-data-sources` sobre `Tareas` con `Estado = Lista`, ordenado por
   prioridad, límite 5. Se muestran las candidatas; el usuario elige o pide la primera.
   `notion-fetch` de la página para leer contexto y criterios.
2. **Tomar** — `notion-update-page` → `Estado = En curso`. *(escritura 1)*
3. **Implementar** — manda el flujo que ya existe en el proyecto: `superpowers:test-driven-development`
   y `java-testing` para el ciclo RED-GREEN-REFACTOR, y si la tarea agrega endpoints, el request
   Bruno con sus `runtime.assertions` se escribe en el mismo ciclo RED. El skill de Notion no
   reimplementa nada de esto: delega.
4. **Verificar** — `/verificar` (suite Gradle + checkstyle) y, si toca endpoints,
   `bru run . -r --env local` desde `bruno/`. Sin verde no se avanza al paso 5.
5. **Cerrar** — `notion-update-page` → `Estado = Por revisar`, `Evidencia` con el conteo de tests y
   el resultado de `bru run`, `Commit` si ya se commiteó; `notion-create-comment` con el resumen de
   qué se hizo. *(escrituras 2 y 3)*
6. **Descubrimientos** — cada pendiente que aparezca en el camino se crea con `notion-create-pages`
   en `Tareas`, `Estado = Backlog`, con el contexto en el cuerpo, y se reporta en chat qué se creó.
   *(escritura 4)*
7. El usuario mueve `Por revisar` → `Hecha`.

Prohibiciones del skill: Claude no pone `Hecha`, no archiva, no descarta y no cambia tareas
distintas de la que está en curso.

## Reparto con la memoria persistente

Notion se queda con lo accionable; la memoria, con el porqué.

- `pendientes-etapa-conexion.md` se reescribe: conserva las decisiones (sin Flyway y por qué,
  credenciales en `application-local.yaml`, `src/test/resources/application.yaml` autocontenido,
  Testcontainers exige Docker, `userId` en la URL con IDOR aceptado) y pierde la sección
  "Pendiente de cobertura", que pasa a ser tareas.
- Nueva memoria `tablero-notion` (tipo `project`) con la URL de la página raíz, los IDs de las dos
  data sources y la regla del reparto.
- La regla del CLAUDE.md global que exige registrar cada mock/stub acordado en una memoria tipo
  `project` se satisface creando la tarea en Notion; la memoria apunta al tablero en vez de listar
  pendientes. Esto queda escrito en el `CLAUDE.md` del proyecto para que se lea como adaptación
  consciente y no como omisión.

### Seed inicial

Etapas 1–3 en `Cerrada`, y estas tareas derivadas de los pendientes reales de la memoria:

| Título | Tipo | Prioridad | Estado |
|---|---|---|---|
| Corregir el password de `spring.r2dbc` en `application-local.yaml` y confirmar `bruno/system/` 3/3 | Infra | Alta | Lista |
| Cubrir el camino `postgres UP` de `PostgresHealthCheckAdapter` con Testcontainers | Cobertura | Alta | Lista |
| IT end-to-end de `GET /users/{userId}/monthly-spending` con datos (servidor real + contenedor) | Cobertura | Alta | Lista |
| Test de arranque con `startup.db-check` activo | Cobertura | Media | Lista |
| Decidir la estrategia de migraciones de esquema (Flyway sobre JDBC vs. manual) | Deuda técnica | Media | Backlog |
| Validar el `userId` de la ruta contra el JWT y agregar el request Bruno de 403 | Feature | Baja | Bloqueada |

## Errores

- **MCP caído o sin autenticar**: el ciclo se detiene y se dice en chat. No se implementa a ciegas
  una tarea que no se pudo leer, ni se guarda evidencia en un archivo local para sincronizar
  después — esa es justamente la duplicación que se descartó.
- **Escritura fallida tras implementar**: el código no se revierte. Se reporta el fallo junto con el
  contenido exacto que se iba a escribir, para pegarlo a mano en Notion.
- **Tarea ambigua**: si los criterios de aceptación no permiten decidir cuándo está hecha, no se
  toma. Se comenta la duda en la tarea y se deja en `Lista`.

## Prueba de aceptación del montaje

Recorrer los siete pasos con la primera tarea del seed (password local + `bruno/system/` en 3/3),
que es corta, real y ya tiene criterio de éxito objetivo. El montaje se da por bueno si al final la
tarea quedó en `Por revisar` con `Evidencia` y un comentario de cierre.

## Desviaciones al construirlo (2026-09-11)

El tablero quedó montado. Cuatro diferencias respecto a lo diseñado arriba, todas verificadas contra
el comportamiento real de la herramienta:

1. **`Estado` es `select`, no `status`.** El DDL de `notion-create-database` no permite definir las
   opciones de una propiedad `status`; habría creado los grupos por defecto de Notion
   (*Not started / In progress / Done*). Con `select` las seis opciones quedan exactamente como se
   diseñaron. Un tablero agrupado por `select` funciona igual.
2. **No existe `Creado por`.** El DDL no soporta el tipo `created_by`. Notion registra el autor de
   cada página de forma nativa y se ve en la UI, así que la necesidad está cubierta sin la columna.
3. **No existe `Bloqueada por`.** La autorrelación no se creó porque no hay a qué apuntar: la tarea
   que bloquea a FA-6 es "que entre JWT", y esa etapa no está planificada. El motivo del bloqueo
   está escrito en el cuerpo de FA-6. Se añade con `notion-update-data-source` cuando exista la
   tarea bloqueante.
4. **Se añadió `ID`** (`UNIQUE_ID` con prefijo `FA`), que no estaba en el diseño. Permite referirse
   a una tarea como FA-3 en el chat y en los mensajes de commit, en vez de por el título completo.

Sobre el plan de Notion: `query_data_sources` está `available_with_limit` y `ai_search` requiere
plan de pago. El paso de consumir usa `query_data_sources` en modo `rows`, que funciona; si el
límite del plan llega a molestar, la salida es la vista `Siguiente` en modo `view`, que la
documentación describe como sin cuota en todos los planes.

`Prioridad` se ordena **ascendente** para obtener Alta primero: Notion ordena los `select` por el
orden de sus opciones, no alfabéticamente.

## Fuera de alcance

Sincronización bidireccional automática, webhooks de Notion hacia el repo, métricas o reportes de
velocidad, y cualquier automatización que corra sin una sesión de Claude abierta.
