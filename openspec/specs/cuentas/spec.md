# cuentas Specification

## Purpose
Las cuentas del usuario —efectivo, bancos, tarjetas— de donde sale y adonde llega el dinero: cómo
se presentan en el API y cómo se corrigen sin descuadrar su saldo vigente.

## Requirements

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

### Requirement: Saldo vigente de una cuenta
El `currentBalance` de una cuenta, en `GET`, `POST` y `PATCH /api/accounts` y en
`GET /api/reports/balance`, SHALL ser el saldo vigente: el saldo inicial más el efecto de los
movimientos confirmados cuyo `occurredAt` ya llegó. Los programados no lo mueven hasta su fecha.
`availableCredit` SHALL ser `creditLimit` más ese mismo saldo vigente, menos el capital de las cuotas de
compras en cuotas de la tarjeta cuyo `occurredAt` todavía no llega.

#### Scenario: Tarjeta con un gasto programado
- **WHEN** la tarjeta Mastercard, con cupo 3000000 y saldo vigente -200000, tiene además un gasto programado de 100000
- **THEN** `GET /api/accounts` la trae con `currentBalance` -200000 y `availableCredit` 2800000

#### Scenario: Compra en cuotas registrada hoy
- **WHEN** una tarjeta con cupo 5000000 y saldo vigente 0 registra una compra de 1200000 a 3 cuotas, todas futuras
- **THEN** `GET /api/accounts` la trae con `currentBalance` 0 y `availableCredit` 3800000, y `GET /api/reports/balance` con el mismo `availableCredit`

#### Scenario: Una cuota de la compra ya venció
- **WHEN** la misma compra tiene vencida la primera cuota, de 400000 de capital y 24000 de interés
- **THEN** la tarjeta trae `currentBalance` -424000 y `availableCredit` 3776000

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

### Requirement: Solo se desactiva una cuenta sin series activas
Un parche con `isActive` en `false` sobre una cuenta activa que tiene series activas MUST NOT
aplicarse. Serie activa es una no cancelada con ocurrencias por venir, como en `GET /api/recurrences`.
La respuesta SHALL ser 409 con un error `RESOURCE_IN_USE` en el campo `isActive` cuya `description`
dice cuántas series tiene, y ningún campo del parche SHALL cambiar. Al cancelar sus series, la cuenta
SHALL poder desactivarse.

#### Scenario: Cuenta con una serie sin fin
- **WHEN** el usuario envía `{"isActive": false}` sobre su cuenta `Desactivable`, activa, sobre la que corre una serie sin fin
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `isActive` que dice que tiene 1 serie activa, y `Desactivable` sigue en `GET /api/accounts`

#### Scenario: El rechazo no aplica el resto del parche
- **WHEN** el parche trae `{"name": "Vieja", "isActive": false}` sobre `Desactivable` con su serie activa
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en `isActive`, y la cuenta conserva su nombre

#### Scenario: Desactivar tras cancelar la serie
- **WHEN** el usuario cancela con `DELETE /api/recurrences/{id}` la serie de `Desactivable` y después envía `{"isActive": false}`
- **THEN** la respuesta es 200 con `isActive` en `false`

#### Scenario: Una serie terminada no impide desactivar
- **WHEN** la única serie de la cuenta es una con fin cuyas ocurrencias ya pasaron todas, y el usuario envía `{"isActive": false}`
- **THEN** la respuesta es 200 con `isActive` en `false`
