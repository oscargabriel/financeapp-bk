---
name: tareas-notion
description: Usar cuando se pida la siguiente tarea, tomar o cerrar una tarea del tablero de financeapp-bk ("siguiente tarea", "qué sigue", "toma la tarea X", "cierra la tarea"), o cuando aparezca un pendiente que haya que registrar. Cubre el ciclo consumir → implementar → actualizar contra el tablero de Notion. No usar para tareas que no estén en el tablero ni en otros repositorios.
---

# Tareas en Notion — financeapp-bk

El tablero vive en Notion y es la fuente de verdad de **qué falta**. La memoria persistente del
proyecto guarda **por qué** se decidió algo, no la lista de pendientes.

## Dónde está el tablero

Página raíz: https://app.notion.com/p/3d9890a64a8d819a9830e3d3b6a58812

| Base | `data_source_url` |
|---|---|
| Tareas | `collection://8c19b32d-45a6-4e75-a627-b3f2ca9f25c1` |
| Etapas | `collection://72f4eed6-b7fe-47a0-81ca-b83589c7e3cd` |

Cada tarea tiene un `ID` autoincremental con prefijo `FA` (FA-1, FA-2…). Úsalo al referirte a una
tarea en el chat y en los mensajes de commit.

## El ciclo

### 1. Consumir

`notion-query-data-sources` en modo `rows` sobre `Tareas`, filtro `Estado = Lista`, orden por
`Prioridad` y luego por `ID`, límite 5. Mostrar las candidatas con `ID`, título, tipo y prioridad.
Si el usuario pidió "la siguiente", tomar la primera; si no, esperar a que elija.

**Los dos criterios de orden son `ascending`**, no `descending`. Notion ordena los `select` por el
orden de sus opciones, y `Alta` es la primera: pedir `descending` devuelve las de prioridad baja
arriba. El `ID` desempata dentro de un mismo nivel, y hace falta porque la mayoría de las tareas
comparten prioridad: sin él, "la siguiente" no es una pregunta con una sola respuesta.

### Qué significa cada prioridad

| Nivel | Cuándo | Cuántas esperar |
|---|---|---|
| `Alta` | Sale primero: un bug, una corrección urgente, o que el usuario diga que esa va antes que las demás. | Pocas; lo normal es una |
| `Media` | **El valor por defecto de toda tarea nueva.** El trabajo ordinario de las etapas. | La mayoría |
| `Baja` | Accesorio. Se hace cuando no queda nada en `Media`. | Las que sobran |

No hay un nivel por encima de `Alta` a propósito: un nivel nuevo se infla igual que el anterior si
no hay una regla de uso, y `Alta` con la regla escrita ya cubre el caso de "esta va primero". Si la
mayoría de las tareas tomables acaba en `Alta`, la prioridad dejó de discriminar: se recalibra, no
se agregan niveles.

Si el plan de Notion empieza a limitar `query_data_sources` en modo `rows`, la vista `Siguiente` ya
trae el mismo filtro y el mismo orden en dos criterios, y el modo `view` no consume cuota:

```
mode: view
view_url: https://app.notion.com/7c9aba57a3264bb794be111685f2d646?v=3d9890a64a8d817e8227000c98c70f2a
```

`notion-fetch` de la página elegida para leer `## Contexto` y `## Criterios de aceptación`.

**Si los criterios no permiten decidir cuándo la tarea está hecha, no la tomes.** Deja la duda en un
comentario con `notion-create-comment`, mantén el estado en `Lista` y dilo en chat.

#### Dependencias entre tareas

El orden que da la consulta —prioridad, luego `ID`— no conoce las ataduras reales. Una tarea puede
declarar en su cuerpo un encabezado `## Depende de` con los `ID` que tienen que estar cerrados antes:

```markdown
## Depende de
- FA-44 — confirma los parámetros de TLS que este archivo tiene que escribir
```

**Antes de tomar una tarea, leer ese bloque.** Si alguna de las tareas listadas no está en
`Por revisar`, `Hecha` o `Descartada`, no la tomes: dilo en chat y ofrece la que la desbloquea.

### 2. Tomar

`notion-update-page` → `Estado = En curso`. Solo una tarea en curso a la vez: si ya hay otra,
avisar y preguntar antes de tomar la nueva.

