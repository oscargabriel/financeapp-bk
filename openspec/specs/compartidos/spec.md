# compartidos Specification

## Purpose
Compartir un gasto o una transferencia con personas afiliadas (FIN-66, epic FIN-9): las partes que
se indican al registrarlo, el pendiente que recibe cada persona, su aprobación con cuenta y
categoría, su rechazo, la deuda que queda abierta y lo que se puede hacer después con el gasto. Lo
que el usuario y cada persona se deben, y el detalle de sus partes, salen bajo `/api/affiliations`
(FIN-67).

## Requirements

### Requirement: Partes de un movimiento en el alta
Cada elemento de `POST /api/transactions` de tipo `EXPENSE` o `TRANSFER` SHALL aceptar `shares`: una
lista de `{affiliationId, amount}`, una por persona. `shares` ausente, `null` o `[]` SHALL ser un
movimiento sin compartir, como hoy. Cada `amount` SHALL estar en la moneda en que llega el `amount`
del elemento: su `currencyCode`, o la de la cuenta si no trae.

#### Scenario: Gasto con dos personas
- **WHEN** A registra un gasto de 90000 con `shares` `[{affiliationId: AB, amount: 30000}, {affiliationId: AC, amount: 30000}]`, siendo AB y AC sus afiliaciones aceptadas con B y C
- **THEN** la respuesta es 201 con el gasto de 90000 y sus dos partes, B y C tienen cada uno un pendiente de 30000, y el lote no cambia en lo demás

#### Scenario: Sin partes
- **WHEN** A registra un gasto con `"shares": []`
- **THEN** la respuesta es 201 con `"shares": null`, como un gasto sin compartir

### Requirement: Reglas de las partes
Cada parte SHALL traer `affiliationId` y un `amount` mayor que cero con hasta 4 decimales. Ninguna
afiliación SHALL repetirse en un elemento. En un gasto la suma SHALL ser menor o igual al total; en
una transferencia, igual. Un `INCOME` con partes SHALL ser un error. Cada regla incumplida SHALL
responder 400 `VALIDATION_ERROR` con su índice y SHALL rechazar el lote entero sin guardar nada.

#### Scenario: Monto en cero
- **WHEN** un elemento trae una parte con `"amount": 0` en la posición 1
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[1].amount`, y no se guarda ningún movimiento ni parte

#### Scenario: Monto con más de 4 decimales
- **WHEN** una parte trae `"amount": 10.12345`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].amount`

#### Scenario: Sin afiliación
- **WHEN** una parte no trae `affiliationId`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].affiliationId`

#### Scenario: Persona repetida
- **WHEN** un elemento trae dos partes con el mismo `affiliationId`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares`

#### Scenario: Las partes superan el total de un gasto
- **WHEN** un gasto de 50000 trae partes de 30000 y 25000
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares`, y no se guarda nada

#### Scenario: Las partes suman el total de un gasto
- **WHEN** un gasto de 50000 trae partes de 30000 y 20000
- **THEN** la respuesta es 201: quien pagó no se queda con parte

#### Scenario: Un ingreso con partes
- **WHEN** un elemento de tipo `INCOME` trae `shares` con una parte
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares`

#### Scenario: El error de un elemento rechaza todo el lote
- **WHEN** un lote trae un gasto válido sin partes y, en la posición 1, un gasto con una parte en cero
- **THEN** la respuesta es 400 con un error en `[1].shares[0].amount`, y el gasto sin partes tampoco se guarda

### Requirement: Solo se comparte con personas afiliadas
`affiliationId` SHALL ser una afiliación en la que participa el usuario y que está `ACCEPTED`. Si no
existe, es de otras personas, o está solicitada, rechazada, cancelada o desvinculada, SHALL responder
400 `VALIDATION_ERROR` en `[i].shares[j].affiliationId`, con el mismo error en todos los casos, y SHALL
NOT guardar nada del lote.

#### Scenario: Afiliación desvinculada
- **WHEN** A comparte un gasto usando el `affiliationId` de una afiliación con B que ya desvinculó
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].affiliationId`, y B no recibe ningún pendiente

#### Scenario: Solicitud sin aceptar
- **WHEN** A comparte un gasto usando el id de una solicitud que B no ha aceptado
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].affiliationId`

#### Scenario: Afiliación de otras personas
- **WHEN** A comparte un gasto usando el id de una afiliación aceptada entre B y C
- **THEN** la respuesta es 400 con el mismo error que una afiliación inexistente

