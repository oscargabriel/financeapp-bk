# Proposal

Origen: FA-20. Al tomarla se acordó en el chat (05-10-2026) que el parche no puede vaciar `icon`
ni `color`, y que cambiar `appliesTo` a un alcance que deja movimientos de la categoría con un
tipo incompatible se rechaza con 409. El usuario pidió además que el alta exija icono y color;
eso quedó como tarea aparte, FA-66.

## Why

Una categoría creada con un nombre o un color equivocados, o una de la semilla que el usuario
quiere adaptar, solo se puede corregir hoy con psql.

## What Changes

- `PATCH /api/categories/{id}`: modifica cualquier subconjunto de `name`, `appliesTo`, `icon` y
  `color`. Lo ausente o en `null` se conserva. Responde 200 con la categoría completa.
- Cada campo enviado cumple el formato del alta, y además no puede ir en blanco: el parche no vacía
  `icon` ni `color`, solo los reemplaza. Un parche sin campos responde 400 sobre `body`.
- Las categorías de la semilla se modifican igual que las propias, y `isSystem` no cambia: marca el
  origen de la fila, como dice el comentario de la columna `is_system`.
- Un id mal formado responde 400 sobre `id`. Una categoría inexistente, borrada o de otro usuario
  responde 404 sobre `id`, sin distinguir entre los tres casos.
- Un nombre que ya usa otra categoría viva del usuario responde 409 `DUPLICATE_RESOURCE` sobre
  `name`. Cambiar solo las mayúsculas del nombre propio no es un choque.
- Cambiar `appliesTo` a `EXPENSE` cuando la categoría tiene ingresos, o a `INCOME` cuando tiene
  gastos, responde 409 `RESOURCE_IN_USE` sobre `appliesTo` y no modifica nada. Pasar a `BOTH`
  siempre se permite.
- `ErrorCodes` gana `RESOURCE_IN_USE`.

No es un cambio incompatible: ningún endpoint existente cambia de contrato.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `categorias`: agrega la modificación parcial de una categoría. Los requisitos del alta no
  cambian.

## Fuera de alcance

- Hacer obligatorios `icon` y `color` en el alta: FA-66.
- Cambiar `sort_order` o reordenar la lista.
- Revalidar o migrar los movimientos de una categoría al cambiar su alcance: el cambio se rechaza.
- Borrar categorías: FA-21.

## Impact

- Dominio: `UpdateCategoryCommand`, `UpdateCategoryPort`, y en `CategoryRepositoryPort` la lectura
  de una categoría viva por id y usuario, la consulta de si tiene movimientos de un tipo y el
  `update`. `RESOURCE_IN_USE` en `ErrorCodes`.
- Aplicación: `UpdateCategoryUseCase`.
- Web: `PATCH` en `CategoryController` con el id parseado a mano y un `UpdateCategoryRequest`.
- Persistencia: `CategoryR2dbcAdapter` con el `SELECT`, el `EXISTS` sobre `finance.transactions` y
  el `UPDATE ... RETURNING`, filtrados por `user_id`.
- `docs/database/test-data.sql`: "Cigarrillos" (la borrada del escenario) pasa a tener un id fijo,
  para que Bruno pueda pedirla y comprobar el 404.
- `bruno/categories/`: requests nuevos; replicados en `bruno-personal/categorias/`.
- `docs/api/contrato-api.md`: el endpoint nuevo, el índice, el código nuevo y la lista de lo que el
  API todavía no tiene.
- Sin cambios en el esquema.
