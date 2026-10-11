# Spec Delta

## ADDED Requirements

### Requirement: Solo se borra una cuenta sin movimientos programados
Una cuenta con movimientos programados —`CONFIRMED` con `occurredAt` posterior al momento actual—,
como origen o como destino, MUST NOT borrarse. La respuesta SHALL ser 409 con un error
`RESOURCE_IN_USE` en el campo `id` cuya `description` dice cuántos programados tiene, y nada SHALL
cambiar. Si además tiene saldo, la respuesta SHALL traer también el error de `currentBalance`. Los
movimientos `PENDING` no cuentan para esta regla.

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
- **THEN** la respuesta es 409 y trae un error `RESOURCE_IN_USE` en el campo `id`

#### Scenario: Saldo y programados a la vez
- **WHEN** el usuario borra una cuenta con saldo vigente -30000 y un gasto programado
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en `currentBalance` y otro en `id`

#### Scenario: Los pendientes no impiden el borrado
- **WHEN** el usuario borra una cuenta con saldo 0, sin programados y con un gasto pendiente
- **THEN** la respuesta es 204, y el pendiente ya no sale en `GET /api/transactions/pending`
