# Proposal

Origen: FA-21. Su último criterio de aceptación viene de FA-19: el escenario "El nombre lo usa una
categoría borrada" quedó en la spec sin prueba en Bruno porque no había forma de borrar una
categoría desde el API.

## Why

Una categoría que el usuario ya no usa solo se puede quitar hoy con psql. Y como el nombre es
único entre las categorías vivas, tampoco se puede liberar para crear otra con el mismo nombre.

## What Changes

- `DELETE /api/categories/{id}`: marca `deleted_at` en la categoría del usuario del token y responde
  204 sin cuerpo. La fila no se borra.
- Se puede borrar una categoría con movimientos, y también una copiada de la semilla. Los movimientos
  siguen apuntando a ella y no cambian:
  - El reporte de movimientos los sigue mostrando con su `categoryName`.
  - Un PATCH que no toca la categoría ni el tipo de uno de esos movimientos se acepta.
  - Ningún movimiento nuevo, ni un PATCH que la elija, puede usarla: responde 400 en `categoryId`,
    como con una categoría que no existe.
- La categoría borrada deja de aparecer en `GET /api/categories`, y su nombre queda libre para un
  alta o un PATCH.
- Un id mal formado responde 400 sobre `id`. Una categoría inexistente, ya borrada o de otro
  usuario responde 404 sobre `id`, sin distinguir entre los tres casos.
- **Decisión sobre `v_monthly_spending_by_category`:** la categoría borrada sigue apareciendo, con
  su nombre, icono y color, en los meses en que tuvo gasto. Ocultarla haría que los porcentajes
  del mes ya no sumaran 100 y que el desglose no cuadrara con el total de `v_monthly_spending`.
  Así funciona ya hoy, porque la vista no filtra `deleted_at`. La decisión queda escrita en
  `docs/database/modelo-datos.md`. Ningún endpoint lee esa vista todavía.

No es un cambio incompatible: ningún endpoint existente cambia de contrato.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `categorias`: agrega el borrado lógico y el comportamiento de los movimientos de una categoría
  borrada. El escenario "El nombre lo usa una categoría borrada" pasa a describir la prueba
  concreta.
- `reportes`: el reporte de movimientos sigue mostrando los de una categoría borrada.

## Fuera de alcance

- Restaurar una categoría borrada, o listar las borradas.
- Pasar los movimientos de una categoría borrada a otra.
- Los topes de gasto de una categoría borrada. No hay API de metas: lo decide la tarea que la cree.
- Cambiar `sort_order` al borrar: el hueco que queda no se compacta.

## Impact

- Dominio: `DeleteCategoryPort`, y `softDelete` en `CategoryRepositoryPort`.
- Aplicación: `DeleteCategoryUseCase`, que reutiliza el 404 de `UpdateCategoryUseCase`.
- Web: `@DeleteMapping("/{id}")` en `CategoryController`, con el id parseado a mano.
- Persistencia: un `UPDATE ... SET deleted_at = now()` en `CategoryR2dbcAdapter`, filtrado por
  `id`, `user_id` y `deleted_at IS NULL`.
- `bruno/categories/`: requests nuevos al final de la carpeta. El gasto de Huerta guarda su id para
  modificarlo después del borrado. Se replican en `bruno-personal/categorias/`.
- `docs/api/contrato-api.md`: el endpoint nuevo, el índice y la lista de lo que el API todavía no
  tiene.
- `docs/database/modelo-datos.md`: la decisión sobre `v_monthly_spending_by_category`.
- Sin cambios en el esquema.