### 3. Implementar

Aquí manda el flujo que ya tiene el proyecto. Este skill no lo reimplementa, lo delega:

- `superpowers:test-driven-development` y `java-testing` para el ciclo RED-GREEN-REFACTOR y la
  ubicación de los tests por capa hexagonal.
- Si la tarea agrega o cambia endpoints, el request de `bruno/` con su bloque `runtime.assertions`
  se escribe **en el mismo ciclo RED**, fallando antes de que exista el endpoint. Ver la memoria
  `convencion-coleccion-bruno`.

**Antes de escribir código, cargar con `Skill` las skills de dominio que correspondan** a lo que la
tarea toca. Sus descripciones se disparan con "diseñar" o "configurar", no con "implementar FA-n",
así que sin este paso no se leen: el constructor manual en todos los beans (corregido en el PR #16)
salió de ignorar `java-architect`, que pedía `@AllArgsConstructor` desde antes del primer commit.

| Si la tarea toca… | Cargar |
|---|---|
| Un puerto, caso de uso, adapter, controlador, record de dominio o DTO | `java-architect` |
| `ErrorCodes`, `BadRequestException`, `WebExceptionHandler` o errores en la cadena reactiva | `java-exceptions` |
| `SecurityConfig`, JWT, Basic, CORS o rutas protegidas | `java-security` |
| `LoggingFilter`, `ReactorMdcHook`, MDC o niveles de log | `java-logging` |
| Un bug en un pipeline `Mono`/`Flux` | `java-debugging` |
| Cualquier archivo de `bruno/` | `bruno-cli` |

Si la tarea no toca nada de la tabla, no se carga ninguna; si toca varias filas, se cargan todas.
Las reglas de la skill ceden ante `CLAUDE.md` cuando chocan.

### 4. Verificar

`.\gradlew.bat build` — la suite completa más el umbral de cobertura, que `test` no exige. No
necesita Docker ni base.

`bru run . -r --env local` desde `bruno/` con la app levantada — **siempre**, no solo cuando la
tarea toca endpoints. Es la única capa que ejerce el SQL de las vistas y la base real, así que una
tarea que cambie una consulta, el esquema o el escenario de datos sin pasar por aquí se cierra sin
verificar. Si `bru` no está en el PATH, eso es un bloqueo que se reporta, no un paso que se omite.

Este proyecto **no tiene checkstyle ni linter**: no reportes un paso de lint que no corrió.

**Sin verde no se pasa al paso 5.** No se cierra una tarea con tests rojos ni con una corrida de
`bru` que no se ejecutó.

### 5. Entregar

Con el paso 4 en verde, sin preguntar ni esperar revisión:

1. `git checkout -b feature/fa-<n>-<slug>` desde `dev`.
2. `git add` **solo** de los archivos de la tarea: el working tree suele tener cambios del usuario
   que no son de la entrega.
3. Commit con el `ID` en el título (`FA-<n> …`) y push.
4. `gh pr create --base dev` con resumen, cambios y la evidencia del paso 4.
5. `pwsh -NoProfile -File .claude/scripts/merge-pr-dev.ps1 <número>`: comprueba que la base sea
   `dev`, mergea con merge commit, vuelve a `dev` con `pull --ff-only` y aparta y repone los
   cambios locales que estorben al checkout. No usar `gh pr merge` directo: el permiso del proyecto
   autoriza el script, no el comando suelto.

El usuario revisa los PRs después en GitHub. Esto reemplaza en este repo al skill global `entregar`,
que nunca mergea. Los PRs contra `main` **no** se mergean nunca: el script los rechaza.

### 6. Cerrar

`notion-update-page` sobre la tarea en curso:

- `Estado = Por revisar`
- `Evidencia` — conteo de tests de la suite y resultado de `bru run`, con números reales tomados de
  la salida, nunca estimados, y el número del PR
- `Commit` — sha corto del commit de la tarea

Después `notion-create-comment` con el resumen: qué se implementó, qué archivos, qué quedó fuera y
**qué skills se cargaron en el paso 3**.

Reportar en chat el enlace de la tarea.

### 7. Descubrimientos — una tarea, un resultado

**Nunca agrandes la tarea en curso.** Si durante la implementación aparece algo que sus criterios de
aceptación no cubren —parametrizar los mensajes del bot, un filtro de seguridad contra inyección,
cualquier pieza que sea un feature en sí misma— se crea una tarea nueva, no se mete en esta.

El discriminador es una sola pregunta:

> **¿Puedo dejar esta tarea en verde y cerrada sin eso?**

- **Sí** → tarea nueva con `notion-create-pages` en `Tareas`: `Estado = Backlog`, `Tipo`
  correspondiente, `Prioridad = Media` salvo que sea un bug o una urgencia, y en el cuerpo bajo
  `## Contexto` **por qué surgió y en qué tarea apareció**.
- **No** —el código no compila, la suite no pasa, el endpoint queda roto sin eso— es parte de la
  tarea actual, y se dice explícitamente al cerrarla en `## Notas de implementación`.

Decir siempre en chat qué tareas se crearon. Que una etapa crezca mientras se implementa es la regla
funcionando, no un desvío.

Esto cubre la regla del CLAUDE.md global sobre registrar mocks y stubs acordados: la tarea en Notion
es el registro. En la memoria solo va la decisión de diseño, si la hubo.

### 8. Cuando una tarea queda obsoleta

Una tarea muere de dos formas: **otra la sustituye**, o **una decisión posterior vuelve sus criterios
irrealizables**. En los dos casos pasa a `Estado = Descartada`, que la saca de las vistas activas sin
sacarla de la base: sigue buscable, sigue enlazable y se revierte cambiando el estado.

Hazlo por tu cuenta, sin pedir permiso, y dilo en chat. Antes, escribe al **principio** del cuerpo:

```markdown
## Descartada

**Fecha** — DD-MM-AAAA.
**Qué la sustituye** — el `ID` de la tarea que la reemplaza, o la decisión que la dejó sin sentido,
con su fecha y dónde está escrita.
**Dónde quedó cubierto lo que pedía** — el archivo, el test o el request de Bruno concreto. Si no
quedó cubierto en ninguna parte, decirlo con esas palabras: es un hueco, no una tarea muerta.
```

Los tres campos son obligatorios. Sin el tercero, descartar una tarea equivale a perder trabajo
pendiente sin que nadie lo note.

**El único caso que no descartas tú** es una tarea en la que el usuario ya invirtió decisiones y
quiere revisar: si dudas, deja el estado y pregunta.

Se descarta directamente, en vez de proponerlo en chat, porque un aviso que vive en una
conversación se pierde al cerrarla mientras la fila sigue en el tablero: un aviso que solo se dice
una vez no es un estado.

## Prohibiciones

- **Nunca** poner `Estado = Hecha`. Ese paso es del usuario.
- **Nunca** borrar ni archivar una tarea hacia la papelera de Notion. Lo obsoleto se marca
  `Descartada` con su bloque `## Descartada`, que es reversible y localizable; la papelera no.
- **Nunca** modificar tareas distintas de la que está en curso. Dos excepciones: crear nuevas, y las
  operaciones de tablero que el usuario pida explícitamente —repriorizar un conjunto, descartar lo
  obsoleto, reordenar una etapa—, que se ejecutan y se reportan una por una.
- **Nunca** inventar el resultado de una verificación que no se corrió.

## Cuando Notion falla

Si el servidor MCP no responde o no está autenticado, detener el ciclo y decirlo. No implementar a
ciegas una tarea que no se pudo leer, y no guardar la evidencia en un archivo local para
sincronizarla después.

Si la escritura falla **después** de implementar, no revertir el código: reportar el fallo junto con
el contenido exacto que se iba a escribir, para pegarlo a mano.

### Un timeout no es un fallo

Una escritura de contenido que expira **puede haberse aplicado igual**: el servidor sigue procesando
después de que la llamada se rinde. Reintentarla a ciegas duplica el bloque dentro de la página.

Ante un timeout en cualquier escritura, **leer la página con `notion-fetch` antes de reintentar**, y
decidir con lo que diga: si el contenido ya está, no hay nada que reintentar; si está a medias, se
corrige con `replace_content` sobre el cuerpo completo, no con otro `insert_content`.
