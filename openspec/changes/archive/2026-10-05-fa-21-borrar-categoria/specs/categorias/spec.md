# Spec Delta

## ADDED Requirements

### Requirement: Borrado lógico de una categoría
`DELETE /api/categories/{id}` SHALL borrar lógicamente la categoría del usuario del token y
responder 204 sin cuerpo. La categoría SHALL dejar de aparecer en `GET /api/categories`. Se SHALL
poder borrar una categoría con movimientos y una copiada de la semilla.

#### Scenario: Categoría con movimientos
- **WHEN** el usuario borra su categoría `Huerta`, que tiene un gasto
- **THEN** la respuesta es 204 sin cuerpo, y `Huerta` ya no aparece en `GET /api/categories`

#### Scenario: Categoría de la semilla
- **WHEN** el usuario borra su `Mercado`, copiada de la semilla
- **THEN** la respuesta es 204, y `Mercado` ya no aparece en `GET /api/categories`

### Requirement: Categoría a borrar
El `{id}` SHALL ser un UUID; si no lo es, la respuesta SHALL ser 400 con un error
`VALIDATION_ERROR` en el campo `id`. Una categoría que no existe, que ya está borrada o que es de
otro usuario SHALL responder 404 con un error `NOT_FOUND` en el campo `id`, sin distinguir entre
los tres casos y sin borrar nada.

#### Scenario: Id mal formado
- **WHEN** el usuario envía `DELETE /api/categories/abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

#### Scenario: Categoría inexistente
- **WHEN** el `{id}` es un UUID que no corresponde a ninguna categoría
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Categoría ya borrada
- **WHEN** el usuario borra otra vez su categoría `Huerta`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Categoría de otro usuario
- **WHEN** un usuario recién registrado borra una categoría del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la categoría ajena sigue en el listado de su dueño

### Requirement: Movimientos de una categoría borrada
Los movimientos de una categoría borrada SHALL conservar su categoría, y un PATCH que no cambie su
categoría ni su tipo SHALL aceptarse. Una categoría borrada MUST NOT asignarse a un movimiento: el
alta o el PATCH que la elijan SHALL responder 400 con un error `VALIDATION_ERROR` en el campo de la
categoría, como con una categoría que no existe.

#### Scenario: Modificar el monto de un gasto de la categoría borrada
- **WHEN** después de borrar `Huerta` el usuario cambia solo el `amount` de su gasto
- **THEN** la respuesta es 200 y el gasto conserva el `categoryId` de `Huerta`

#### Scenario: Alta con la categoría borrada
- **WHEN** el usuario envía un gasto con el `categoryId` de `Huerta` ya borrada
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].categoryId`, y no se crea nada

### Requirement: Borrado autenticado con JWT
`DELETE /api/categories/{id}` SHALL exigir un Bearer válido. Sin credencial o con la credencial
Basic compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`, sin borrar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `DELETE /api/categories/{id}` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `DELETE /api/categories/{id}` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

## MODIFIED Requirements

### Requirement: Nombre único entre las categorías vivas
El nombre SHALL ser único entre las categorías no borradas del usuario, sin distinguir mayúsculas
y después de recortar los espacios del borde. Un nombre repetido SHALL responder 409 con un error
`DUPLICATE_RESOURCE` en el campo `name`. Un nombre que solo usan categorías borradas del usuario,
o categorías de otro usuario, SHALL poder crearse.

#### Scenario: Repite una categoría propia
- **WHEN** el usuario ya tiene `Plantas` y envía `{"name": " PLANTAS ", "appliesTo": "BOTH"}`
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`, y no se crea nada

#### Scenario: Repite una categoría de la semilla
- **WHEN** el usuario envía `{"name": "mercado", "appliesTo": "EXPENSE"}` y tiene viva la `Mercado` de la semilla
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`

#### Scenario: El nombre lo usa una categoría borrada
- **WHEN** el usuario borra su categoría `Huerta` y después crea otra con `{"name": "huerta", "appliesTo": "EXPENSE", "icon": "leaf", "color": "#558B2F"}`
- **THEN** la respuesta es 201 con un `id` distinto del de la borrada, y la categoría nueva aparece en `GET /api/categories`

#### Scenario: El nombre lo usa otro usuario
- **WHEN** un usuario recién registrado crea `Gimnasio`, que es una categoría propia del usuario del escenario de pruebas
- **THEN** la respuesta es 201