#### Scenario: Id inexistente o mal formado
- **WHEN** una parte trae un `affiliationId` que no es UUID o que no corresponde a ninguna afiliación
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].affiliationId`

### Requirement: Gasto compartido
Un gasto con partes SHALL registrarse para quien paga como cualquier gasto: en su cuenta y su
categoría, por el total, y moviendo su saldo igual. Por cada parte SHALL crearse para esa persona un
gasto `PENDING` por el monto de la parte, en su moneda, sin cuenta ni categoría, con la descripción y
la fecha del gasto, con `sharedBy` quien pagó y con un `groupId` propio.

#### Scenario: El total en la cuenta de quien paga
- **WHEN** A registra un gasto compartido de 90000 en su cuenta con saldo 200000 y partes de 30000 para B y C
- **THEN** el `currentBalance` de la cuenta de A baja 90000

#### Scenario: El pendiente de quien debe
- **WHEN** B pide `GET /api/transactions/pending` después de que A compartió con él 30000 de «Cena»
- **THEN** sale un gasto `PENDING` de 30000 con `accountId` y `categoryId` en `null`, `description` «Cena», el `occurredAt` del gasto de A, `sharedBy` con el correo y el nombre de A y un `groupId` que no comparte con nadie

#### Scenario: Gasto compartido en otra moneda
- **WHEN** A registra un gasto de 90 USD con una cuenta en COP y una parte de 30 USD para B
- **THEN** el gasto de A entra convertido a COP y `PENDING`, como todo lo convertido, y B recibe un pendiente de 30 en USD

#### Scenario: Gasto compartido con fecha futura
- **WHEN** A registra un gasto compartido con fecha posterior a hoy
- **THEN** el gasto de A queda programado, y el pendiente de B tiene la misma fecha y sale con `"scheduled": true`

#### Scenario: El pendiente no mueve saldos de quien debe
- **WHEN** B tiene el pendiente compartido sin aprobar
- **THEN** ninguna cuenta de B cambia de saldo

### Requirement: La parte pendiente es deuda
Mientras una parte esté pendiente SHALL contar como deuda de esa persona con quien pagó. El gasto de
quien pagó SHALL traer en `shares` cada parte con su persona, su monto y su estado: `PENDING`, `PAID`,
`REJECTED` o `VOIDED`.

#### Scenario: Partes recién creadas
- **WHEN** A registra un gasto compartido con B y C
- **THEN** la respuesta trae `shares` con dos elementos, cada uno con `person.email`, `amount`, `currencyCode` y `"status": "PENDING"`

#### Scenario: Una parte pagada y otra rechazada
- **WHEN** B aprueba su parte, C rechaza la suya y A pide el reporte de movimientos que incluye su gasto
- **THEN** el gasto trae la parte de B con `"status": "PAID"` y la de C con `"status": "REJECTED"`

### Requirement: Transferencia compartida
Un elemento `TRANSFER` con partes SHALL NOT traer `destinationAccountId` ni `destinationAmount`, y
SHALL traer `categoryId` de una categoría de gasto del usuario. Para quien la hace SHALL registrarse
como un `EXPENSE` de esa categoría por el total. Cada persona SHALL recibir un ingreso `PENDING` por su
monto, sin cuenta ni categoría, con `sharedBy` quien transfirió. No SHALL crear partes ni deuda.

#### Scenario: Transferencia a dos personas
- **WHEN** A registra una `TRANSFER` de 100000 con `categoryId` de gasto, sin destino, y partes de 60000 para B y 40000 para C
- **THEN** la respuesta es 201 con un movimiento `"type": "EXPENSE"` de 100000 y `"shares": null`, el saldo de A baja 100000, B tiene un ingreso pendiente de 60000 y C uno de 40000, los dos con `sharedBy` A

#### Scenario: Partes que no suman el total
- **WHEN** una transferencia compartida de 100000 trae partes que suman 90000
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares`

#### Scenario: Transferencia compartida con cuenta destino
- **WHEN** una transferencia con partes trae `destinationAccountId`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].destinationAccountId`

#### Scenario: Transferencia compartida sin categoría
- **WHEN** una transferencia con partes no trae `categoryId`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].categoryId`

