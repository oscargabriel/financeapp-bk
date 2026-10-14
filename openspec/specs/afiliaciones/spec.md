# afiliaciones Specification

## Purpose
Solicitudes de afiliación entre usuarios por correo (FIN-65, epic FIN-9): enviar, aceptar, rechazar,
cancelar y desvincular, y el listado de las relaciones abiertas, bajo `/api/affiliations`. Compartir
gastos con una persona exige una afiliación aceptada. El API no revela qué correos tienen cuenta.

## Requirements

### Requirement: Enviar una solicitud de afiliación
`POST /api/affiliations` SHALL recibir `email` y crear una solicitud del usuario autenticado a ese
correo, con estado `REQUESTED`. El correo SHALL normalizarse como en el registro: sin espacios
alrededor y en minúsculas. SHALL responder 201 con la relación: `id`, `status`, `direction` `SENT`,
`person` con el correo normalizado, y `createdAt` y `updatedAt`.

#### Scenario: Solicitud a un usuario registrado
- **WHEN** A envía `{"email": "  B@Correo.com "}` y `b@correo.com` es un usuario activo
- **THEN** la respuesta es 201 con `"status": "REQUESTED"`, `"direction": "SENT"` y `person.email` `b@correo.com`, y B la ve en su listado con `"direction": "RECEIVED"`

#### Scenario: Correo con formato inválido
- **WHEN** A envía `{"email": "no-es-correo"}`
- **THEN** la respuesta es 400 con code `VALIDATION_ERROR` y field `email`, y no se crea nada

#### Scenario: Correo vacío o ausente
- **WHEN** A envía `{}` o `{"email": ""}`
- **THEN** la respuesta es 400 con code `VALIDATION_ERROR` y field `email`

#### Scenario: Cuerpo que no es JSON
- **WHEN** A envía un cuerpo que no es JSON válido
- **THEN** la respuesta es 400 con code `JSON_PARSING_ERROR`

### Requirement: La solicitud no revela qué correos tienen cuenta
Una solicitud a un correo sin usuario activo SHALL responder igual que a uno registrado: 201 con la
misma forma y `firstName` y `lastName` en `null`. Nadie SHALL recibirla, aunque la persona se registre
después, y el remitente SHALL poder cancelarla. En una solicitud enviada que sigue `REQUESTED`,
`person` SHALL traer solo el correo escrito, con `firstName` y `lastName` en `null`, tenga o no cuenta.

#### Scenario: Correo sin cuenta
- **WHEN** A envía una solicitud a un correo que no es de ningún usuario
- **THEN** la respuesta es 201 con `"status": "REQUESTED"`, `"direction": "SENT"`, `person.email` el correo y `person.firstName` y `person.lastName` en `null`, y A la ve en su listado

#### Scenario: Correo con cuenta, visto por el remitente
- **WHEN** A envía una solicitud a un usuario registrado y lista sus afiliaciones antes de que la acepte
- **THEN** la solicitud sale con `person.firstName` y `person.lastName` en `null`, igual que la de un correo sin cuenta

#### Scenario: Usuario desactivado
- **WHEN** A envía una solicitud al correo de un usuario desactivado o borrado
- **THEN** la respuesta es la de un correo sin cuenta, y ese usuario no la recibe

### Requirement: Solicitudes que no se pueden enviar
Una solicitud al correo del propio usuario SHALL responder 400 `VALIDATION_ERROR` en `email`. Una
solicitud a alguien con quien el usuario ya tiene una relación abierta que ve —una solicitud enviada
a ese correo que sigue `REQUESTED`, una recibida de esa persona que sigue `REQUESTED`, o una
afiliación `ACCEPTED`, en cualquier sentido— SHALL responder 409 `DUPLICATE_RESOURCE` en `email`. En
los dos casos no SHALL crearse nada.

#### Scenario: A sí mismo
- **WHEN** A envía una solicitud a su propio correo, en mayúsculas
- **THEN** la respuesta es 400 con code `VALIDATION_ERROR` y field `email`, y no se crea nada

