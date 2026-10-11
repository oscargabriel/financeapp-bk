# Proposal

Origen: FA-24. Al tomarla se decidió en el chat (05-10-2026), a pregunta del criterio 3 de la tarea:

- `initialBalance` se puede cambiar siempre, con o sin movimientos, y el saldo vigente se corre en
  la misma diferencia: no hay descuadre.
- `currencyCode` solo se puede cambiar si la cuenta no tiene movimientos; con movimientos es 409.
- `type`, `isActive` y `currentBalance` quedan fuera del parche: mandarlos es 400 en su campo.
  Desactivar es FA-25.

## Why

Una cuenta creada con un nombre, un cupo, unas fechas de corte o un saldo de arranque equivocados
solo se puede corregir hoy con psql.

## What Changes

- `PATCH /api/accounts/{id}`: modifica cualquier subconjunto de `name`, `currencyCode`,
  `initialBalance`, `creditLimit`, `statementDay` y `paymentDueDay`. Lo ausente o en `null` se
  conserva: el parche no vacía campos. Responde 200 con la cuenta completa.
- Cada campo enviado cumple el formato del alta y no puede ir en blanco. Un parche sin campos
  modificables responde 400 sobre `body`.
- `currentBalance`, `type` e `isActive` en el cuerpo responden 400 en su campo. El saldo vigente
  no lo escribe el cliente por ninguna vía.
- `creditLimit`, `statementDay` y `paymentDueDay` sobre una cuenta que no es `CREDIT` responden 400,
  un error por campo, igual que en el alta.
- Cambiar `initialBalance` corre `currentBalance` en la misma diferencia, haya o no movimientos.
- Cambiar `currencyCode` exige que la moneda exista y esté activa (400) y que la cuenta no tenga
  movimientos como origen ni como destino (409 `RESOURCE_IN_USE` en `currencyCode`). Mandar la
  misma moneda que ya tiene no es un cambio.
- Un nombre que ya usa otra cuenta viva del usuario responde 409 `DUPLICATE_RESOURCE` en `name`;
  cambiar solo las mayúsculas del propio nombre se permite.
- Un id mal formado responde 400 en `id`. Una cuenta inexistente, borrada o de otro usuario
  responde 404 en `id`, sin distinguir. Una cuenta desactivada se modifica igual que una activa.
- La cuenta en las respuestas de `GET`, `POST` y `PATCH /api/accounts` gana `initialBalance`,
  `creditLimit`, `statementDay` y `paymentDueDay`: sin ellos el cliente no ve lo que está editando
  ni el PATCH puede confirmar el cambio. Es aditivo, no rompe a nadie.

No es un cambio incompatible.

## Capabilities

### New Capabilities

- `cuentas`: la modificación parcial de una cuenta y la forma de la cuenta en las respuestas del
  API. El listado y el alta, que ya existen sin spec, no se describen aquí más allá de su forma de
  respuesta.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Cambiar el tipo de una cuenta, ni de ni hacia `CREDIT`.
- Desactivar o reactivar una cuenta: FA-25.
- Vaciar `creditLimit`, `statementDay` o `paymentDueDay` de una tarjeta.
- Migrar o convertir los movimientos al cambiar de moneda: el cambio se rechaza.
- La spec del listado y el alta de cuentas más allá de su forma de respuesta.

## Impact

- Esquema: un trigger `BEFORE UPDATE` en `finance.accounts` que corre `current_balance` cuando
  cambia `initial_balance`, en `schema.sql` y como update en `docs/database/update/`, con su
  reversión. El comentario de `initial_balance` se actualiza.
- Dominio: `Account` gana `initialBalance`, `statementDay` y `paymentDueDay`.
  `UpdateAccountCommand`, `UpdateAccountPort`, y en `AccountRepositoryPort` la lectura de una cuenta
  viva por id y usuario, la consulta de si tiene movimientos y el `update`.
- Aplicación: `UpdateAccountUseCase`.
- Web: `PATCH` en `AccountController` con el id parseado a mano, `UpdateAccountRequest` y
  `AccountResponse` con los cuatro campos nuevos.
- Persistencia: `AccountR2dbcAdapter` con el `SELECT`, el `EXISTS` sobre `finance.transactions` y
  el `UPDATE ... RETURNING`, filtrados por `user_id`; el listado y el alta devuelven las columnas
  nuevas.
- `docs/database/test-data.sql`: la cuenta borrada `Davivienda` pasa a tener id fijo.
- `bruno/accounts/`: requests nuevos; replicados en `bruno-personal/cuentas/`.
- `docs/api/contrato-api.md`: el endpoint nuevo, los campos nuevos de la respuesta y la lista de lo
  que el API todavía no tiene.