#### Scenario: Transferencia compartida con una categoría de ingreso
- **WHEN** una transferencia con partes trae el `categoryId` de una categoría de ingreso
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].categoryId`

#### Scenario: Transferencia compartida con monto de destino
- **WHEN** una transferencia con partes trae `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].destinationAmount`

### Requirement: Aprobar un pendiente compartido
`POST /api/transactions/{id}/approve` sobre un pendiente sin cuenta SHALL exigir un cuerpo con
`accountId` y `categoryId` del usuario, de categoría del tipo del pendiente. SHALL confirmarlo con
esa cuenta y esa categoría, moviendo el saldo, y responder 200 con el movimiento completo.

#### Scenario: Aprobar un ingreso compartido
- **WHEN** B aprueba el ingreso pendiente de 60000 de una transferencia compartida con `{accountId, categoryId}` de una categoría de ingreso
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"`, esa cuenta y esa categoría, y el saldo de la cuenta de B sube 60000

#### Scenario: Aprobar sin cuerpo
- **WHEN** B aprueba su pendiente compartido sin cuerpo
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `accountId` y otro en `categoryId`, y el pendiente sigue pendiente

### Requirement: Aprobar una parte es pagarla
Aprobar el gasto pendiente de una parte SHALL dejar la parte `PAID` y crear para quien pagó un
ingreso `PENDING` por el monto de la parte, en su moneda, sin cuenta ni categoría, con la descripción
del gasto, `sharedBy` quien aprobó, la fecha de la aprobación y un `groupId` propio. Todo SHALL ocurrir
junto o nada.

#### Scenario: B paga su parte
- **WHEN** B aprueba su parte de 30000 con una cuenta propia y una categoría de gasto
- **THEN** la respuesta es 200 con el gasto de B `CONFIRMED`, el saldo de su cuenta baja 30000, la parte sale `PAID` en el gasto de A, y A tiene un ingreso pendiente de 30000 con `sharedBy` B

#### Scenario: Quien pagó aprueba el ingreso
- **WHEN** A aprueba ese ingreso con una cuenta propia y una categoría de ingreso
- **THEN** la respuesta es 200 y el saldo de la cuenta de A sube 30000; la parte sigue `PAID`

#### Scenario: Quien pagó rechaza el ingreso
- **WHEN** A rechaza ese ingreso pendiente
- **THEN** la respuesta es 204, el ingreso deja de existir y la parte sigue `PAID`

### Requirement: Validaciones al aprobar con cuenta y categoría
Al aprobar con cuerpo, una cuenta inexistente, ajena, desactivada o mal formada SHALL responder 400
`VALIDATION_ERROR` en `accountId`, y una categoría inexistente, borrada o de otro tipo, 400 en
`categoryId`. Con referencias válidas, el saldo SHALL validarse como al aprobar cualquier pendiente:
409 `INSUFFICIENT_FUNDS` en `amount`. En todos los casos nada SHALL cambiar.

#### Scenario: Cuenta de otro usuario
- **WHEN** B aprueba su parte con el `accountId` de una cuenta de A
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `accountId`, y la parte sigue `PENDING`

#### Scenario: Cuenta desactivada
- **WHEN** B aprueba su parte con una cuenta propia desactivada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `accountId`

#### Scenario: Categoría de ingreso para un gasto
- **WHEN** B aprueba su parte con una categoría de ingreso
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `categoryId`

