# cuentas Specification

## Purpose
Las cuentas del usuario —efectivo, bancos, tarjetas— de donde sale y adonde llega el dinero: cómo
se presentan en el API y cómo se corrigen sin descuadrar su saldo vigente.

## Requirements

### Requirement: Forma de la cuenta en las respuestas
`GET /api/accounts`, `POST /api/accounts` y `PATCH /api/accounts/{id}` SHALL presentar cada cuenta
como `{id, name, type, currencyCode, initialBalance, currentBalance, creditLimit, availableCredit,
statementDay, paymentDueDay, isActive}`. En una cuenta que no es `CREDIT`, `creditLimit`,
`availableCredit`, `statementDay` y `paymentDueDay` SHALL ser `null`.

#### Scenario: Una tarjeta de crédito
- **WHEN** el usuario crea una cuenta `CREDIT` con `initialBalance` -200000, `creditLimit` 3000000, `statementDay` 20 y `paymentDueDay` 5
- **THEN** la respuesta trae `initialBalance` -200000, `currentBalance` -200000, `creditLimit` 3000000, `availableCredit` 2800000, `statementDay` 20 y `paymentDueDay` 5

#### Scenario: Una cuenta que no es de crédito
- **WHEN** el usuario lista sus cuentas y tiene una `CASH`
- **THEN** esa cuenta trae `initialBalance` con su saldo de arranque y `creditLimit`, `availableCredit`, `statementDay` y `paymentDueDay` en `null`

### Requirement: Modificación parcial de una cuenta
`PATCH /api/accounts/{id}` SHALL aceptar cualquier subconjunto de `name`, `currencyCode`,
`initialBalance`, `creditLimit`, `statementDay` y `paymentDueDay`, normalizados como en el alta. Un
campo ausente o en `null` SHALL conservar su valor. La respuesta SHALL ser 200 con la cuenta tal
como quedó.

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

### Requirement: Formato del parche de una cuenta
Cada campo enviado SHALL cumplir las reglas de formato del alta y MUST NOT ir en blanco. Un parche
sin ningún campo modificable SHALL responder 400 con un error `VALIDATION_ERROR` en el campo
`body`. Ningún error de formato SHALL modificar la cuenta.

#### Scenario: Parche vacío
- **WHEN** el cuerpo es `{}` o solo trae campos en `null`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

#### Scenario: Nombre en blanco o demasiado largo
- **WHEN** el parche trae `"name": "   "` o un nombre de 81 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `name`

#### Scenario: Moneda mal formada
- **WHEN** el parche trae `"currencyCode": "us"` o `"currencyCode": " "`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `currencyCode`

#### Scenario: Montos y días fuera de rango
- **WHEN** el parche trae `"initialBalance": 1.23456`, `"creditLimit": 0`, `"statementDay": 32` y `"paymentDueDay": 0`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `initialBalance`, otro en `creditLimit`, otro en `statementDay` y otro en `paymentDueDay`

#### Scenario: Varios errores a la vez
- **WHEN** el parche trae `{"name": " ", "currencyCode": "us", "statementDay": 32}`
- **THEN** la respuesta es 400 con un error en `name`, otro en `currencyCode` y otro en `statementDay`

### Requirement: Campos que el parche no admite
`currentBalance`, `type` e `isActive` MUST NOT modificarse por el parche. Si cualquiera llega con
valor, la respuesta SHALL ser 400 con un error `VALIDATION_ERROR` en ese campo, sin modificar nada.

#### Scenario: Intentar fijar el saldo vigente, el tipo o el estado
- **WHEN** el parche trae `{"currentBalance": 1, "type": "CASH", "isActive": false}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `currentBalance`, otro en `type` y otro en `isActive`, y la cuenta no cambia

### Requirement: Campos de crédito según el tipo de la cuenta
`creditLimit`, `statementDay` y `paymentDueDay` SHALL admitirse solo sobre una cuenta `CREDIT`.
Sobre cualquier otro tipo, cada uno que llegue con valor SHALL responder 400 con un error
`VALIDATION_ERROR` en su campo, sin modificar nada.

#### Scenario: Campos de crédito sobre una cuenta de efectivo
- **WHEN** el parche trae `{"creditLimit": 1000000, "statementDay": 3}` sobre la cuenta `CASH` `Bolsillo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `creditLimit` y otro en `statementDay`

### Requirement: El saldo inicial corre el saldo vigente
Cambiar `initialBalance` SHALL desplazar `currentBalance` en la misma diferencia, de modo que el
saldo vigente siga siendo el saldo inicial más el efecto de los movimientos. SHALL permitirse con
o sin movimientos.

#### Scenario: Cuenta con movimientos
- **WHEN** la cuenta `Bolsillo` tiene saldo inicial 200000 y un gasto de 50000 (saldo vigente 150000) y el parche trae `{"initialBalance": 300000}`
- **THEN** la respuesta es 200 con `initialBalance` 300000 y `currentBalance` 250000

### Requirement: Moneda de una cuenta con movimientos
Un `currencyCode` distinto del actual SHALL existir y estar activo en el catálogo; si no, la
respuesta SHALL ser 400 `VALIDATION_ERROR` en `currencyCode`. Si la cuenta tiene movimientos como
origen o como destino, SHALL responder 409 `RESOURCE_IN_USE` en `currencyCode` sin modificar ningún
campo del parche. Enviar la moneda que ya tiene SHALL permitirse.

#### Scenario: Cuenta sin movimientos
- **WHEN** la tarjeta `Mastercard`, sin movimientos, recibe `{"currencyCode": "usd"}`
- **THEN** la respuesta es 200 con `currencyCode` `USD`

#### Scenario: Moneda inexistente
- **WHEN** el parche trae `{"currencyCode": "XYZ"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `currencyCode`

#### Scenario: Cuenta con movimientos
- **WHEN** la cuenta `Bolsillo` (`COP`) tiene un gasto y el parche trae `{"currencyCode": "USD", "name": "Otro"}`
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `currencyCode`, y la cuenta conserva su moneda y su nombre

#### Scenario: La misma moneda con movimientos
- **WHEN** la misma cuenta recibe `{"currencyCode": "cop"}`
- **THEN** la respuesta es 200 con `currencyCode` `COP`

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

#### Scenario: Cuenta desactivada
- **WHEN** el usuario del escenario de pruebas envía `{"name": "Nequi"}` sobre su cuenta desactivada `Nequi`
- **THEN** la respuesta es 200 con `isActive` en `false`

### Requirement: Nombre único al modificar
El nombre nuevo SHALL ser único entre las otras cuentas no borradas del usuario, sin distinguir
mayúsculas. Un nombre repetido SHALL responder 409 `DUPLICATE_RESOURCE` en el campo `name` sin
modificar nada. Cambiar solo las mayúsculas del nombre de la propia cuenta SHALL permitirse.

#### Scenario: Repite otra cuenta viva
- **WHEN** el usuario tiene `Bolsillo` y `Mastercard` y renombra `Bolsillo` a `MASTERCARD`
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`, y `Bolsillo` conserva su nombre

#### Scenario: Cambia solo las mayúsculas del propio nombre
- **WHEN** el usuario renombra `Bolsillo` a `BOLSILLO`
- **THEN** la respuesta es 200 con `name` `BOLSILLO`

### Requirement: Modificación autenticada con JWT
`PATCH /api/accounts/{id}` SHALL exigir un Bearer válido. Sin credencial o con la credencial Basic
compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`, sin modificar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `PATCH /api/accounts/{id}` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `PATCH /api/accounts/{id}` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`
