## ADDED Requirements

### Requirement: Estado de aprobación de un movimiento
Todo movimiento SHALL tener un estado, `PENDING` o `CONFIRMED`. Los que entran por
`POST /api/transactions` SHALL quedar `CONFIRMED`. La respuesta de un movimiento SHALL incluir el
campo `status` en el alta, la modificación, la aprobación y la lista de pendientes.

#### Scenario: El alta entra confirmada
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido
- **THEN** la respuesta es 201 y cada movimiento devuelve `"status": "CONFIRMED"`

#### Scenario: El cliente no puede elegir el estado
- **WHEN** un elemento del lote trae `"status": "PENDING"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"status": "CONFIRMED"`

### Requirement: Un pendiente no mueve saldos
Un movimiento `PENDING` SHALL dejar el `currentBalance` de sus cuentas como estaba, y SHALL
moverlo al aprobarse, como si se hubiera registrado confirmado.

#### Scenario: Gasto pendiente
- **WHEN** el usuario tiene un gasto pendiente de 45000 sobre una cuenta
- **THEN** el `currentBalance` de esa cuenta en `GET /api/accounts` no lo descuenta

#### Scenario: Transferencia pendiente
- **WHEN** el usuario tiene una transferencia pendiente entre dos cuentas propias
- **THEN** ninguna de las dos cuentas cambia de saldo

### Requirement: Lista de movimientos pendientes
`GET /api/transactions/pending` SHALL devolver 200 con los movimientos `PENDING` del usuario, del
más reciente al más antiguo por `occurredAt`. Cada uno SHALL tener los mismos campos que la
respuesta del alta. No SHALL incluir movimientos confirmados ni pendientes de otro usuario.

#### Scenario: Usuario con pendientes
- **WHEN** el usuario tiene un gasto y una transferencia pendientes, y otros movimientos confirmados
- **THEN** la respuesta es 200 con exactamente los dos pendientes, ordenados por `occurredAt` descendente, cada uno con `"status": "PENDING"`

#### Scenario: Usuario sin pendientes
- **WHEN** el usuario no tiene ningún movimiento pendiente
- **THEN** la respuesta es 200 con una lista vacía

#### Scenario: Pendientes de otro usuario
- **WHEN** otro usuario tiene movimientos pendientes
- **THEN** no aparecen en la lista del usuario autenticado

### Requirement: Aprobación de un pendiente
`POST /api/transactions/{id}/approve` sobre un pendiente del usuario SHALL confirmarlo y responder
200 con el movimiento completo y `"status": "CONFIRMED"`. Desde ese momento SHALL mover los saldos y
contar en los reportes.

#### Scenario: Aprobar un gasto
- **WHEN** el usuario aprueba un gasto pendiente de 45000
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"`, el `currentBalance` de su cuenta baja 45000 y el gasto deja de salir en `GET /api/transactions/pending`

#### Scenario: Aprobar una transferencia
- **WHEN** el usuario aprueba una transferencia pendiente de 100000 de la cuenta A a la cuenta B
- **THEN** la respuesta es 200, el saldo de A baja 100000 y el de B sube 100000

### Requirement: Rechazo de un pendiente
`POST /api/transactions/{id}/reject` sobre un pendiente del usuario SHALL borrarlo y responder 204
sin cuerpo. No SHALL quedar rastro en saldos, reportes ni en la lista de pendientes.

#### Scenario: Rechazar un pendiente
- **WHEN** el usuario rechaza un gasto pendiente
- **THEN** la respuesta es 204, el gasto deja de salir en `GET /api/transactions/pending` y el saldo de su cuenta no cambia

#### Scenario: Rechazar dos veces
- **WHEN** el usuario repite el rechazo sobre el mismo id
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

### Requirement: Aprobar o rechazar lo que no es un pendiente propio
Sobre un id inexistente o de otro usuario, aprobar y rechazar SHALL responder 404 con el mismo error,
sin revelar si existe. Sobre un movimiento propio ya confirmado SHALL responder 409. Un `{id}` que no
es UUID SHALL responder 400. Ninguno de estos casos SHALL cambiar nada.

#### Scenario: Aprobar un movimiento de otro usuario
- **WHEN** el usuario envía `POST /api/transactions/{id}/approve` con el id de un pendiente de otro usuario
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y el movimiento sigue pendiente

#### Scenario: Rechazar un movimiento de otro usuario
- **WHEN** el usuario envía `POST /api/transactions/{id}/reject` con el id de un pendiente de otro usuario
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y el movimiento sigue existiendo

#### Scenario: Id que no existe
- **WHEN** el id tiene formato UUID válido pero no corresponde a ningún movimiento
- **THEN** aprobar y rechazar responden 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Aprobar un movimiento ya confirmado
- **WHEN** el usuario aprueba un movimiento propio confirmado
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `status`, y su saldo no cambia

#### Scenario: Rechazar un movimiento ya confirmado
- **WHEN** el usuario rechaza un movimiento propio confirmado
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `status`, y el movimiento sigue existiendo

#### Scenario: Id mal formado
- **WHEN** se envía `POST /api/transactions/abc/approve` o `POST /api/transactions/abc/reject`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

### Requirement: Modificar o eliminar un pendiente
`PATCH` y `DELETE` sobre `/api/transactions/{id}` SHALL funcionar igual sobre un pendiente que sobre
un confirmado. La modificación SHALL dejarlo `PENDING` y sin efecto en los saldos.

#### Scenario: Corregir un pendiente antes de aprobarlo
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` con `{"amount": 50000}` sobre un gasto pendiente de 45000
- **THEN** la respuesta es 200 con `amount` 50000 y `"status": "PENDING"`, y el saldo de su cuenta no cambia

#### Scenario: Eliminar un pendiente
- **WHEN** el usuario envía `DELETE /api/transactions/{id}` sobre un pendiente propio
- **THEN** la respuesta es 204 y el saldo de su cuenta no cambia

### Requirement: Pendientes autenticados con JWT
Los tres endpoints de pendientes SHALL exigir un Bearer JWT válido. Sin credencial, o con la
credencial Basic compartida, SHALL responder 401 sin cambiar nada.

#### Scenario: Lista sin credencial
- **WHEN** se envía `GET /api/transactions/pending` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Lista con la credencial Basic compartida
- **WHEN** se envía `GET /api/transactions/pending` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Aprobar sin credencial
- **WHEN** se envía `POST /api/transactions/{id}/approve` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`, y el movimiento sigue pendiente

#### Scenario: Aprobar con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions/{id}/approve` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Rechazar sin credencial
- **WHEN** se envía `POST /api/transactions/{id}/reject` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`, y el movimiento sigue existiendo

#### Scenario: Rechazar con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions/{id}/reject` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