#### Scenario: Saldo insuficiente
- **WHEN** B aprueba su parte de 30000 con una cuenta `DEBIT` con saldo 10000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount`, la parte sigue `PENDING` y A no recibe ningún ingreso

#### Scenario: Cuerpo en un pendiente que ya tiene cuenta
- **WHEN** el usuario aprueba con `{accountId}` un pendiente convertido que ya tiene cuenta
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `accountId`, y el pendiente sigue pendiente

### Requirement: Aprobar con una cuenta en otra moneda
Si la cuenta elegida está en otra moneda que el pendiente, el monto SHALL convertirse a la moneda de
la cuenta con la tasa de la fecha del pendiente, y el movimiento SHALL guardar `originalAmount` y
`originalCurrencyCode`. Quedará `CONFIRMED`, no pendiente. La parte SHALL quedar pagada por su monto,
en su moneda.

#### Scenario: Parte en USD pagada desde una cuenta en COP
- **WHEN** B aprueba una parte de 30 USD con una cuenta en COP y la tasa de esa fecha es 4100
- **THEN** la respuesta es 200 con `amount` 123000, `currencyCode` COP, `originalAmount` 30, `originalCurrencyCode` USD y `"status": "CONFIRMED"`, y A recibe un ingreso pendiente de 30 USD

#### Scenario: Sin tasa disponible
- **WHEN** no hay ninguna tasa entre las dos monedas y el proveedor no responde
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en `server`, y nada cambia

### Requirement: Rechazar una parte
Rechazar el gasto pendiente de una parte SHALL dejar la parte `REJECTED`, borrar el pendiente y
responder 204. La parte SHALL dejar de ser deuda. El gasto de quien pagó SHALL NOT cambiar.

#### Scenario: C rechaza su parte
- **WHEN** C rechaza su pendiente de 30000
- **THEN** la respuesta es 204, el pendiente deja de salir en su lista, la parte sale `REJECTED` en el gasto de A, y el saldo y el monto del gasto de A no cambian

#### Scenario: Descartar el grupo de una parte
- **WHEN** C descarta con `POST /api/transactions/groups/{groupId}/reject` el grupo de su pendiente compartido
- **THEN** la respuesta es 204 y la parte queda `REJECTED`, igual que al rechazarlo uno a uno

#### Scenario: Rechazar partes en lote
- **WHEN** A rechaza con `POST /api/transactions/pending/reject` los pendientes de su parte de un gasto de B y de su parte de un gasto de C
- **THEN** la respuesta es 204, las dos partes quedan `REJECTED`, dejan de ser deuda en `GET /api/affiliations/balances`, y los gastos de B y de C no cambian

#### Scenario: Un lote que falla no rechaza partes
- **WHEN** A rechaza en lote el pendiente de su parte de un gasto de B junto con un id que no existe
- **THEN** la respuesta es 404 y la parte sigue `PENDING`

### Requirement: Un pendiente compartido solo se aprueba o se rechaza
Un pendiente con `sharedBy` SHALL NOT modificarse ni borrarse: `PATCH` y `DELETE` sobre él SHALL
responder 409 `INVALID_STATE` en `status`. Confirmar su grupo SHALL responder 409 `INVALID_STATE` en
`[i].accountId`, porque solo se aprueba uno a uno con cuenta y categoría. Ninguno SHALL cambiar nada.

#### Scenario: PATCH sobre la parte
- **WHEN** B envía `PATCH /api/transactions/{id}` con `{"amount": 1000}` sobre su pendiente compartido
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `status`, y el pendiente sigue con su monto

#### Scenario: DELETE sobre la parte
- **WHEN** B envía `DELETE /api/transactions/{id}` sobre su pendiente compartido
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `status`, y la parte sigue `PENDING`

#### Scenario: Confirmar el grupo de una parte
- **WHEN** B confirma con `POST /api/transactions/groups/{groupId}/approve` el grupo de su pendiente compartido
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `[0].accountId`, y el pendiente sigue pendiente

### Requirement: El monto de un gasto con partes pendientes no cambia
Mientras el gasto de quien pagó tenga alguna parte `PENDING`, un `PATCH` que cambie `amount` o `type`
SHALL responder 409 `INVALID_STATE` en ese campo sin cambiar nada. Los demás campos SHALL poder
cambiarse. Sin partes pendientes, el gasto SHALL modificarse como cualquier otro.

#### Scenario: Cambiar el monto con partes pendientes
- **WHEN** A envía `PATCH` con `{"amount": 100000}` sobre su gasto compartido con una parte pendiente
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `amount`, y el gasto sigue con su monto

#### Scenario: Cambiar el tipo con partes pendientes
- **WHEN** A envía `PATCH` con `{"type": "INCOME", "categoryId": ...}` sobre su gasto compartido con una parte pendiente
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `type`

#### Scenario: Cambiar la descripción
- **WHEN** A envía `PATCH` con `{"description": "Cena de cumpleaños"}` sobre su gasto compartido con partes pendientes
- **THEN** la respuesta es 200 con la descripción nueva

#### Scenario: Sin partes pendientes
- **WHEN** todas las partes del gasto de A están pagadas o rechazadas y A cambia su monto
- **THEN** la respuesta es 200 con el monto nuevo, y las partes no cambian

### Requirement: Borrar un gasto compartido
Borrar el gasto de quien pagó SHALL anular sus partes `PENDING` (`VOIDED`) y borrar los pendientes de
esas partes, en la misma operación. Las partes `PAID` y `REJECTED`, y los movimientos que ya crearon,
SHALL NOT cambiar. Vale para `DELETE`, y para rechazar o descartar el gasto si quedó pendiente por
convertirse.

#### Scenario: Borrar con una parte pendiente y otra pagada
- **WHEN** A borra su gasto compartido después de que B pagó su parte y con la de C pendiente
- **THEN** la respuesta es 204, C ya no tiene el pendiente, el gasto confirmado de B y el ingreso de A no cambian

#### Scenario: Rechazar un gasto compartido convertido
- **WHEN** A rechaza su gasto compartido que entró pendiente por venir en otra moneda
- **THEN** la respuesta es 204 y B ya no tiene el pendiente de su parte

### Requirement: Desvincular no cambia lo compartido
Desvincular a una persona SHALL NOT cambiar las partes ni los pendientes que ya existían entre las
dos: se siguen aprobando y rechazando, y el ingreso del pago sigue llegando. Desde ese momento SHALL
NOT poderse compartir nada nuevo con esa afiliación.

#### Scenario: Pagar después de desvincular
- **WHEN** A comparte un gasto con B, uno de los dos desvincula, y B aprueba su parte
- **THEN** la respuesta es 200, la parte queda `PAID` y A recibe el ingreso pendiente con `sharedBy` B

#### Scenario: Compartir después de desvincular
- **WHEN** A intenta compartir un gasto nuevo con la afiliación desvinculada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `[0].shares[0].affiliationId`

### Requirement: Un pendiente compartido es solo de quien lo recibe
Quien compartió SHALL NOT ver, aprobar, rechazar, modificar ni borrar el pendiente de otra persona:
SHALL responder 404 `NOT_FOUND` en `id`, como a cualquier movimiento ajeno.

#### Scenario: Quien pagó intenta aprobar la parte de otro
- **WHEN** A envía `POST /api/transactions/{id}/approve` con el id del pendiente de B
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en `id`, y la parte sigue `PENDING`

#### Scenario: La lista de quien pagó
- **WHEN** A pide `GET /api/transactions/pending` después de compartir un gasto confirmado
- **THEN** no salen los pendientes de B ni de C

### Requirement: Campos de compartido en la respuesta
Toda respuesta de un movimiento SHALL traer `sharedBy`, `sharedFrom` y `shares`, en `null` cuando no
aplican. `sharedBy` es `{email, firstName, lastName}` de quien lo compartió. `sharedFrom` es
`{description, amount, currencyCode}` del gasto del que sale una parte, mientras exista. `shares` es
la lista de partes del gasto de quien pagó.

#### Scenario: Movimiento sin compartir
- **WHEN** A registra un gasto sin partes
- **THEN** la respuesta trae `"sharedBy": null`, `"sharedFrom": null` y `"shares": null`

#### Scenario: El pendiente de una parte
- **WHEN** B pide sus pendientes con la parte de 30000 de un gasto de 90000 de A en COP
- **THEN** el pendiente trae `sharedBy` con el correo de A, `sharedFrom` con la descripción del gasto, `amount` 90000 y `currencyCode` COP, y `"shares": null`

#### Scenario: El gasto de quien pagó
- **WHEN** A recibe la respuesta del alta de su gasto compartido con B
- **THEN** el gasto trae `"sharedBy": null`, `"sharedFrom": null` y `shares` con `id`, `person` (`email`, `firstName`, `lastName`), `amount`, `currencyCode` y `status` de cada parte

#### Scenario: El ingreso de una transferencia compartida
- **WHEN** B pide sus pendientes con el ingreso de una transferencia compartida de A
- **THEN** el ingreso trae `sharedBy` con el correo de A y `"sharedFrom": null`, porque una transferencia no tiene partes

### Requirement: Compartir autenticado con JWT
El alta con partes y la aprobación con cuerpo SHALL exigir un Bearer JWT válido. Sin credencial, o
con la credencial Basic compartida, SHALL responder 401 sin cambiar nada.

#### Scenario: Alta con partes sin credencial
- **WHEN** se envía `POST /api/transactions` con un gasto con partes y sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`, y nadie recibe un pendiente

