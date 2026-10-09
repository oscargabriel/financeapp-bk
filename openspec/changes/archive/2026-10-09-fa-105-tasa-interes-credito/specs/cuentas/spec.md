## MODIFIED Requirements

### Requirement: Forma de la cuenta en las respuestas
`GET /api/accounts`, `POST /api/accounts` y `PATCH /api/accounts/{id}` SHALL presentar cada cuenta
como `{id, name, type, currencyCode, initialBalance, currentBalance, creditLimit, availableCredit,
statementDay, paymentDueDay, monthlyInterestRate, isActive}`. En una cuenta que no es `CREDIT`,
`creditLimit`, `availableCredit`, `statementDay`, `paymentDueDay` y `monthlyInterestRate` SHALL ser
`null`.

#### Scenario: Una tarjeta de crédito
- **WHEN** el usuario crea una cuenta `CREDIT` con `initialBalance` -200000, `creditLimit` 3000000, `statementDay` 20, `paymentDueDay` 5 y `monthlyInterestRate` 2.15
- **THEN** la respuesta trae `initialBalance` -200000, `currentBalance` -200000, `creditLimit` 3000000, `availableCredit` 2800000, `statementDay` 20, `paymentDueDay` 5 y `monthlyInterestRate` 2.15

#### Scenario: Una cuenta que no es de crédito
- **WHEN** el usuario lista sus cuentas y tiene una `CASH`
- **THEN** esa cuenta trae `initialBalance` con su saldo de arranque y `creditLimit`, `availableCredit`, `statementDay`, `paymentDueDay` y `monthlyInterestRate` en `null`

### Requirement: Modificación parcial de una cuenta
`PATCH /api/accounts/{id}` SHALL aceptar cualquier subconjunto de `name`, `currencyCode`,
`initialBalance`, `creditLimit`, `statementDay`, `paymentDueDay`, `monthlyInterestRate` e `isActive`,
normalizados como en el alta. Un campo ausente o en `null` SHALL conservar su valor. La respuesta
SHALL ser 200 con la cuenta tal como quedó.

#### Scenario: Cambiar nombre y saldo inicial
- **WHEN** el usuario envía `PATCH /api/accounts/{id}` sobre su cuenta `Billetera` (`CASH`, saldo inicial 150000, sin movimientos) con `{"name": " Bolsillo ", "initialBalance": 200000}`
- **THEN** la respuesta es 200 con `name` `Bolsillo`, `initialBalance` 200000, `currentBalance` 200000 y el mismo `id`, `type` y `currencyCode`

#### Scenario: Cambiar los campos de una tarjeta
- **WHEN** el parche trae `{"creditLimit": 4000000, "statementDay": 25, "paymentDueDay": 10, "monthlyInterestRate": 1.9}` sobre su tarjeta `Mastercard` con saldo -200000
- **THEN** la respuesta es 200 con `creditLimit` 4000000, `availableCredit` 3800000, `statementDay` 25, `paymentDueDay` 10 y `monthlyInterestRate` 1.9

#### Scenario: Campo en null
- **WHEN** el parche trae `{"paymentDueDay": 12, "creditLimit": null, "monthlyInterestRate": null}` sobre una tarjeta con cupo y tasa
- **THEN** la respuesta es 200, el día de pago cambia y el cupo y la tasa siguen siendo los mismos

#### Scenario: El listado refleja el cambio
- **WHEN** después de modificar una cuenta el usuario pide `GET /api/accounts`
- **THEN** la cuenta aparece con los datos de la respuesta del PATCH

### Requirement: Campos de crédito según el tipo de la cuenta
`creditLimit`, `statementDay`, `paymentDueDay` y `monthlyInterestRate` SHALL admitirse solo sobre una
cuenta `CREDIT`. Sobre cualquier otro tipo, cada uno que llegue con valor SHALL responder 400 con un
error `VALIDATION_ERROR` en su campo, sin modificar nada.

#### Scenario: Campos de crédito sobre una cuenta de efectivo
- **WHEN** el parche trae `{"creditLimit": 1000000, "statementDay": 3}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `creditLimit` y otro en `statementDay`

#### Scenario: Tasa sobre una cuenta de efectivo
- **WHEN** el parche trae `{"monthlyInterestRate": 2}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`, y `Bolsillo` sigue con la tasa en `null`

## ADDED Requirements

### Requirement: Tasa de interés mensual de una tarjeta
Una cuenta `CREDIT` SHALL guardar `monthlyInterestRate`: su tasa de interés mensual expresada como
porcentaje, de modo que `2.15` es el 2,15 % mensual. Es opcional: una tarjeta creada sin ella SHALL
traerla en `null`. `POST /api/accounts` SHALL aceptarla al crear una `CREDIT`, y en cualquier otro tipo
SHALL responder 400 `VALIDATION_ERROR` en `monthlyInterestRate` sin crear la cuenta.

#### Scenario: Crear una tarjeta con tasa
- **WHEN** el usuario crea una cuenta `CREDIT` con `monthlyInterestRate` 2.15
- **THEN** la respuesta es 201 con `monthlyInterestRate` 2.15, y `GET /api/accounts` la trae igual

#### Scenario: Crear una tarjeta sin tasa
- **WHEN** el usuario crea una cuenta `CREDIT` sin `monthlyInterestRate`
- **THEN** la respuesta es 201 con `monthlyInterestRate` en `null`

#### Scenario: Una tarjeta sin interés
- **WHEN** el usuario crea una cuenta `CREDIT` con `monthlyInterestRate` 0
- **THEN** la respuesta es 201 con `monthlyInterestRate` 0

#### Scenario: Tasa en una cuenta que no es de crédito
- **WHEN** el usuario crea una cuenta `DEBIT` con `monthlyInterestRate` 2
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`, y la cuenta no se crea

### Requirement: Rango de la tasa de interés
`monthlyInterestRate` SHALL estar entre 0 y 10, ambos incluidos, con hasta 4 decimales; los ceros a la
derecha no cuentan como decimales. Fuera de eso, el alta y el parche SHALL responder 400
`VALIDATION_ERROR` en `monthlyInterestRate`, sin crear ni modificar nada.

#### Scenario: Tasa negativa al crear
- **WHEN** el usuario crea una cuenta `CREDIT` con `monthlyInterestRate` -1
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`

#### Scenario: Tasa que parece anual
- **WHEN** el parche trae `{"monthlyInterestRate": 28.5}` sobre la tarjeta `Mastercard`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`, y la tarjeta conserva su tasa

#### Scenario: Demasiados decimales
- **WHEN** el parche trae `{"monthlyInterestRate": 1.23456}` sobre la tarjeta `Mastercard`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `monthlyInterestRate`

#### Scenario: Los límites se admiten
- **WHEN** el parche trae `{"monthlyInterestRate": 10}` y después `{"monthlyInterestRate": 1.50000}`
- **THEN** las dos respuestas son 200, con `monthlyInterestRate` 10 y 1.5

### Requirement: Alta y listado autenticados con JWT
`POST /api/accounts` y `GET /api/accounts` SHALL exigir un Bearer válido. Sin credencial o con la
credencial Basic compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo
`authorization` y la cabecera `WWW-Authenticate: Bearer`, sin crear nada.

#### Scenario: Crear sin credencial
- **WHEN** se envía `POST /api/accounts` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Crear con el Basic compartido
- **WHEN** se envía `POST /api/accounts` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Listar sin credencial
- **WHEN** se envía `GET /api/accounts` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Listar con el Basic compartido
- **WHEN** se envía `GET /api/accounts` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`
