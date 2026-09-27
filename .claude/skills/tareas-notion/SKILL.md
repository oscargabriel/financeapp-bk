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

Se recalibró el 18-09-2026, cuando 8 de las 12 tareas tomables eran `Alta` y la prioridad había
dejado de discriminar. No se agregó un nivel por encima a propósito: un nivel nuevo se infla igual
que el anterior si no hay una regla de uso, y `Alta` con la regla escrita ya cubre el caso de "esta
va primero".

Si el plan de Notion empieza a limitar `query_data_sources` en modo `rows`, la vista `Siguiente` ya
trae el mismo filtro y el mismo orden en dos criterios, y el modo `view` no consume cuota:

```
mode: view
view_url: https://app.notion.com/7c9aba57a3264bb794be111685f2d646?v=3d9890a64a8d817e8227000c98c70f2a
```

`notion-fetch` de la página elegida para leer `## Contexto` y `## Criterios de aceptación`.

**Si los criterios no permiten decidir cuándo la tarea está hecha, no la tomes.** Deja la duda en un
comentario con `notion-create-comment`, mantén el estado en `Lista` y dilo en chat.

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

### 4. Verificar

`.\gradlew.bat test` — la suite completa. No necesita Docker ni base: desde el 12-09-2026 la
estrategia es de dos capas y Testcontainers salió.

`bru run . -r --env local` desde `bruno/` con la app levantada — **siempre**, no solo cuando la
tarea toca endpoints. Es la única capa que ejerce el SQL de las vistas y la base real, así que una
tarea que cambie una consulta, el esquema o el escenario de datos sin pasar por aquí se cierra sin
verificar. Si `bru` no está en el PATH, eso es un bloqueo que se reporta, no un paso que se omite.

Este proyecto **no tiene checkstyle ni linter**: no reportes un paso de lint que no corrió.

**Sin verde no se pasa al paso 5.** No se cierra una tarea con tests rojos ni con una corrida de
`bru` que no se ejecutó.

### 5. Cerrar

`notion-update-page` sobre la tarea en curso:

- `Estado = Por revisar`
- `Evidencia` — conteo de tests de la suite y resultado de `bru run`, con números reales tomados de
  la salida, nunca estimados
- `Commit` — sha corto, si ya se commiteó

Después `notion-create-comment` con el resumen: qué se implementó, qué archivos, qué quedó fuera.

Reportar en chat el enlace de la tarea.

### 6. Descubrimientos — una tarea, un resultado

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

## Prohibiciones

- **Nunca** poner `Estado = Hecha`. Ese paso es del usuario.
- **Nunca** archivar, borrar ni descartar una tarea. Si algo parece obsoleto, proponerlo en chat.
- **Nunca** modificar tareas distintas de la que está en curso, salvo crear nuevas en `Backlog`.
- **Nunca** inventar el resultado de una verificación que no se corrió.

## Cuando Notion falla

Si el servidor MCP no responde o no está autenticado, detener el ciclo y decirlo. No implementar a
ciegas una tarea que no se pudo leer, y no guardar la evidencia en un archivo local para
sincronizarla después.

Si la escritura falla **después** de implementar, no revertir el código: reportar el fallo junto con
el contenido exacto que se iba a escribir, para pegarlo a mano.