#### Scenario: Alta con partes con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions` con un gasto con partes y el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Aprobar con cuerpo sin credencial
- **WHEN** se envía `POST /api/transactions/{id}/approve` con `{accountId, categoryId}` y sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`, y la parte sigue `PENDING`

### Requirement: Saldo con cada persona
`GET /api/affiliations/balances` SHALL responder 200 con una entrada por persona: `affiliationId`,
`status` (`ACCEPTED` o `UNLINKED`), `person` (`email`, `firstName`, `lastName` actuales) y `balances`,
una entrada por moneda con `currencyCode`, `owedToMe`, `iOwe` y `net`. SHALL ordenarse de la más
reciente a la más antigua por la fecha de creación de su afiliación. Sin personas, `[]`.

#### Scenario: Afiliada que debe en dos monedas
- **WHEN** A pagó un gasto con una parte pendiente de 30000 COP y otro con una parte pendiente de 20 USD de B, y A pide el saldo
- **THEN** la respuesta es 200 con una entrada para B con `"status": "ACCEPTED"`, su correo y su nombre, y `balances` `[{currencyCode: COP, owedToMe: 30000, iOwe: 0, net: 30000}, {currencyCode: USD, owedToMe: 20, iOwe: 0, net: 20}]`

#### Scenario: Afiliada sin deudas
- **WHEN** A y C están afiliados y no tienen ninguna parte pendiente entre los dos
- **THEN** C sale en el saldo de A con `"status": "ACCEPTED"` y `"balances": []`

#### Scenario: Sin afiliaciones
- **WHEN** el usuario no tiene ninguna afiliación aceptada ni desvinculada
- **THEN** la respuesta es 200 con `[]`

### Requirement: El saldo es la deuda pendiente entre los dos
Por cada moneda, `owedToMe` SHALL ser la suma de las partes `PENDING` que la persona le debe al
usuario, `iOwe` la de las partes `PENDING` que el usuario le debe a ella, y `net` SHALL ser
`owedToMe - iOwe`. Las partes `PAID`, `REJECTED` y `VOIDED` SHALL NOT contar. Las monedas SHALL NOT
convertirse entre sí, y SHALL salir solo las que tengan alguna parte pendiente, ordenadas por código.

#### Scenario: Deuda en los dos sentidos
- **WHEN** B le debe a A una parte pendiente de 30000 COP y A le debe a B una de 10000 COP
- **THEN** A ve a B con `{currencyCode: COP, owedToMe: 30000, iOwe: 10000, net: 20000}`, y B ve a A con `{currencyCode: COP, owedToMe: 10000, iOwe: 30000, net: -20000}`

#### Scenario: Una parte pagada deja de contar
- **WHEN** B paga su parte de 30000 COP y A le sigue debiendo 10000 COP
- **THEN** A ve a B con `{currencyCode: COP, owedToMe: 0, iOwe: 10000, net: -10000}`

#### Scenario: Una parte rechazada deja de contar
- **WHEN** C rechaza su única parte pendiente con A
- **THEN** C sale en el saldo de A con `"balances": []`

#### Scenario: Las transferencias compartidas no cuentan
- **WHEN** A hace una transferencia compartida a B
- **THEN** el saldo de A con B no cambia

### Requirement: Una persona desvinculada con deuda sigue en el saldo
Una persona sin afiliación `ACCEPTED` con el usuario SHALL salir en el saldo si tiene con él alguna
parte `PENDING`, con `"status": "UNLINKED"` y el `affiliationId` de la desvinculación más reciente. Sin
partes pendientes SHALL NOT salir. Con una afiliación `ACCEPTED` vigente, `affiliationId` SHALL ser el
de esa afiliación.

#### Scenario: Desvinculada con una parte pendiente
- **WHEN** A comparte con C un gasto con una parte de 15000 COP y después desvincula a C
- **THEN** C sale en el saldo de A con `"status": "UNLINKED"` y `{currencyCode: COP, owedToMe: 15000, iOwe: 0, net: 15000}`, y A sale en el saldo de C con `"status": "UNLINKED"` y `net` -15000

#### Scenario: Desvinculada que paga lo pendiente
- **WHEN** C paga su última parte pendiente con A después de desvincularse
- **THEN** C deja de salir en el saldo de A

#### Scenario: Afiliados de nuevo
- **WHEN** A y B se desvincularon, quedaron con una parte pendiente, y se afiliaron otra vez
- **THEN** B sale una sola vez en el saldo de A, con `"status": "ACCEPTED"`, el `affiliationId` de la afiliación nueva y la parte pendiente en `balances`

### Requirement: Detalle de las partes con una persona
`GET /api/affiliations/{id}/shares` SHALL responder 200 con las partes entre el usuario y la otra
persona de esa afiliación, en cualquier estado y de cualquier afiliación entre los dos, de la más
reciente a la más antigua por `occurredAt`. Cada parte SHALL traer `id`, `description`, `occurredAt`,
`amount`, `currencyCode`, `status`, `resolvedAt` y `direction`. Sin partes, `[]`.

#### Scenario: Partes en los dos sentidos y en varios estados
- **WHEN** A pagó «Cena» con una parte de B ya pagada y «Hotel» con una parte de B pendiente en USD, B pagó «Taxi» con una parte pendiente de A, y A pide el detalle con su afiliación con B
- **THEN** la respuesta es 200 con tres partes: «Cena» `PAID` y `"direction": "OWED_TO_ME"` con `resolvedAt`, «Hotel» `PENDING`, `OWED_TO_ME`, en USD y con `"resolvedAt": null`, y «Taxi» `PENDING` con `"direction": "I_OWE"`

#### Scenario: Una parte rechazada, vista por quien pagó
- **WHEN** C rechazó su parte de «Cena» y A pide el detalle con C
- **THEN** la parte sale con `"status": "REJECTED"` y `"direction": "OWED_TO_ME"`

#### Scenario: Una parte anulada
- **WHEN** A borró un gasto compartido con una parte pendiente de B, y B pide el detalle con A
- **THEN** la parte sale con `"status": "VOIDED"` y `"direction": "I_OWE"`

#### Scenario: Detalle con una persona desvinculada
- **WHEN** A pide el detalle con el `affiliationId` de su afiliación desvinculada con C
- **THEN** la respuesta es 200 con las partes entre A y C

#### Scenario: Afiliados sin partes
- **WHEN** A pide el detalle con una afiliación aceptada con quien no comparte nada
- **THEN** la respuesta es 200 con `[]`

### Requirement: Solo las partes del usuario
El saldo y el detalle SHALL contar y listar solo partes en las que el usuario pagó o debe. Las partes
entre la otra persona y terceros SHALL NOT salir ni sumar.

#### Scenario: Partes entre dos afiliados del usuario
- **WHEN** A está afiliado con B y con C, y B comparte con C un gasto
- **THEN** esa parte no sale en el detalle de A con B ni en el de A con C, y no suma en el saldo de A

### Requirement: Afiliaciones que no sirven para el detalle
En `GET /api/affiliations/{id}/shares`, un `id` que no es UUID SHALL responder 400 `VALIDATION_ERROR`
en `id`. Una afiliación inexistente o en la que el usuario no participa SHALL responder 404
`NOT_FOUND` en `id`. Una afiliación del usuario que no está `ACCEPTED` ni `UNLINKED` SHALL responder 409
`INVALID_STATE` en `status`.

#### Scenario: Id mal formado
- **WHEN** A pide `GET /api/affiliations/abc/shares`
- **THEN** la respuesta es 400 con code `VALIDATION_ERROR` y field `id`

#### Scenario: Afiliación ajena
- **WHEN** A pide el detalle con el id de la afiliación entre B y C
- **THEN** la respuesta es 404 con code `NOT_FOUND` y field `id`, igual que con un UUID que no es de ninguna afiliación

#### Scenario: Solicitud sin aceptar
- **WHEN** A pide el detalle con el id de una solicitud que envió y sigue `REQUESTED`
- **THEN** la respuesta es 409 con code `INVALID_STATE` y field `status`

### Requirement: Autenticación del saldo y el detalle
Las dos rutas SHALL exigir un Bearer válido. Sin credencial, o con el Basic compartido, SHALL
responder 401 `UNAUTHENTICATED` en `authorization`, con `WWW-Authenticate: Bearer`.

#### Scenario: Sin credencial
- **WHEN** se pide `GET /api/affiliations/balances` o `GET /api/affiliations/{id}/shares` sin `Authorization`
- **THEN** la respuesta es 401 con code `UNAUTHENTICATED`, field `authorization` y `WWW-Authenticate` `Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se pide cualquiera de las dos rutas con el Basic de `/auth/*`
- **THEN** la respuesta es 401 con code `UNAUTHENTICATED`, field `authorization` y `WWW-Authenticate` `Bearer`
