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
`currentBalance` y `type` MUST NOT modificarse por el parche. Si cualquiera llega con valor, la
respuesta SHALL ser 400 con un error `VALIDATION_ERROR` en ese campo, sin modificar nada.

#### Scenario: Intentar fijar el saldo vigente, el tipo o el estado
- **WHEN** el parche trae `{"currentBalance": 1, "type": "CASH", "isActive": false}` sobre una cuenta activa
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `currentBalance` y otro en `type`, ninguno en `isActive`, y la cuenta no cambia: sigue activa

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

#### Scenario: Desactivar una cuenta de otro usuario
- **WHEN** un usuario recién registrado envía `{"isActive": false}` sobre la cuenta `Efectivo` del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la cuenta ajena sigue en el listado de su dueño sin `includeInactive`

#### Scenario: Reactivar una cuenta borrada
- **WHEN** el usuario del escenario de pruebas envía `{"isActive": true}` sobre su cuenta borrada `Davivienda`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

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

### Requirement: Borrado lógico de una cuenta
`DELETE /api/accounts/{id}` SHALL marcar la cuenta del usuario como borrada sin eliminar la fila, y
responder 204 sin cuerpo. Una cuenta desactivada SHALL poder borrarse igual que una activa. La
cuenta borrada MUST NOT aparecer en `GET /api/accounts`, tampoco con `includeInactive=true`, y su
nombre SHALL quedar libre para un alta o un PATCH.

#### Scenario: Borrar una cuenta en cero con movimientos
- **WHEN** el usuario borra su cuenta `Caja chica`, que tiene un gasto y saldo vigente 0
- **THEN** la respuesta es 204 sin cuerpo

#### Scenario: El listado ya no la trae
- **WHEN** después del borrado el usuario pide `GET /api/accounts?includeInactive=true`
- **THEN** la cuenta borrada no aparece y las demás siguen

#### Scenario: El nombre queda libre
- **WHEN** después del borrado el usuario crea una cuenta llamada `caja chica`
- **THEN** la respuesta es 201 con un `id` distinto del de la cuenta borrada

### Requirement: Solo se borra una cuenta en cero
Una cuenta con `currentBalance` distinto de cero, a favor o en deuda, MUST NOT borrarse: la
respuesta SHALL ser 409 con un error `RESOURCE_IN_USE` en el campo `currentBalance`, y la cuenta no
cambia.

#### Scenario: Cuenta con saldo
- **WHEN** el usuario borra `Caja chica` mientras su saldo vigente es -30000 por un gasto
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `currentBalance`, y la cuenta sigue en el listado

#### Scenario: Dejarla en cero y borrarla
- **WHEN** el usuario corrige el `initialBalance` de `Caja chica` a 30000, con lo que su saldo vigente queda en 0, y la borra
- **THEN** la respuesta es 204

#### Scenario: Cuenta desactivada con saldo
- **WHEN** el usuario del escenario de pruebas borra su cuenta desactivada `Nequi`, con saldo 80000
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `currentBalance`

### Requirement: Cuenta a borrar
El `{id}` SHALL ser un UUID; si no lo es, la respuesta SHALL ser 400 `VALIDATION_ERROR` en el campo
`id`. Una cuenta que no existe, que ya está borrada o que es de otro usuario SHALL responder 404
`NOT_FOUND` en el campo `id`, sin distinguir entre los tres casos y sin modificar nada.

#### Scenario: Id mal formado
- **WHEN** el usuario envía `DELETE /api/accounts/abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

#### Scenario: Cuenta inexistente
- **WHEN** el `{id}` es un UUID que no corresponde a ninguna cuenta
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Cuenta ya borrada
- **WHEN** el usuario borra otra vez la cuenta que acaba de borrar
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Cuenta de otro usuario
- **WHEN** un usuario recién registrado borra la cuenta `Efectivo` del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la cuenta ajena sigue en el listado de su dueño

### Requirement: Movimientos de una cuenta borrada
Los movimientos de una cuenta borrada SHALL quedar como estaban. Un PATCH de uno de ellos que no
elige la cuenta borrada SHALL aceptarse. Un movimiento nuevo, o un PATCH, que elija la cuenta
borrada como origen o destino SHALL responder 400 `VALIDATION_ERROR` en ese campo, como con una
cuenta que no existe.

#### Scenario: Modificar un gasto de la cuenta borrada
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` con `{"description": "Corregido"}` sobre el gasto de la cuenta borrada
- **THEN** la respuesta es 200 con la descripción nueva y el `accountId` de la cuenta borrada

#### Scenario: Registrar un gasto en la cuenta borrada
- **WHEN** el usuario envía `POST /api/transactions` con un gasto en la cuenta borrada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].accountId`

### Requirement: Borrado autenticado con JWT
`DELETE /api/accounts/{id}` SHALL exigir un Bearer válido. Sin credencial o con la credencial Basic
compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`, sin borrar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `DELETE /api/accounts/{id}` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `DELETE /api/accounts/{id}` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

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