#### Scenario: Solicitud abierta en el mismo sentido
- **WHEN** A ya envió a B una solicitud que sigue `REQUESTED` y vuelve a enviarla
- **THEN** la respuesta es 409 con code `DUPLICATE_RESOURCE` y field `email`

#### Scenario: Solicitud abierta en el sentido contrario
- **WHEN** B envió a A una solicitud que sigue `REQUESTED` y A envía una a B
- **THEN** la respuesta es 409 con code `DUPLICATE_RESOURCE` y field `email`

#### Scenario: Ya afiliados
- **WHEN** A y B tienen una afiliación `ACCEPTED` y cualquiera de los dos envía una solicitud al otro
- **THEN** la respuesta es 409 con code `DUPLICATE_RESOURCE` y field `email`

#### Scenario: Repetida a un correo sin cuenta
- **WHEN** A ya envió una solicitud que sigue `REQUESTED` a un correo sin cuenta y vuelve a enviarla
- **THEN** la respuesta es 409 con code `DUPLICATE_RESOURCE` y field `email`

### Requirement: Listar las relaciones del usuario
`GET /api/affiliations` SHALL responder 200 con las relaciones abiertas (`REQUESTED` o `ACCEPTED`) en
las que participa el usuario, como remitente o como destinatario, de la más reciente a la más
antigua por `createdAt`. Cada una SHALL traer `id`, `status`, `direction` (`SENT` o `RECEIVED`),
`person` (`email`, `firstName`, `lastName`), `createdAt` y `updatedAt`. Las rechazadas, canceladas y
desvinculadas no SHALL salir. Sin relaciones, `[]`.

#### Scenario: Recibida
- **WHEN** B envió una solicitud a A y A lista sus afiliaciones
- **THEN** sale con `"direction": "RECEIVED"`, `"status": "REQUESTED"` y en `person` el correo, el nombre y el apellido de B

#### Scenario: Aceptada
- **WHEN** A envió una solicitud a B, B la aceptó y A lista sus afiliaciones
- **THEN** sale con `"direction": "SENT"`, `"status": "ACCEPTED"` y en `person` el correo, el nombre y el apellido de B

#### Scenario: Cerradas fuera del listado
- **WHEN** las solicitudes de A fueron rechazadas, canceladas o desvinculadas
- **THEN** ninguna de ellas sale en el listado de A ni en el de la otra persona

#### Scenario: Relaciones de otros
- **WHEN** B y C tienen una afiliación y A lista las suyas
- **THEN** esa afiliación no sale en el listado de A

### Requirement: Aceptar o rechazar una solicitud recibida
`POST /api/affiliations/{id}/accept` SHALL pasar a `ACCEPTED` una solicitud `REQUESTED` cuyo
destinatario es el usuario, y responder 200 con la relación. `POST /api/affiliations/{id}/reject`
SHALL pasarla a `REJECTED` y responder 204. Ninguna de las dos lleva cuerpo.

#### Scenario: Aceptar
- **WHEN** B acepta la solicitud que le envió A
- **THEN** la respuesta es 200 con `"status": "ACCEPTED"`, `"direction": "RECEIVED"` y los datos de A en `person`, y los dos la ven aceptada en su listado

#### Scenario: Rechazar
- **WHEN** B rechaza la solicitud que le envió A
- **THEN** la respuesta es 204 y la solicitud deja de salir en el listado de los dos

#### Scenario: Aceptar la propia
- **WHEN** A intenta aceptar o rechazar la solicitud que envió
- **THEN** la respuesta es 409 con code `INVALID_STATE` y field `status`, y la solicitud sigue `REQUESTED`

#### Scenario: Aceptar una ya cerrada o aceptada
- **WHEN** B intenta aceptar o rechazar una solicitud que ya está `ACCEPTED`, `REJECTED` o `CANCELLED`
- **THEN** la respuesta es 409 con code `INVALID_STATE` y field `status`, y el estado no cambia

### Requirement: Cancelar una solicitud enviada
`POST /api/affiliations/{id}/cancel` SHALL pasar a `CANCELLED` una solicitud `REQUESTED` cuyo
remitente es el usuario, tenga o no destinatario, y responder 204.

