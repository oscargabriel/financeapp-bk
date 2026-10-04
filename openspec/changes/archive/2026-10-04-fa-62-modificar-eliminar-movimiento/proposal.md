# Proposal

Origen: FA-62. El encargo llegó por chat el 04-10-2026: *"Completar los endpoints de Transaction,
para modificar monto, categoria, type, descripcion, fecha, cuenta dado el id de la transaccion y
otro endpoint para eliminar con el ID de la transacción"*, con la nota de crear en
`bruno-personal/` los requests con todos los parámetros y valores de ejemplo. Al registrarla se
aclaró que la modificación es un PATCH parcial.

## Why

Hoy un movimiento solo se puede crear. Un monto mal digitado, una categoría equivocada o un lote
enviado dos veces (el alta no es idempotente) no tienen arreglo desde el API: hay que entrar a la
base con psql. El propio `docs` de `bruno-personal/movimientos/registrar.yml` lo advierte.

## What Changes

- `PATCH /api/transactions/{id}`: modifica cualquier subconjunto de `type`, `accountId`,
  `destinationAccountId`, `categoryId`, `amount`, `description` y `occurredAt`. Lo que no se envía
  se conserva. Responde 200 con el movimiento completo.
- Las reglas que cruzan campos (forma de la transferencia, alcance de la categoría, cuenta destino
  distinta del origen) se evalúan sobre el movimiento **resultante**, no sobre el parche.
- `DELETE /api/transactions/{id}`: borra el movimiento y responde 204.
- Un id mal formado es 400 sobre `id`; uno inexistente o de otro usuario es 404, sin distinguir
  entre los dos.
- Los saldos de las cuentas los sigue moviendo `trg_transactions_sync_balance`, que ya revierte la
  fila vieja en UPDATE y DELETE. La aplicación no los toca.
- CORS pasa a permitir `PATCH`: hoy admite `GET`, `POST`, `PUT` y `DELETE`, y un navegador
  rechazaría el preflight del endpoint nuevo.

No es un cambio incompatible: ningún endpoint existente cambia de contrato.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: agrega la modificación parcial y la eliminación de un movimiento por su id. Los
  requisitos del alta no cambian.

## Fuera de alcance

- Modificar `notes`, `currencyCode` o `destinationAmount`: no están en el encargo. `notes` se
  conserva tal cual; la moneda sigue fija en COP (FA-51).
- Vaciar un campo enviándolo en `null`: `null` significa "no cambia" (ver design.md).
- Listar o consultar un movimiento por id (`GET /api/transactions/{id}`): es el reporte de FA-63 o
  una tarea nueva.
- Borrado lógico o papelera: la tabla documenta que los movimientos se borran físicamente.
- Control de concurrencia (ETag, versión): un solo usuario por cuenta, sin escritores concurrentes
  en la práctica.

## Impact

- Dominio: puertos de entrada `UpdateTransactionPort` y `DeleteTransactionPort`, un command para
  el parche, y tres operaciones nuevas en `TransactionRepositoryPort` (buscar por id y usuario,
  actualizar, borrar).
- Aplicación: `UpdateTransactionUseCase`, `DeleteTransactionUseCase`, y la validación de cuentas y
  categorías del alta extraída para compartirla.
- Web: `TransactionController` con los dos métodos y un `UpdateTransactionRequest` con Bean
  Validation.
- Persistencia: `TransactionR2dbcAdapter` con `SELECT`, `UPDATE` y `DELETE` filtrados por
  `user_id`.
- `SecurityConfig`: `PATCH` en los métodos de CORS.
- `bruno/transactions/`: requests nuevos con aserciones, saldos incluidos; replicados con valores
  de ejemplo en `bruno-personal/movimientos/`.
- `docs/api/contrato-api.md`: los dos endpoints, el índice, CORS y la lista de lo que el API
  todavía no tiene.
- Sin cambios en la base de datos.
