# Design

## Context

El alta (FA-19) dejó el reparto a imitar: formato en el record de request, normalización en el caso
de uso y el choque del índice `ux_categories_user_name` traducido a 409 en `CategoryR2dbcAdapter`.
El PATCH de movimientos (FA-62) dejó el de un parche: `null` es "no cambia", el parche vacío lo
rechaza el controlador sobre `body`, el id se parsea a mano y un recurso ajeno es 404.

La compatibilidad entre el tipo de un movimiento y el alcance de su categoría no la protege la
base: solo la comprueban el alta y el PATCH de movimientos (`ReferenciasDelUsuario`). Cambiar el
`applies_to` de una categoría podría dejar movimientos viejos con una categoría que ya no les sirve.

## Goals / Non-Goals

**Goals:**
- Parche con la misma semántica de `null` que el de movimientos.
- Que ningún movimiento quede con una categoría incompatible con su tipo por culpa de este endpoint.

**Non-Goals:**
- Vaciar `icon` o `color` (decisión del usuario), ni hacerlos obligatorios en el alta (FA-66).
- Bloquear el cambio de alcance a nivel de base (trigger o constraint).

## Decisions

### `null` es "no cambia", y el texto en blanco es un error

Igual que en movimientos: ausente y `null` valen lo mismo. El usuario decidió que el parche no
vacía `icon` ni `color`, así que el texto en blanco en cualquiera de los cuatro campos responde 400
(`Formatos.NO_EN_BLANCO`) en vez de guardarse como `null`, como hace el alta. Se descartaron "texto
vacío vacía el campo" y JSON Merge Patch (RFC 7396).

`UpdateCategoryRequest` lleva las constraints del alta sin `@NotBlank`: `@Size(max = 60)` en
`name`, `@ValorDeEnum` en `appliesTo`, `@Size(max = 40)` en `icon` y `Formatos.COLOR_HEX` en
`color`, más `NO_EN_BLANCO` en los cuatro. `COLOR_HEX` acepta el vacío, así que un color en blanco
da un solo error.

### El caso de uso carga, decide y reemplaza

`UpdateCategoryUseCase`:

1. Lee la categoría viva por id y usuario (`findActiveByIdAndUser`). Vacío → 404 `NOT_FOUND` en
   `id`. Un solo SELECT con `deleted_at IS NULL AND user_id = :userId` cubre inexistente, borrada y
   ajena.
2. Aplica el parche sobre la categoría leída, con la normalización del alta.
3. Si el alcance cambia a `EXPENSE` o a `INCOME`, pregunta si la categoría tiene movimientos del
   tipo opuesto (`hasTransactionsOfType`). Si los tiene → 409 `RESOURCE_IN_USE` en `appliesTo`.
4. Escribe la categoría resultante completa (`update`) con `UPDATE ... WHERE id AND user_id AND
   deleted_at IS NULL RETURNING`. El choque de nombre sale del índice como en el alta. Cero filas
   (borrada entre la lectura y la escritura) → 404.

Se descartó un `UPDATE` con `COALESCE(:campo, campo)` sin lectura previa: ahorra un viaje, pero el
chequeo de movimientos necesita saber el alcance actual y el resultante, y el 404 tendría que salir
igual de un `RETURNING` vacío. El mismo orden de pasos que `UpdateTransactionUseCase`.

Las tres consultas son operaciones nuevas de `CategoryRepositoryPort`, que implementa
`CategoryR2dbcAdapter`. La de movimientos lee `finance.transactions`, pero responde una pregunta
sobre la categoría, y así el caso de uso no depende de `TransactionRepositoryPort`.

### `RESOURCE_IN_USE`, no `DUPLICATE_RESOURCE` ni `VALIDATION_ERROR`

El 409 por alcance no es un nombre repetido ni un formato inválido: es un recurso que no se puede
cambiar mientras otros lo usan. Nombrarlo por su categoría permite reutilizarlo después (una cuenta
con movimientos, por ejemplo) sin que el código mienta.

### `isSystem` no cambia

El comentario de `is_system` en `schema.sql` lo define como "solo informativo: el usuario puede
editarla o borrarla igual". Una categoría de la semilla se modifica como cualquier otra y sigue
marcada como de la semilla.

### Cigarrillos con id fijo en `test-data.sql`

Para probar el 404 de una categoría borrada, Bruno necesita su id, y el listado no la devuelve. Se
le da `30000000-0000-7000-8000-000000000170`, como ya tienen fijo las cuentas del escenario. El
request usa el token del usuario del escenario, pero un 404 no escribe nada, así que sigue sin
modificar al escenario.

## Risks / Trade-offs

- **Carrera entre el chequeo de movimientos y el UPDATE.** Un movimiento registrado entre los dos
  pasos podría quedar incompatible. Se acepta: un usuario por cuenta, sin escritores concurrentes
  en la práctica, el mismo supuesto de FA-62.
- **Tres viajes a la base en el peor caso.** Es un endpoint de edición ocasional; la claridad del
  caso de uso pesa más.
