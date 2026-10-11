# Spec Delta

## ADDED Requirements

### Requirement: Saldo no negativo en las cuentas que no son de crédito
Una petición que baja el `currentBalance` de una cuenta `CASH`, `DEBIT`, `SAVINGS`, `INVESTMENT` u
`OTHER` SHALL rechazarse si lo deja por debajo de cero. SHALL responder 409 con un error
`INSUFFICIENT_FUNDS` por cada cuenta afectada, cuya `description` nombra la cuenta y el saldo en que
quedaría, sin cambiar nada. Una petición que no baja el saldo de una cuenta no SHALL rechazarse por
esta regla. Una cuenta `CREDIT` no tiene tope.

#### Scenario: Gasto mayor que el saldo de una cuenta de débito
- **WHEN** la cuenta `DEBIT` `Nómina`, con `currentBalance` 100000, recibe un gasto de 150000 con fecha de hoy
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` cuya `description` nombra `Nómina` y el saldo -50000, y el `currentBalance` de `Nómina` sigue en 100000

#### Scenario: Gasto que deja la cuenta exactamente en cero
- **WHEN** la misma cuenta, con `currentBalance` 100000, recibe un gasto de 100000
- **THEN** el gasto se registra y el `currentBalance` queda en 0

#### Scenario: Tarjeta de crédito sin tope
- **WHEN** una cuenta `CREDIT` con `currentBalance` 0 recibe un gasto de 500000
- **THEN** el gasto se registra y el `currentBalance` queda en -500000

### Requirement: Cuentas que ya están en negativo
Una cuenta no `CREDIT` cuyo `currentBalance` ya es negativo —por datos anteriores a esta regla o por
un programado que llegó a su fecha— SHALL seguir aceptando las peticiones que suben su saldo, aunque
no lo lleven a cero, y SHALL rechazar con 409 `INSUFFICIENT_FUNDS` las que lo bajan. Ningún dato
existente SHALL modificarse por esta regla.

#### Scenario: Ingreso que no alcanza a salir de cero
- **WHEN** la cuenta `CASH` `Bolsillo`, con `currentBalance` -30000, recibe un ingreso de 10000
- **THEN** el ingreso se registra y el `currentBalance` queda en -20000

#### Scenario: Gasto sobre una cuenta en negativo
- **WHEN** la misma cuenta, con `currentBalance` -20000, recibe un gasto de 1000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` y el saldo no cambia

### Requirement: Saldo inicial según el tipo de la cuenta
`initialBalance` negativo SHALL admitirse solo en una cuenta `CREDIT`. En `POST /api/accounts` sobre
cualquier otro tipo, y en `PATCH /api/accounts/{id}` sobre una cuenta guardada de otro tipo, SHALL
responder 400 con un error `VALIDATION_ERROR` en `initialBalance`, sin crear ni modificar nada.

#### Scenario: Alta de una cuenta de ahorros con saldo negativo
- **WHEN** se envía `POST /api/accounts` con `{"name": "Ahorros", "type": "SAVINGS", "currencyCode": "COP", "initialBalance": -1000}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `initialBalance` y la cuenta no se crea

#### Scenario: Alta de una tarjeta con saldo negativo
- **WHEN** se envía `POST /api/accounts` con una cuenta `CREDIT` válida e `initialBalance` -200000
- **THEN** la respuesta es 201 con `currentBalance` -200000

#### Scenario: Parche con saldo inicial negativo
- **WHEN** el parche trae `{"initialBalance": -1}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `initialBalance` y `Bolsillo` no cambia

### Requirement: Bajar el saldo inicial por debajo de lo gastado
Un `PATCH /api/accounts/{id}` sobre una cuenta no `CREDIT` cuyo `initialBalance` nuevo, no negativo,
dejaría el `currentBalance` por debajo de cero SHALL responder 409 con un error `INSUFFICIENT_FUNDS`
en `initialBalance`, sin modificar ningún campo del parche.

#### Scenario: Saldo inicial menor que los gastos
- **WHEN** la cuenta `Bolsillo` tiene saldo inicial 200000 y un gasto de 150000 (saldo vigente 50000) y el parche trae `{"initialBalance": 100000, "name": "Bolsillo 2"}`
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `initialBalance`, y `Bolsillo` sigue con su nombre, `initialBalance` 200000 y `currentBalance` 50000

#### Scenario: Saldo inicial que deja la cuenta en cero
- **WHEN** la misma cuenta recibe el parche `{"initialBalance": 150000}`
- **THEN** la respuesta es 200 con `currentBalance` 0
