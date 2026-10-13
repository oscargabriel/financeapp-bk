# Spec Delta

## MODIFIED Requirements

### Requirement: Forma de la cuenta en las respuestas
`GET /api/accounts`, `POST /api/accounts` y `PATCH /api/accounts/{id}` SHALL presentar cada cuenta
como `{id, name, type, currencyCode, initialBalance, currentBalance, creditLimit, availableCredit,
statementDay, paymentDueDay, monthlyInterestRate, chargesSingleInstallmentInterest, isActive}`. En una
cuenta que no es `CREDIT`, `creditLimit`, `availableCredit`, `statementDay`, `paymentDueDay`,
`monthlyInterestRate` y `chargesSingleInstallmentInterest` SHALL ser `null`.

#### Scenario: Una tarjeta de crédito
- **WHEN** el usuario crea una cuenta `CREDIT` con `initialBalance` -200000, `creditLimit` 3000000, `statementDay` 20, `paymentDueDay` 5 y `monthlyInterestRate` 2.15
- **THEN** la respuesta trae `initialBalance` -200000, `currentBalance` -200000, `creditLimit` 3000000, `availableCredit` 2800000, `statementDay` 20, `paymentDueDay` 5, `monthlyInterestRate` 2.15 y `chargesSingleInstallmentInterest` `false`

#### Scenario: Una cuenta que no es de crédito
- **WHEN** el usuario lista sus cuentas y tiene una `CASH`
- **THEN** esa cuenta trae `initialBalance` con su saldo de arranque y `creditLimit`, `availableCredit`, `statementDay`, `paymentDueDay`, `monthlyInterestRate` y `chargesSingleInstallmentInterest` en `null`

### Requirement: Modificación parcial de una cuenta
`PATCH /api/accounts/{id}` SHALL aceptar cualquier subconjunto de `name`, `currencyCode`,
`initialBalance`, `creditLimit`, `statementDay`, `paymentDueDay`, `monthlyInterestRate`,
`chargesSingleInstallmentInterest` e `isActive`, normalizados como en el alta. Un campo ausente o en
`null` SHALL conservar su valor. La respuesta SHALL ser 200 con la cuenta tal como quedó.

#### Scenario: Cambiar nombre y saldo inicial
- **WHEN** el usuario envía `PATCH /api/accounts/{id}` sobre su cuenta `Billetera` (`CASH`, saldo inicial 150000, sin movimientos) con `{"name": " Bolsillo ", "initialBalance": 200000}`
- **THEN** la respuesta es 200 con `name` `Bolsillo`, `initialBalance` 200000, `currentBalance` 200000 y el mismo `id`, `type` y `currencyCode`

#### Scenario: Cambiar los campos de una tarjeta
- **WHEN** el parche trae `{"creditLimit": 4000000, "statementDay": 25, "paymentDueDay": 10, "monthlyInterestRate": 1.9, "chargesSingleInstallmentInterest": true}` sobre su tarjeta `Mastercard` con saldo -200000
- **THEN** la respuesta es 200 con `creditLimit` 4000000, `availableCredit` 3800000, `statementDay` 25, `paymentDueDay` 10, `monthlyInterestRate` 1.9 y `chargesSingleInstallmentInterest` `true`

#### Scenario: Campo en null
- **WHEN** el parche trae `{"paymentDueDay": 12, "creditLimit": null, "monthlyInterestRate": null, "chargesSingleInstallmentInterest": null}` sobre una tarjeta con cupo, tasa y la opción en `true`
- **THEN** la respuesta es 200, el día de pago cambia y el cupo, la tasa y la opción siguen siendo los mismos

#### Scenario: El listado refleja el cambio
- **WHEN** después de modificar una cuenta el usuario pide `GET /api/accounts`
- **THEN** la cuenta aparece con los datos de la respuesta del PATCH

### Requirement: Campos de crédito según el tipo de la cuenta
`creditLimit`, `statementDay`, `paymentDueDay`, `monthlyInterestRate` y
`chargesSingleInstallmentInterest` SHALL admitirse solo sobre una cuenta `CREDIT`. Sobre cualquier
otro tipo, cada uno que llegue con valor SHALL responder 400 con un error `VALIDATION_ERROR` en su
campo, sin modificar nada.

#### Scenario: Campos de crédito sobre una cuenta de efectivo
- **WHEN** el parche trae `{"creditLimit": 1000000, "statementDay": 3}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `creditLimit` y otro en `statementDay`

#### Scenario: Tasa sobre una cuenta de efectivo
- **WHEN** el parche trae `{"monthlyInterestRate": 2}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`, y `Bolsillo` sigue con la tasa en `null`

#### Scenario: Interés a una cuota sobre una cuenta de efectivo
- **WHEN** el parche trae `{"chargesSingleInstallmentInterest": true}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `chargesSingleInstallmentInterest`, y `Bolsillo` sigue con el campo en `null`

## ADDED Requirements

### Requirement: Interés en las compras a una cuota
Una cuenta `CREDIT` SHALL guardar `chargesSingleInstallmentInterest`: `true` si la tarjeta cobra
interés en las compras a 1 cuota, `false` si no. `POST /api/accounts` SHALL aceptarlo al crear una
`CREDIT`, y una tarjeta creada sin él SHALL quedar en `false`. En cualquier otro tipo, enviarlo con
valor, también `false`, SHALL responder 400 `VALIDATION_ERROR` en `chargesSingleInstallmentInterest`
sin crear la cuenta.

#### Scenario: Crear una tarjeta que cobra interés a una cuota
- **WHEN** el usuario crea una cuenta `CREDIT` con `chargesSingleInstallmentInterest` `true`
- **THEN** la respuesta es 201 con `chargesSingleInstallmentInterest` `true`, y `GET /api/accounts` la trae igual

#### Scenario: Crear una tarjeta sin la opción
- **WHEN** el usuario crea una cuenta `CREDIT` sin `chargesSingleInstallmentInterest`
- **THEN** la respuesta es 201 con `chargesSingleInstallmentInterest` `false`

#### Scenario: La opción en una cuenta que no es de crédito
- **WHEN** el usuario crea una cuenta `DEBIT` con `chargesSingleInstallmentInterest` `false`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `chargesSingleInstallmentInterest`, y la cuenta no se crea

#### Scenario: Las tarjetas anteriores a la opción
- **WHEN** el usuario lista sus cuentas y tiene una tarjeta creada antes de que existiera la opción
- **THEN** esa tarjeta trae `chargesSingleInstallmentInterest` `false`
