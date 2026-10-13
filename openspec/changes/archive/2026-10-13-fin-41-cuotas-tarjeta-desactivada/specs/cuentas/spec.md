# Spec Delta

## MODIFIED Requirements

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

#### Scenario: Tarjeta con compras en cuotas activas
- **WHEN** el usuario envía `{"isActive": false}` sobre una tarjeta activa que tiene una compra en cuotas con cuotas por venir
- **THEN** la respuesta es 200 con `isActive` en `false` y el mismo `currentBalance` y `availableCredit` que tenía, y la compra sigue en `GET /api/installment-purchases`

### Requirement: Solo se borra una cuenta sin movimientos programados
Una cuenta con movimientos programados —`CONFIRMED` con `occurredAt` posterior al momento actual—,
como origen o como destino, o con series activas, MUST NOT borrarse. La respuesta SHALL ser 409 con
un solo error `RESOURCE_IN_USE` en el campo `id` cuya `description` dice cuántas series activas y
cuántos programados tiene, y nada SHALL cambiar. Si además tiene saldo, la respuesta SHALL traer
también el error de `currentBalance`. Los movimientos `PENDING` no cuentan para esta regla.

#### Scenario: Cuenta en cero con un gasto programado
- **WHEN** el usuario borra su cuenta `Sobre`, con saldo vigente 0 y un gasto con fecha de mañana
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id` que dice que tiene 1 movimiento programado, y `Sobre` sigue en el listado

#### Scenario: Cuenta destino de una transferencia programada
- **WHEN** `Sobre`, con saldo 0, es destino de una transferencia con fecha de mañana desde `Bolsillo`, y el usuario la borra
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id`

#### Scenario: Borrar los programados y después la cuenta
- **WHEN** el usuario borra con `DELETE /api/transactions/{id}` los programados de `Sobre` y después borra la cuenta
- **THEN** la respuesta es 204

#### Scenario: Tarjeta con cuotas por venir
- **WHEN** el usuario borra una tarjeta con saldo vigente 0 que tiene una compra en cuotas con cuotas por venir
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id`, y la compra sigue en `GET /api/installment-purchases`

#### Scenario: Cuenta con una serie activa
- **WHEN** el usuario borra una cuenta sobre la que corre una serie sin fin
- **THEN** la respuesta es 409 y trae un solo error `RESOURCE_IN_USE` en el campo `id`, que dice que tiene 1 serie activa y 1 movimiento programado

#### Scenario: Serie sin fin cuya programada se borró a mano
- **WHEN** el usuario borra con `DELETE /api/transactions/{id}` la ocurrencia programada de la única serie de `Desactivable`, que queda con saldo 0, y después borra la cuenta
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id` que dice que tiene 1 serie activa

#### Scenario: Borrar tras cancelar la serie
- **WHEN** el usuario cancela la serie de una cuenta con saldo 0 y sin otros programados, y después la borra
- **THEN** la respuesta es 204

#### Scenario: Saldo y programados a la vez
- **WHEN** el usuario borra una cuenta con saldo vigente -30000 y un gasto programado
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en `currentBalance` y otro en `id`

#### Scenario: Los pendientes no impiden el borrado
- **WHEN** el usuario borra una cuenta con saldo 0, sin programados y con un gasto pendiente
- **THEN** la respuesta es 204, y el pendiente ya no sale en `GET /api/transactions/pending`

#### Scenario: Tarjeta desactivada con cuotas por venir
- **WHEN** el usuario borra una tarjeta desactivada, con saldo vigente 0, que tiene una compra en cuotas con cuotas por venir
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id`, y la compra sigue en `GET /api/installment-purchases`
