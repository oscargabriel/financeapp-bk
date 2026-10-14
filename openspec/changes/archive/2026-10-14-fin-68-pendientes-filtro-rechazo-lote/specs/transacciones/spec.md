## ADDED Requirements

### Requirement: Pendientes de una persona
`GET /api/transactions/pending?sharedBy=<correo>` SHALL devolver 200 solo con los pendientes del
usuario que compartió la persona de ese correo, en el mismo orden que la lista completa. El correo
SHALL compararse con el actual de esa persona, sin espacios alrededor y sin distinguir mayúsculas.
Sin el parámetro, la lista SHALL ser la de todos los pendientes.

#### Scenario: Filtrar por una persona
- **WHEN** B y C compartieron gastos con A, A tiene además un pendiente propio, y A pide `GET /api/transactions/pending?sharedBy=<correo de B>`
- **THEN** la respuesta es 200 con exactamente los pendientes con `sharedBy` B, ordenados por `occurredAt` descendente

#### Scenario: Correo con mayúsculas y espacios
- **WHEN** A pide la lista con `sharedBy` igual al correo de B en mayúsculas y con espacios alrededor
- **THEN** la respuesta es la misma que con el correo exacto

#### Scenario: Persona desvinculada
- **WHEN** A y C se desvinculan con un pendiente de C todavía abierto, y A filtra por el correo de C
- **THEN** la respuesta es 200 con ese pendiente

#### Scenario: Correo sin pendientes
- **WHEN** A filtra por un correo sin cuenta, o por el de una persona que no compartió nada con A
- **THEN** la respuesta es 200 con una lista vacía, la misma en los dos casos

#### Scenario: Sin filtro
- **WHEN** A pide `GET /api/transactions/pending` sin parámetros
- **THEN** la respuesta incluye los pendientes compartidos por B, los de C y los propios

### Requirement: Formato del filtro por persona
Un `sharedBy` vacío, de más de 255 caracteres o sin formato de correo SHALL responder 400
`VALIDATION_ERROR` en `sharedBy`.

#### Scenario: Correo vacío
- **WHEN** se pide `GET /api/transactions/pending?sharedBy=`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `sharedBy`

#### Scenario: Sin formato de correo
- **WHEN** se pide `GET /api/transactions/pending?sharedBy=laura`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `sharedBy`

### Requirement: Rechazo de pendientes en lote
`POST /api/transactions/pending/reject` con `{"ids": [...]}` SHALL rechazar todos esos pendientes del
usuario en una sola petición y responder 204 sin cuerpo. Cada uno SHALL tener el mismo efecto que su
rechazo individual. Los ids SHALL poder ser de grupos distintos.

#### Scenario: Rechazar pendientes de grupos distintos
- **WHEN** A rechaza en lote un pendiente propio y dos compartidos por B y C, cada uno de un grupo distinto
- **THEN** la respuesta es 204, ninguno sale en `GET /api/transactions/pending` y el saldo de las cuentas de A no cambia

#### Scenario: Lo que no se seleccionó
- **WHEN** A rechaza en lote dos de sus tres pendientes
- **THEN** el tercero sigue saliendo en la lista de pendientes

### Requirement: El rechazo en lote es todo o nada
Si algún id del lote no es un pendiente del usuario, el lote SHALL NOT rechazar ninguno. Un id que no
existe, ya se rechazó o es de otro usuario SHALL dar 404 `NOT_FOUND` en `ids[i]`, la misma respuesta
para los tres. Un movimiento propio confirmado SHALL dar 409 `INVALID_STATE` en `ids[i]`. Con errores
de las dos clases, SHALL responder 404 solo con los no encontrados.

#### Scenario: Un id de otro usuario
- **WHEN** A envía en el lote un pendiente propio y, en la posición 1, el id de un pendiente de B
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en `ids[1]`, el pendiente de A sigue pendiente y el de B también

#### Scenario: Un id que no existe
- **WHEN** el lote trae en la posición 0 un UUID que no corresponde a ningún movimiento
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en `ids[0]`, igual que con el id de otro usuario

#### Scenario: Un movimiento confirmado
- **WHEN** el lote trae en la posición 1 un movimiento propio confirmado
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `ids[1]`, y ningún pendiente del lote se rechaza

#### Scenario: Rechazar el lote dos veces
- **WHEN** A repite el mismo lote después de un 204
- **THEN** la respuesta es 404 con un error `NOT_FOUND` por cada posición

#### Scenario: No encontrados y confirmados juntos
- **WHEN** el lote trae un id ajeno en la posición 0 y un confirmado propio en la posición 1
- **THEN** la respuesta es 404 con un solo error, `NOT_FOUND` en `ids[0]`

### Requirement: Formato del cuerpo del rechazo en lote
`ids` SHALL ser obligatorio, con entre 1 y 100 elementos, sin repetidos. Si no, SHALL responder 400
`VALIDATION_ERROR` en `ids`. Un elemento `null` o que no es UUID SHALL responder 400
`VALIDATION_ERROR` en `ids[i]`. Un cuerpo que no es JSON SHALL responder 400 `JSON_PARSING_ERROR` en
`body`. Ninguno SHALL rechazar nada.

#### Scenario: Sin ids
- **WHEN** el cuerpo es `{}` o `{"ids": []}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `ids`

#### Scenario: Más de 100
- **WHEN** el cuerpo trae 101 ids
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `ids`

#### Scenario: Id repetido
- **WHEN** el cuerpo trae dos veces el mismo id de un pendiente propio
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `ids`, y el pendiente sigue pendiente

#### Scenario: Id mal formado
- **WHEN** el cuerpo es `{"ids": ["<pendiente propio>", "abc"]}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `ids[1]`, y el pendiente sigue pendiente

#### Scenario: Elemento nulo
- **WHEN** el cuerpo es `{"ids": [null]}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `ids[0]`

#### Scenario: Cuerpo que no es JSON
- **WHEN** el cuerpo es `{ids:`
- **THEN** la respuesta es 400 con un error `JSON_PARSING_ERROR` en el campo `body`

### Requirement: Rechazo en lote autenticado con JWT
El rechazo en lote SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial Basic
compartida, SHALL responder 401 sin rechazar nada.

#### Scenario: Rechazo en lote sin credencial
- **WHEN** se envía `POST /api/transactions/pending/reject` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Rechazo en lote con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions/pending/reject` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
