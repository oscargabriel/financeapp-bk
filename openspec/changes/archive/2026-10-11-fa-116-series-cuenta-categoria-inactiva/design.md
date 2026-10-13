# Design

## Context

- La puesta al día (`SeriesAlDia`) lee las series sin fin activas del usuario
  (`findOpenActiveByUser`) y les crea las ocurrencias que faltan. Toda lectura de saldos o
  movimientos la llama antes de consultar.
- Validar la cuenta y la categoría ya pasa en el alta y en el PATCH de una serie. La puesta al día y
  los movimientos que ya existían no lo hacen.
- El listado (`ACTIVAS`) define qué es una serie activa: `status = 'ACTIVE'` y, además, que tenga
  una ocurrencia futura o que no tenga fin.
- Las categorías solo se borran: no se pueden desactivar. Una cuenta borrada no se puede reactivar.
  Una cuenta desactivada sí.
- `DeleteCategoryUseCase` borra con un solo `UPDATE`, sin leer antes la categoría.

## Goals / Non-Goals

**Goals:** que ninguna serie cree ocurrencias sobre una cuenta o una categoría que ya no admite
movimientos nuevos.

**Non-Goals:** reparar las series de datos viejos que ya están en ese estado. Este cambio solo hace
que dejen de crecer.

## Decisions

### Rechazar el cambio con 409, no cancelar ni pausar la serie

Decidido con el usuario el 11-10-2026. Es el mismo criterio de FA-115: el sistema no decide por el
usuario qué pasa con su dinero. El usuario resuelve cada serie con `DELETE /api/recurrences/{id}`, o
con `PATCH /api/recurrences/{id}` para moverla a otra cuenta o categoría, y después repite el cambio.

**Descartado: cancelar la serie al desactivar o borrar.**
- Es irreversible: si el usuario reactiva la cuenta, la serie no vuelve.
- Borra ocurrencias futuras que pueden estar editadas a mano.
- Un PATCH de la cuenta quedaría escribiendo en otro recurso sin que nadie lo pida.

**Descartado: pausar la serie.**
- Obliga a decidir si al reactivar se crean las atrasadas.
- Con una cuenta o una categoría borradas, la pausa es para siempre: la serie quedaría en el
  listado sin avanzar nunca.
- Las ocurrencias futuras que ya existen se quedarían igual.

### Qué cuenta como serie activa

Se usa la misma definición del listado, con la condición nueva sobre la cuenta y la categoría (ver
el requisito modificado de `recurrentes`). Así las tres reglas —desactivar y borrar la cuenta,
borrar la categoría— y el listado hablan de lo mismo: **si el 409 dice «1 serie activa», esa serie
está en `GET /api/recurrences`** y el usuario la puede cancelar.

- Una serie con fin y sin ocurrencias futuras no bloquea nada: ya terminó.
- Una serie sin fin sobre una cuenta ya desactivada no aparece en el listado ni bloquea su borrado.
  Si bloqueara, el usuario no tendría cómo encontrarla para cancelarla.

La condición vive en un solo fragmento SQL del adapter, que comparten el listado y los dos conteos.

### Errores: en qué campo y cuántos

- **Desactivar:** el error va en `isActive`, el campo del parche que no se puede aplicar. Es como
  el 409 de `currencyCode` cuando la cuenta tiene movimientos.
- **Borrar una cuenta:** sigue habiendo un solo error en `id`. Su `description` suma las series y
  los programados: «tiene 1 serie activa y 1 movimiento programado». Dos errores en el mismo campo
  dirían lo mismo dos veces, porque toda serie sin fin activa tiene una ocurrencia programada.
- **Borrar una categoría:** el error va en `id`, igual que el de la cuenta.
- No hay códigos nuevos: `RESOURCE_IN_USE` es el de los otros dos 409.

### Dónde se comprueba

- `RecurrenceRepositoryPort` gana `countActiveByAccount(accountId, userId)` y
  `countActiveByCategory(categoryId, userId)`, los dos con `Mono<Long>`. Filtrar por usuario evita
  que una categoría ajena dé 409 en vez de 404.
- `UpdateAccountUseCase` cuenta las series solo cuando el parche pide `isActive: false` y la cuenta
  guardada está activa. Pedir el estado que ya tiene sigue respondiendo 200 sin consultar nada.
- `DeleteAccountUseCase` las cuenta junto con los programados.
- `DeleteCategoryUseCase` cuenta antes del `UPDATE`. Con cero series sigue como hoy: cero filas
  afectadas es 404. El conteo solo mira categorías no borradas, así que una ya borrada sigue dando
  404 aunque tenga series viejas.

**Descartado: un `NOT EXISTS` dentro del `UPDATE` de la categoría.** Con cero filas afectadas no se
podría distinguir el 404 del 409 sin otra lectura, y no diría cuántas series hay.

### La puesta al día filtra en la consulta

`findOpenActiveByUser` se une a `accounts` y a `categories` y solo devuelve las series con la cuenta
activa y viva y la categoría viva. Si el filtro estuviera en Java, haría falta leer las dos tablas
aparte. La serie no se marca de ninguna forma: si la cuenta se reactiva, vuelve a ponerse al día.

## Risks / Trade-offs

- **[Carrera]** Una serie creada entre el conteo y la desactivación, o el borrado, queda sobre una
  cuenta o categoría no válida. Es la misma ventana de FA-115. → La puesta al día la ignora, así que
  no crea nada nuevo.
- **[Reactivar una cuenta con una serie vieja]** Una serie sin fin de datos anteriores sobre una
  cuenta desactivada no crea ocurrencias mientras siga así. Si la cuenta se reactiva, la siguiente
  lectura crea de golpe todas las atrasadas, hasta el tope de 500. → Se acepta. Solo pasa con datos
  de antes de este cambio, y es lo mismo que hace hoy una serie que lleva tiempo sin leerse.
- **[Series viejas invisibles]** Una serie de datos viejos sobre una cuenta borrada sale del
  listado y queda `ACTIVE` para siempre, sin crear nada. → Se acepta: se comporta como una serie
  terminada, y sus ocurrencias pasadas conservan su `recurrenceId`.
