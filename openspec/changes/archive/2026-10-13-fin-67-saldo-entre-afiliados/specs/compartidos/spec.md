# Spec Delta

## ADDED Requirements

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
