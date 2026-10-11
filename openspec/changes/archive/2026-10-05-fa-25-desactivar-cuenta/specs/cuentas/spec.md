# Spec Delta

## ADDED Requirements

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
