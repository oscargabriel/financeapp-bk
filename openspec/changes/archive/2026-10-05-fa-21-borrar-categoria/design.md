# Design

## Context

`finance.categories` ya tiene `deleted_at`, y todo lo que lee categorías vivas lo filtra:
el listado, el `SELECT` del PATCH y las referencias contra las que se validan los movimientos
(`findActiveByUser`). El índice único del nombre es parcial (`WHERE deleted_at IS NULL`). Así, marcar
la columna basta para sacar la categoría del listado, liberar el nombre e impedir que se use en
un movimiento. No hay que tocar ninguna de esas consultas.

Lo que **no** filtra `deleted_at` son las lecturas de historia: el `LEFT JOIN` del reporte de
movimientos y el `JOIN` de `v_monthly_spending_by_category`. El PATCH de movimientos conserva la
categoría guardada sin revalidarla mientras no cambie el tipo (`UpdateTransactionUseCase.categoria`).

El borrado de movimientos (`DeleteTransactionUseCase`) dejó el patrón: una sola sentencia
filtrada por usuario, y si afecta cero filas, la respuesta es 404.

## Goals / Non-Goals

**Goals:**
- Borrar sin romper ninguna lectura de historia ni ningún movimiento existente.

**Non-Goals:**
- Tocar el esquema, las vistas o las consultas de reportes.

## Decisions

### Un solo `UPDATE`, sin lectura previa

`UPDATE finance.categories SET deleted_at = now() WHERE id = :id AND user_id = :userId AND
deleted_at IS NULL`. El puerto devuelve si afectó una fila, y cero filas es el 404 de los tres
casos (inexistente, ya borrada, ajena). Es el patrón de `DeleteTransactionUseCase`.

Se descartó leer primero con `findActiveByIdAndUser`, como hace el PATCH. El PATCH necesita la
fila para fusionar el parche y comprobar el alcance; el borrado no necesita nada de ella, y la
lectura previa solo agregaría una ida a la base y una carrera entre el `SELECT` y el `UPDATE`.

El 404 sale del mismo método que el del PATCH, `UpdateCategoryUseCase.noEncontrada()`, que pasa de
privado a visible en el paquete. Así los dos dicen lo mismo, igual que `DeleteTransactionUseCase`
reutiliza `UpdateTransactionUseCase.noEncontrado()`.

### Se borra aunque tenga movimientos

El borrado lógico existe precisamente para eso (comentario de `categories.deleted_at` en
`schema.sql`). Se descartó responder 409 `RESOURCE_IN_USE` como en el cambio de alcance. Allí el
cambio dejaría movimientos inválidos; aquí los movimientos no cambian y la historia se sigue
leyendo. Obligar a borrar o mover antes los movimientos sería pedirle al usuario reescribir su
historia para ordenar el selector de categorías.

### La historia muestra la categoría borrada

El reporte de movimientos y `v_monthly_spending_by_category` siguen mostrando la categoría borrada
con su nombre, y no se cambia ninguna consulta. Se descartó filtrarla:

- En la vista, el desglose dejaría de sumar el total del mes de `v_monthly_spending` y
  `share_of_month` no llegaría a 100.
- En el reporte, los movimientos quedarían con `categoryName` en `null`, que el contrato reserva
  para las transferencias.

La decisión sobre la vista se escribe en `docs/database/modelo-datos.md`, junto a su
descripción. Como ningún endpoint la lee, la comprobación es una consulta con psql contra la base
local después de la corrida de Bruno.

### 204 sin cuerpo

Igual que `DELETE /api/transactions/{id}`. Se descartó devolver la categoría borrada: el cliente
ya la tiene, y un 200 con cuerpo sugeriría que sigue siendo un recurso consultable.

### Bruno: el borrado va al final de `bruno/categories/`

Los requests nuevos van después del seq 41, porque el borrado de `Huerta` necesita el gasto que
crea `gasto-en-huerta` (seq 36). Ese request guarda ahora el id del gasto en `idGastoHuerta` para
modificarlo después del borrado. `categorias-tras-modificar` (seq 25) cuenta las categorías antes
de cualquier borrado, así que no cambia.

## Risks / Trade-offs

- [El hueco de `sort_order` que deja la borrada no se compacta] → No afecta el orden relativo, y
  el alta calcula el siguiente con `max(sort_order)` de las vivas.
- [Un tope de gasto de una categoría borrada seguiría en la vista] → No hay API de metas; queda
  anotado como fuera de alcance para la tarea que la cree.

## Migration Plan

Sin cambios de esquema: el despliegue es solo la imagen nueva.