#### Scenario: Cancelar
- **WHEN** A cancela la solicitud que envió a B
- **THEN** la respuesta es 204 y la solicitud deja de salir en el listado de los dos

#### Scenario: Cancelar una a un correo sin cuenta
- **WHEN** A cancela la solicitud que envió a un correo sin cuenta
- **THEN** la respuesta es 204 y deja de salir en su listado

#### Scenario: Cancelar una recibida o ya aceptada
- **WHEN** B intenta cancelar la solicitud que recibió de A, o A intenta cancelar una ya `ACCEPTED`
- **THEN** la respuesta es 409 con code `INVALID_STATE` y field `status`, y el estado no cambia

### Requirement: Desvincular una afiliación
`POST /api/affiliations/{id}/unlink` SHALL pasar a `UNLINKED` una afiliación `ACCEPTED` en la que
participa el usuario, sea remitente o destinatario, y responder 204. Desvincular no SHALL cambiar
ningún movimiento ni ninguna parte compartida entre las dos personas.

#### Scenario: Desvincula el remitente
- **WHEN** A desvincula la afiliación aceptada con B
- **THEN** la respuesta es 204 y deja de salir en el listado de los dos

#### Scenario: Desvincula el destinatario
- **WHEN** B desvincula la afiliación aceptada con A
- **THEN** la respuesta es 204 y deja de salir en el listado de los dos

#### Scenario: Desvincular una solicitud sin aceptar
- **WHEN** A intenta desvincular una solicitud que sigue `REQUESTED`
- **THEN** la respuesta es 409 con code `INVALID_STATE` y field `status`

### Requirement: Una solicitud nueva tras cerrar la anterior
Después de un rechazo, una cancelación o una desvinculación, cualquiera de las dos personas SHALL
poder enviar una solicitud nueva a la otra, en cualquier sentido. La nueva SHALL ser otra relación,
con otro `id`, y la anterior SHALL conservar su estado final.

#### Scenario: Tras un rechazo
- **WHEN** B rechazó la solicitud de A y A vuelve a enviarle una
- **THEN** la respuesta es 201 con un `id` distinto del de la rechazada

#### Scenario: Tras desvincular, en el sentido contrario
- **WHEN** A y B se desvincularon y B le envía una solicitud a A
- **THEN** la respuesta es 201, y A la ve como recibida

### Requirement: Relaciones ajenas
Una relación inexistente o en la que el usuario no participa —ni como remitente ni como
destinatario— SHALL responder 404 `NOT_FOUND` en `id` en las cuatro acciones, sin cambiar nada. Una
solicitud a un correo sin cuenta solo la tiene su remitente. Un `id` que no es UUID SHALL responder
400 `VALIDATION_ERROR` en `id`.

#### Scenario: Relación de otros
- **WHEN** C intenta aceptar, rechazar, cancelar o desvincular una relación entre A y B
- **THEN** la respuesta es 404 con code `NOT_FOUND` y field `id`, y el estado no cambia

#### Scenario: Inexistente
- **WHEN** A pide una acción sobre un UUID que no es de ninguna relación
- **THEN** la respuesta es 404 con code `NOT_FOUND` y field `id`

#### Scenario: Id mal formado
- **WHEN** A pide `POST /api/affiliations/abc/accept`
- **THEN** la respuesta es 400 con code `VALIDATION_ERROR` y field `id`

### Requirement: Autenticación de las rutas de afiliaciones
Las seis rutas SHALL exigir un Bearer válido. Sin credencial, o con el Basic compartido, SHALL
responder 401 `UNAUTHENTICATED` en `authorization`, con `WWW-Authenticate: Bearer`.

#### Scenario: Sin credencial
- **WHEN** se pide cualquiera de las seis rutas sin `Authorization`
- **THEN** la respuesta es 401 con code `UNAUTHENTICATED`, field `authorization` y `WWW-Authenticate` `Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se pide cualquiera de las seis rutas con el Basic de `/auth/*`
- **THEN** la respuesta es 401 con code `UNAUTHENTICATED`, field `authorization` y `WWW-Authenticate` `Bearer`
