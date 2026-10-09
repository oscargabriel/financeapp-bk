# Spec Delta

## MODIFIED Requirements

### Requirement: Modificación parcial de una cuenta
`PATCH /api/accounts/{id}` SHALL aceptar cualquier subconjunto de `name`, `currencyCode`,
`initialBalance`, `creditLimit`, `statementDay`, `paymentDueDay` e `isActive`, normalizados como en el
alta. Un campo ausente o en `null` SHALL conservar su valor. La respuesta SHALL ser 200 con la cuenta
tal como quedó.

#### Scenario: Cambiar nombre y saldo inicial
- **WHEN** el usuario envía `PATCH /api/accounts/{id}` sobre su cuenta `Billetera` (`CASH`, saldo inicial 150000, sin movimientos) con `{"name": " Bolsillo ", "initialBalance": 200000}`
- **THEN** la respuesta es 200 con `name` `Bolsillo`, `initialBalance` 200000, `currentBalance` 200000 y el mismo `id`, `type` y `currencyCode`

#### Scenario: Cambiar los campos de una tarjeta
- **WHEN** el parche trae `{"creditLimit": 4000000, "statementDay": 25, "paymentDueDay": 10}` sobre su tarjeta `Mastercard` con saldo -200000
- **THEN** la respuesta es 200 con `creditLimit` 4000000, `availableCredit` 3800000, `statementDay` 25 y `paymentDueDay` 10

#### Scenario: Campo en null
- **WHEN** el parche trae `{"paymentDueDay": 12, "creditLimit": null}` sobre una tarjeta con cupo
- **THEN** la respuesta es 200, el día de pago cambia y el cupo sigue siendo el mismo

#### Scenario: El listado refleja el cambio
- **WHEN** después de modificar una cuenta el usuario pide `GET /api/accounts`
- **THEN** la cuenta aparece con los datos de la respuesta del PATCH

### Requirement: Campos que el parche no admite
`currentBalance` y `type` MUST NOT modificarse por el parche. Si cualquiera llega con valor, la
respuesta SHALL ser 400 con un error `VALIDATION_ERROR` en ese campo, sin modificar nada.

#### Scenario: Intentar fijar el saldo vigente, el tipo o el estado
- **WHEN** el parche trae `{"currentBalance": 1, "type": "CASH", "isActive": false}` sobre una cuenta activa
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `currentBalance` y otro en `type`, ninguno en `isActive`, y la cuenta no cambia: sigue activa

### Requirement: Cuenta a modificar
El `{id}` SHALL ser un UUID; si no lo es, la respuesta SHALL ser 400 `VALIDATION_ERROR` en el campo
`id`. Una cuenta que no existe, que está borrada o que es de otro usuario SHALL responder 404
`NOT_FOUND` en el campo `id`, sin distinguir entre los tres casos y sin modificar nada. Una cuenta
desactivada SHALL modificarse igual que una activa.

#### Scenario: Id mal formado
- **WHEN** el usuario envía `PATCH /api/accounts/abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

#### Scenario: Cuenta inexistente
- **WHEN** el `{id}` es un UUID que no corresponde a ninguna cuenta
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Cuenta borrada
- **WHEN** el usuario del escenario de pruebas modifica su cuenta borrada `Davivienda`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Cuenta de otro usuario
- **WHEN** un usuario recién registrado modifica la cuenta `Efectivo` del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la cuenta ajena no cambia

#### Scenario: Desactivar una cuenta de otro usuario
- **WHEN** un usuario recién registrado envía `{"isActive": false}` sobre la cuenta `Efectivo` del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la cuenta ajena sigue en el listado de su dueño sin `includeInactive`

#### Scenario: Reactivar una cuenta borrada
- **WHEN** el usuario del escenario de pruebas envía `{"isActive": true}` sobre su cuenta borrada `Davivienda`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Cuenta desactivada
- **WHEN** el usuario del escenario de pruebas envía `{"name": "Nequi"}` sobre su cuenta desactivada `Nequi`
- **THEN** la respuesta es 200 con `isActive` en `false`

## ADDED Requirements

### Requirement: Desactivar y reactivar una cuenta
`isActive` en `false` SHALL desactivar la cuenta y en `true` SHALL reactivarla, con cualquier
`currentBalance`, a favor, en deuda o en cero. Pedir el estado que la cuenta ya tiene SHALL responder
200 con la cuenta sin cambios. El saldo y los movimientos de la cuenta MUST NOT cambiar por el
cambio de estado.

#### Scenario: Desactivar una cuenta con saldo
- **WHEN** el usuario envía `{"isActive": false}` sobre su cuenta `Bolsillo`, activa y con saldo vigente 250000
- **THEN** la respuesta es 200 con `isActive` en `false` y `currentBalance` 250000

#### Scenario: Desactivar una cuenta ya desactivada
- **WHEN** el usuario vuelve a enviar `{"isActive": false}` sobre `Bolsillo`
- **THEN** la respuesta es 200 con `isActive` en `false` y los mismos datos

#### Scenario: Reactivar
- **WHEN** el usuario envía `{"isActive": true}` sobre `Bolsillo` desactivada
- **THEN** la respuesta es 200 con `isActive` en `true` y `currentBalance` 250000

#### Scenario: Desactivar y renombrar a la vez
- **WHEN** el parche trae `{"name": "Vieja", "isActive": false}` sobre una cuenta activa
- **THEN** la respuesta es 200 con `name` `Vieja` e `isActive` en `false`

#### Scenario: Un error del parche no desactiva
- **WHEN** el parche trae `{"currencyCode": "USD", "isActive": false}` sobre una cuenta activa en `COP` con movimientos
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en `currencyCode`, y la cuenta sigue activa

#### Scenario: El estado en null no es un cambio
- **WHEN** el cuerpo es `{"isActive": null}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

### Requirement: Efecto de desactivar una cuenta
Una cuenta desactivada MUST NOT aparecer en `GET /api/accounts` sin `includeInactive=true`, y SHALL
aparecer con `isActive` en `false` cuando se pide. Un movimiento nuevo que la elija como origen o
destino SHALL responder 400 `VALIDATION_ERROR` en ese campo. Al reactivarla, SHALL volver al listado y
admitir movimientos nuevos.

#### Scenario: Fuera del listado por defecto
- **WHEN** después de desactivar `Bolsillo` el usuario pide `GET /api/accounts`
- **THEN** `Bolsillo` no aparece y las demás cuentas activas sí

#### Scenario: En el listado con las inactivas
- **WHEN** el usuario pide `GET /api/accounts?includeInactive=true`
- **THEN** `Bolsillo` aparece con `isActive` en `false` y su `currentBalance`

#### Scenario: Gasto en una cuenta desactivada
- **WHEN** el usuario envía `POST /api/transactions` con un gasto en `Bolsillo` desactivada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].accountId`, y el saldo de `Bolsillo` no cambia

#### Scenario: Gasto tras reactivar
- **WHEN** después de reactivar `Bolsillo` el usuario registra el mismo gasto
- **THEN** la respuesta es 201 y `Bolsillo` vuelve a `GET /api/accounts` con el saldo reducido en el monto del gasto
