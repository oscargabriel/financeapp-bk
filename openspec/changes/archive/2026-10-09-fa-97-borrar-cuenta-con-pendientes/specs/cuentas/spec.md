# Spec Delta

## MODIFIED Requirements

### Requirement: Movimientos de una cuenta borrada
Los movimientos confirmados de una cuenta borrada SHALL quedar como estaban. Un PATCH de uno de
ellos que no elige la cuenta borrada SHALL aceptarse. Un movimiento nuevo, o un PATCH, que elija la
cuenta borrada como origen o destino SHALL responder 400 `VALIDATION_ERROR` en ese campo, como con
una cuenta que no existe.

#### Scenario: Modificar un gasto de la cuenta borrada
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` con `{"description": "Corregido"}` sobre el gasto de la cuenta borrada
- **THEN** la respuesta es 200 con la descripción nueva y el `accountId` de la cuenta borrada

#### Scenario: Registrar un gasto en la cuenta borrada
- **WHEN** el usuario envía `POST /api/transactions` con un gasto en la cuenta borrada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].accountId`

## ADDED Requirements

### Requirement: Pendientes de una cuenta borrada
Un borrado que responde 204 SHALL eliminar los movimientos `PENDING` del usuario que tienen la
cuenta como origen o como destino, en la misma operación que la marca como borrada. Ningún
`currentBalance` SHALL cambiar por eso. Los pendientes de otras cuentas MUST NOT eliminarse, y un
borrado que no responde 204 MUST NOT eliminar ninguno.

#### Scenario: El borrado se lleva los pendientes de la cuenta
- **WHEN** `Caja chica`, con saldo 0, tiene un gasto pendiente y es destino de una transferencia pendiente desde `Bolsillo`, y el usuario la borra
- **THEN** la respuesta es 204 y ninguno de los dos sale en `GET /api/transactions/pending`

#### Scenario: Los pendientes de otra cuenta se quedan
- **WHEN** el usuario tiene además un gasto pendiente en `Bolsillo` y borra `Caja chica`
- **THEN** el gasto pendiente de `Bolsillo` sigue en `GET /api/transactions/pending`

#### Scenario: Los saldos no cambian
- **WHEN** después del borrado el usuario pide `GET /api/accounts`
- **THEN** `Bolsillo` tiene el mismo `currentBalance` que antes de crear la transferencia pendiente

#### Scenario: Un borrado rechazado no elimina pendientes
- **WHEN** `Caja chica` tiene saldo -30000 y pendientes, y el usuario la borra
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en `currentBalance`, y sus pendientes siguen en `GET /api/transactions/pending`
