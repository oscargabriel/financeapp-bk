# transacciones Specification

## Purpose
Movimientos (gastos, ingresos y transferencias) del usuario autenticado: el alta en lote con
`POST /api/transactions`, y la modificación parcial y la eliminación de uno con `PATCH` y `DELETE`
sobre `/api/transactions/{id}`. También el estado de aprobación (FA-76): los pendientes, que no
tienen efecto hasta aprobarse, se listan, aprueban y rechazan bajo `/api/transactions`.

## Requirements

### Requirement: Fecha del movimiento por defecto
Cuando un elemento del lote llega sin `occurredAt` (ausente, `null`, vacío o solo espacios), el
sistema SHALL guardarlo con el instante en que atiende la petición. Todos los elementos sin fecha de
un mismo lote SHALL recibir el mismo instante. La respuesta SHALL devolver ese instante en UTC.

#### Scenario: Elemento sin occurredAt
- **WHEN** el usuario envía `POST /api/transactions` con un elemento válido que no trae `occurredAt`
- **THEN** la respuesta es 201 y el movimiento devuelve un `occurredAt` en UTC igual al instante en que se atendió la petición

#### Scenario: occurredAt nulo, vacío o en blanco
- **WHEN** un elemento trae `"occurredAt": null`, `"occurredAt": ""` o `"occurredAt": "   "`
- **THEN** se trata igual que un elemento sin `occurredAt`: 201 y el instante de la petición

#### Scenario: Varios elementos sin fecha en el mismo lote
- **WHEN** un lote trae tres elementos sin `occurredAt`
- **THEN** los tres movimientos devuelven exactamente el mismo `occurredAt`

#### Scenario: Lote mixto
- **WHEN** un lote trae un elemento con `"occurredAt": "2026-09-20T10:15:00-05:00"` y otro sin fecha
- **THEN** el primero devuelve `2026-09-20T15:15:00Z` y el segundo el instante de la petición

### Requirement: Fecha explícita con offset
Cuando un elemento trae `occurredAt` con contenido, el sistema SHALL exigir una fecha ISO-8601 con
offset y SHALL guardar ese instante, devolviéndolo en UTC.

#### Scenario: Fecha con offset
- **WHEN** un elemento trae `"occurredAt": "2026-09-20T10:15:00-05:00"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"occurredAt": "2026-09-20T15:15:00Z"`

#### Scenario: Fecha sin offset
- **WHEN** el elemento de índice 4 trae `"occurredAt": "2026-09-21T10:00:00"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[4].occurredAt` y no se guarda ningún elemento del lote

#### Scenario: Fecha mal formada
- **WHEN** el elemento de índice 0 trae `"occurredAt": "ayer"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].occurredAt` y no se guarda ningún elemento del lote

### Requirement: Alta autenticada con JWT
`POST /api/transactions` SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial
Basic compartida de `/auth/*` y `/status`, SHALL responder 401 sin guardar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `POST /api/transactions` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Modificación parcial de un movimiento
`PATCH /api/transactions/{id}` SHALL aceptar cualquier subconjunto de `type`, `accountId`,
`destinationAccountId`, `categoryId`, `amount`, `description` y `occurredAt`. Un campo ausente o en
`null` SHALL conservar su valor. La respuesta SHALL ser 200 con el movimiento completo tal como
quedó, con `occurredAt` en UTC.

#### Scenario: Cambiar monto y descripción
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` sobre un gasto propio con `{"amount": 45000, "description": "Mercado del mes"}`
- **THEN** la respuesta es 200 con `amount` 45000 y la descripción nueva, y `type`, `accountId`, `categoryId` y `occurredAt` iguales a los que tenía

#### Scenario: Cambiar la fecha
- **WHEN** el parche trae `{"occurredAt": "2026-09-20T10:15:00-05:00"}`
- **THEN** la respuesta es 200 con `"occurredAt": "2026-09-20T15:15:00Z"`

#### Scenario: Campo en null
- **WHEN** el parche trae `{"description": "Taxi", "categoryId": null}` sobre un gasto con categoría
- **THEN** la respuesta es 200, la descripción cambia y la categoría sigue siendo la misma

#### Scenario: Las notas no cambian
- **WHEN** se modifica un movimiento que tiene `notes`
- **THEN** la respuesta conserva el mismo `notes`

### Requirement: Formato de los campos del parche
Cada campo enviado SHALL cumplir las mismas reglas de formato que en el alta. Un parche sin ningún
campo modificable SHALL responder 400. Ningún error de formato SHALL modificar el movimiento.

#### Scenario: Parche vacío
- **WHEN** el cuerpo es `{}` o solo trae campos en `null`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

#### Scenario: Monto no positivo
- **WHEN** el parche trae `{"amount": 0}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `amount`

#### Scenario: Monto con demasiados decimales
- **WHEN** el parche trae `{"amount": 10.12345}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `amount`

#### Scenario: Tipo fuera del enum
- **WHEN** el parche trae `{"type": "PAGO"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `type`

#### Scenario: Descripción en blanco o demasiado larga
- **WHEN** el parche trae `"description": "   "` o una descripción de 256 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `description`

#### Scenario: Fecha sin offset o mal formada
- **WHEN** el parche trae `"occurredAt": "2026-09-21T10:00:00"` o `"occurredAt": "ayer"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `occurredAt`

#### Scenario: Tipo o fecha en blanco
- **WHEN** el parche trae `"type": "  "` o `"occurredAt": " "`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en ese campo: a diferencia del alta, una fecha en blanco no significa "ahora"

#### Scenario: Varios errores a la vez
- **WHEN** el parche trae `{"amount": -1, "occurredAt": "ayer"}`
- **THEN** la respuesta es 400 con un error en `amount` y otro en `occurredAt`

### Requirement: Reglas evaluadas sobre el movimiento resultante
Las reglas que dependen del tipo SHALL evaluarse sobre el movimiento que resultaría del parche: una
transferencia lleva cuenta destino distinta del origen y no lleva categoría; un gasto o un ingreso
lleva una categoría compatible con su tipo y no lleva cuenta destino. Al cambiar el tipo, el campo
que deja de corresponder SHALL quedar vacío sin que el cliente lo envíe.

#### Scenario: Gasto a transferencia
- **WHEN** sobre un gasto el parche trae `{"type": "TRANSFER", "destinationAccountId": "<otra cuenta propia>"}`
- **THEN** la respuesta es 200 con `type` `TRANSFER`, la cuenta destino enviada y `categoryId` en null

#### Scenario: Gasto a transferencia sin cuenta destino
- **WHEN** sobre un gasto el parche trae `{"type": "TRANSFER"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAccountId`

#### Scenario: Transferencia a gasto
- **WHEN** sobre una transferencia el parche trae `{"type": "EXPENSE", "categoryId": "<categoría de gasto propia>"}`
- **THEN** la respuesta es 200 con `type` `EXPENSE`, la categoría enviada y `destinationAccountId` en null

#### Scenario: Transferencia a gasto sin categoría
- **WHEN** sobre una transferencia el parche trae `{"type": "EXPENSE"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Gasto a ingreso con categoría de gasto
- **WHEN** sobre un gasto con categoría de alcance `EXPENSE` el parche trae solo `{"type": "INCOME"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Categoría en una transferencia
- **WHEN** sobre una transferencia el parche trae `{"categoryId": "<categoría propia>"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Cuenta destino en un gasto
- **WHEN** sobre un gasto el parche trae `{"destinationAccountId": "<cuenta propia>"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAccountId`

#### Scenario: Origen igual al destino
- **WHEN** sobre una transferencia el parche trae como `accountId` la misma cuenta que ya es su destino
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAccountId`

### Requirement: Referencias del usuario en la modificación
Toda cuenta o categoría que el parche envíe SHALL pertenecer al usuario y cumplir las mismas
condiciones que en el alta (cuenta activa, categoría vigente y compatible), además de la regla de
moneda de la modificación. Una referencia ajena, inexistente o mal formada SHALL dar el mismo error,
sin revelar si existe para otro usuario. Las referencias que el parche no envía y que siguen
correspondiendo al tipo resultante SHALL conservarse sin volver a validarse, salvo la categoría
cuando cambia el tipo.

#### Scenario: Cuenta de otro usuario
- **WHEN** el parche trae como `accountId` una cuenta de otro usuario
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId` y el movimiento no cambia

#### Scenario: Categoría inexistente
- **WHEN** el parche trae un `categoryId` que no existe o está mal formado
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Cuenta desactivada después del alta
- **WHEN** la cuenta de un movimiento se desactivó y el parche solo trae `description`
- **THEN** la respuesta es 200 y el movimiento conserva su cuenta

### Requirement: Saldos tras modificar
Los saldos de las cuentas SHALL reflejar el movimiento modificado como si se hubiera registrado así
desde el principio, también cuando cambian la cuenta, el tipo o el monto.

#### Scenario: Cambio de monto
- **WHEN** un gasto de 30000 sobre una cuenta pasa a 45000
- **THEN** el `currentBalance` de esa cuenta en `GET /api/accounts` baja 15000 más

#### Scenario: Cambio de cuenta
- **WHEN** un gasto de 45000 pasa de la cuenta A a la cuenta B
- **THEN** el saldo de A sube 45000 y el de B baja 45000

### Requirement: Eliminación de un movimiento
`DELETE /api/transactions/{id}` SHALL borrar el movimiento del usuario y responder 204 sin cuerpo.
Los saldos SHALL quedar como si el movimiento no se hubiera registrado.

#### Scenario: Borrar un gasto
- **WHEN** el usuario envía `DELETE /api/transactions/{id}` sobre un gasto propio de 45000
- **THEN** la respuesta es 204 y el saldo de su cuenta sube 45000

#### Scenario: Borrar dos veces
- **WHEN** el usuario repite el mismo `DELETE` sobre un movimiento ya borrado
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

### Requirement: Movimiento inexistente o ajeno
`PATCH` y `DELETE` sobre un id que no existe o que pertenece a otro usuario SHALL responder 404 con
el mismo error, sin modificar ni borrar nada.

#### Scenario: Modificar un movimiento de otro usuario
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` con el id de un movimiento de otro usuario
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id` y el movimiento no cambia

#### Scenario: Borrar un movimiento de otro usuario
- **WHEN** el usuario envía `DELETE /api/transactions/{id}` con el id de un movimiento de otro usuario
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id` y el movimiento sigue existiendo

#### Scenario: Id que no existe
- **WHEN** el id tiene formato UUID válido pero no corresponde a ningún movimiento
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

### Requirement: Id del movimiento mal formado
Un `{id}` que no es un UUID SHALL responder 400 sobre el parámetro de ruta, en los dos endpoints.

#### Scenario: Id mal formado en PATCH
- **WHEN** se envía `PATCH /api/transactions/abc` con un parche válido
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

#### Scenario: Id mal formado en DELETE
- **WHEN** se envía `DELETE /api/transactions/abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

### Requirement: Modificación y eliminación autenticadas con JWT
`PATCH` y `DELETE` sobre `/api/transactions/{id}` SHALL exigir un Bearer JWT válido. Sin credencial,
o con la credencial Basic compartida, SHALL responder 401 sin modificar nada.

#### Scenario: PATCH sin credencial
- **WHEN** se envía `PATCH /api/transactions/{id}` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: PATCH con la credencial Basic compartida
- **WHEN** se envía `PATCH /api/transactions/{id}` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: DELETE sin credencial
- **WHEN** se envía `DELETE /api/transactions/{id}` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: DELETE con la credencial Basic compartida
- **WHEN** se envía `DELETE /api/transactions/{id}` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: PATCH permitido por CORS
La política CORS del API SHALL incluir `PATCH` entre los métodos permitidos, para que un cliente
de navegador pueda modificar movimientos.

#### Scenario: Preflight de un PATCH
- **WHEN** un navegador envía `OPTIONS /api/transactions/{id}` con `Access-Control-Request-Method: PATCH` desde un origen permitido
- **THEN** la respuesta autoriza el método `PATCH` en `Access-Control-Allow-Methods`

### Requirement: Estado de aprobación de un movimiento
Todo movimiento SHALL tener un estado, `PENDING` o `CONFIRMED`. Un movimiento SHALL quedar `PENDING`
cuando necesita que el usuario lo confirme, sea cual sea su origen: lo que registra el asistente, lo
que el alta convierte de otra moneda y lo que otra persona comparte con el usuario (ver
`compartidos`). Lo demás que entra por `POST /api/transactions` SHALL quedar `CONFIRMED`. La
respuesta de un movimiento SHALL incluir el campo `status` en el alta, la modificación, la
aprobación y la lista de pendientes.

#### Scenario: El alta entra confirmada
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido en la moneda de sus cuentas
- **THEN** la respuesta es 201 y cada movimiento devuelve `"status": "CONFIRMED"`

#### Scenario: El cliente no puede elegir el estado
- **WHEN** un elemento del lote trae `"status": "PENDING"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"status": "CONFIRMED"`

#### Scenario: Lo compartido entra pendiente para la otra persona
- **WHEN** A registra un gasto confirmado con una parte para B
- **THEN** el gasto de A devuelve `"status": "CONFIRMED"` y el de B sale en su lista de pendientes con `"status": "PENDING"`

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
contar en los reportes. Un pendiente que ya tiene cuenta SHALL aprobarse sin cuerpo; uno sin cuenta
SHALL aprobarse con `{accountId, categoryId}` (ver `compartidos`).

#### Scenario: Aprobar un gasto
- **WHEN** el usuario aprueba un gasto pendiente de 45000
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"`, el `currentBalance` de su cuenta baja 45000 y el gasto deja de salir en `GET /api/transactions/pending`

#### Scenario: Aprobar una transferencia
- **WHEN** el usuario aprueba una transferencia pendiente de 100000 de la cuenta A a la cuenta B
- **THEN** la respuesta es 200, el saldo de A baja 100000 y el de B sube 100000

#### Scenario: Aprobar con cuenta y categoría un pendiente sin cuenta
- **WHEN** el usuario aprueba un pendiente compartido con `{"accountId": ..., "categoryId": ...}` propios
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y esa cuenta y esa categoría

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
un confirmado. La modificación SHALL dejarlo `PENDING` y sin efecto en los saldos. La excepción es un
pendiente que otra persona compartió con el usuario: solo se aprueba o se rechaza (ver
`compartidos`).

#### Scenario: Corregir un pendiente antes de aprobarlo
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` con `{"amount": 50000}` sobre un gasto pendiente de 45000
- **THEN** la respuesta es 200 con `amount` 50000 y `"status": "PENDING"`, y el saldo de su cuenta no cambia

#### Scenario: Eliminar un pendiente
- **WHEN** el usuario envía `DELETE /api/transactions/{id}` sobre un pendiente propio
- **THEN** la respuesta es 204 y el saldo de su cuenta no cambia

#### Scenario: Modificar un pendiente compartido
- **WHEN** el usuario envía `PATCH /api/transactions/{id}` sobre un pendiente que otra persona compartió con él
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `status`, y el pendiente no cambia

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

### Requirement: Origen de un movimiento
Todo movimiento SHALL tener un origen: `WEB`, `TELEGRAM` o `IMPORT`. Los que entran por
`POST /api/transactions` SHALL tener origen `WEB`, y los que registra el asistente, `TELEGRAM`. El
origen SHALL NOT decidir el estado: solo dice por dónde entró el movimiento. La respuesta de un
movimiento SHALL incluir el campo `origin` en el alta, la modificación, la aprobación y la lista de
pendientes.

#### Scenario: El alta es WEB
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido
- **THEN** la respuesta es 201 y cada movimiento devuelve `"origin": "WEB"`

#### Scenario: El cliente no puede elegir el origen
- **WHEN** un elemento del lote trae `"origin": "TELEGRAM"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"` y `"status": "CONFIRMED"`

#### Scenario: El origen se conserva al aprobar
- **WHEN** el usuario aprueba un pendiente creado por el asistente
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "TELEGRAM"`

#### Scenario: Un pendiente WEB
- **WHEN** el usuario registra con `POST /api/transactions` un gasto en otra moneda que la de su cuenta
- **THEN** la respuesta es 201 con `"status": "PENDING"` y `"origin": "WEB"`

### Requirement: Aprobar un pendiente con una cuenta desactivada
`POST /api/transactions/{id}/approve` sobre un pendiente propio cuya cuenta origen o destino está
desactivada SHALL responder 409 con un error `INVALID_STATE` por cada cuenta desactivada: en
`accountId` si es el origen, en `destinationAccountId` si es el destino. El pendiente SHALL seguir
pendiente y ningún saldo SHALL cambiar. Rechazarlo SHALL seguir funcionando, y reactivada la cuenta,
SHALL aprobarse como cualquier otro.

#### Scenario: Cuenta origen desactivada
- **WHEN** el usuario aprueba un gasto pendiente cuya cuenta está desactivada
- **THEN** la respuesta es 409 con un único error `INVALID_STATE` en el campo `accountId`, el gasto sigue en `GET /api/transactions/pending` y el `currentBalance` de la cuenta no cambia

#### Scenario: Cuenta destino desactivada
- **WHEN** el usuario aprueba una transferencia pendiente cuya cuenta destino está desactivada y la de origen activa
- **THEN** la respuesta es 409 con un único error `INVALID_STATE` en el campo `destinationAccountId`, y ninguna de las dos cuentas cambia de saldo

#### Scenario: Las dos cuentas desactivadas
- **WHEN** el usuario aprueba una transferencia pendiente con las dos cuentas desactivadas
- **THEN** la respuesta es 409 con dos errores `INVALID_STATE`, uno en `accountId` y otro en `destinationAccountId`

#### Scenario: Rechazar con la cuenta desactivada
- **WHEN** el usuario rechaza un pendiente cuya cuenta está desactivada
- **THEN** la respuesta es 204 y el pendiente deja de salir en `GET /api/transactions/pending`

#### Scenario: Aprobar tras reactivar la cuenta
- **WHEN** el usuario reactiva la cuenta con `PATCH /api/accounts/{id}` y `{"isActive": true}`, y aprueba el pendiente
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y el saldo de la cuenta se mueve

### Requirement: Movimiento programado
Un movimiento `CONFIRMED` cuyo `occurredAt` es posterior al momento actual SHALL estar programado: no
SHALL mover el `currentBalance` de sus cuentas. Cuando el momento actual alcanza su `occurredAt`,
SHALL moverlo sin que nadie lo modifique, aunque el servicio no haya atendido peticiones mientras
tanto. Un pendiente con fecha futura sigue sin mover saldos y, al aprobarse, queda programado hasta
su fecha.

#### Scenario: Gasto con fecha futura
- **WHEN** el usuario registra un gasto de 40000 con `occurredAt` dentro de 30 días sobre una cuenta con saldo 500000
- **THEN** la respuesta es 201 y el `currentBalance` de la cuenta en `GET /api/accounts` sigue en 500000

#### Scenario: Transferencia con fecha futura
- **WHEN** el usuario registra una transferencia con fecha futura entre dos cuentas propias
- **THEN** ninguna de las dos cuentas cambia de saldo

#### Scenario: Llega la fecha
- **WHEN** el usuario registra un gasto de 40000 con `occurredAt` unos segundos en el futuro y consulta sus cuentas después de esa fecha, sin haber tocado el movimiento
- **THEN** el `currentBalance` de la cuenta ya descuenta los 40000

### Requirement: Marca de programado en la respuesta
La respuesta de un movimiento SHALL incluir `scheduled`: `true` si su `occurredAt` es posterior al
momento en que se atiende la petición, `false` si no. SHALL ir en el alta, la modificación, la
aprobación y la lista de pendientes. El cliente no la envía: si llega en el cuerpo, se ignora.

#### Scenario: Alta con fecha futura y pasada
- **WHEN** el usuario envía un lote con un gasto de fecha futura y otro sin `occurredAt`
- **THEN** el primero devuelve `"scheduled": true` y el segundo `"scheduled": false`

#### Scenario: Pendiente con fecha futura
- **WHEN** el usuario tiene un pendiente con fecha futura
- **THEN** `GET /api/transactions/pending` lo devuelve con `"scheduled": true`

### Requirement: Cambiar la fecha entre futura y pasada
Un `PATCH /api/transactions/{id}` que cambia `occurredAt` SHALL dejar los saldos como corresponden a
la fecha nueva: una fecha que pasa de futura a pasada SHALL aplicar el movimiento, y una que pasa de
pasada a futura SHALL retirarlo hasta que llegue. La respuesta SHALL traer `scheduled` según la
fecha nueva.

#### Scenario: De futura a pasada
- **WHEN** un gasto programado de 40000 sobre una cuenta con saldo 500000 recibe `{"occurredAt": <ayer>}`
- **THEN** la respuesta es 200 con `"scheduled": false` y el `currentBalance` de la cuenta queda en 460000

#### Scenario: De pasada a futura
- **WHEN** el mismo gasto recibe después `{"occurredAt": <dentro de 30 días>}`
- **THEN** la respuesta es 200 con `"scheduled": true` y el `currentBalance` vuelve a 500000

### Requirement: Movimientos con fecha pasada
Los movimientos con `occurredAt` anterior o igual al momento actual SHALL afectar los saldos y los
reportes igual que antes de existir los programados.

#### Scenario: El escenario de pruebas no cambia
- **WHEN** el usuario del escenario de pruebas, sin movimientos con fecha futura, pide `GET /api/accounts`
- **THEN** cada cuenta trae el mismo `currentBalance` que tenía antes de este cambio

### Requirement: Serie del movimiento en la respuesta
La respuesta de un movimiento SHALL incluir `recurrenceId`: el id de la serie de la que es ocurrencia,
o `null` si no pertenece a ninguna. SHALL ir en el alta, la modificación, la aprobación y la lista de
pendientes. El cliente no la envía: si llega en el cuerpo del alta o del parche, se ignora, y un
movimiento no se puede agregar a una serie ni sacar de ella.

#### Scenario: Movimiento suelto
- **WHEN** el usuario registra un gasto con `POST /api/transactions`
- **THEN** la respuesta trae `"recurrenceId": null`

#### Scenario: recurrenceId en el cuerpo
- **WHEN** el usuario envía un elemento del lote con un `recurrenceId`
- **THEN** la respuesta es 201 con `"recurrenceId": null`

#### Scenario: Ocurrencia modificada
- **WHEN** el usuario modifica con `PATCH /api/transactions/{id}` la descripción de una ocurrencia
- **THEN** la respuesta es 200 con el `recurrenceId` de su serie

### Requirement: Cuota del movimiento en la respuesta
La respuesta de un movimiento SHALL incluir `installment`: `{purchaseId, number, count}` si es una cuota
de una compra en cuotas —el id de la compra, el número de la cuota y el `installmentCount` de la
compra—, o `null` si no lo es. SHALL ir en el alta, la modificación, la aprobación y la lista de
pendientes. El cliente no lo envía: si llega en el cuerpo del alta o del parche, se ignora, y un
movimiento no se puede agregar a una compra ni sacar de ella.

#### Scenario: Movimiento suelto
- **WHEN** el usuario registra un gasto con `POST /api/transactions`
- **THEN** la respuesta trae `"installment": null`

#### Scenario: installment en el cuerpo
- **WHEN** el usuario envía un elemento del lote con un `installment`
- **THEN** la respuesta es 201 con `"installment": null`

#### Scenario: Cuota modificada
- **WHEN** el usuario modifica con `PATCH /api/transactions/{id}` la descripción de la cuota 2 de una compra a 3 cuotas
- **THEN** la respuesta es 200 con `installment` `{"purchaseId": <la compra>, "number": 2, "count": 3}`

### Requirement: Equivalente en USD de cada movimiento
Todo movimiento SHALL guardar `exchange_rate` y `amount_base`:
- `exchange_rate` es la tasa `USD→moneda del movimiento` de su fecha. La fecha es la de
  `occurredAt` en la zona del usuario, y la tasa se elige con la regla de `tasas-de-cambio`: la más
  reciente ≤ la fecha o, si no hay, la más antigua.
- `amount_base` es `amount / exchange_rate`, en USD, con 4 decimales y un mínimo de 0.0001.

Un movimiento en USD SHALL tener `exchange_rate` 1. El cálculo SHALL rehacerse solo cuando cambian el
monto, la moneda o la fecha. Aprobar un pendiente o modificar otros campos MUST NOT cambiar su tasa.
La regla vale para todo camino que escribe movimientos: alta en lote, asistente, modificación, series
y cuotas.

#### Scenario: Gasto en COP
- **WHEN** la tasa `USD→COP` de la fecha del movimiento es 4100 y se registra un gasto de 41000 COP
- **THEN** el movimiento queda con `exchange_rate` 4100 y `amount_base` 10

#### Scenario: Fecha anterior a todas las tasas
- **WHEN** la tasa `USD→COP` más antigua es la de hace 45 días, en 3900, y se registra un gasto de 39000 COP con fecha de hace 50 días
- **THEN** el movimiento queda con `exchange_rate` 3900 y `amount_base` 10

#### Scenario: Modificar la descripción
- **WHEN** después de registrar un movimiento se guarda una tasa nueva para su fecha y se le cambia solo la descripción
- **THEN** su `exchange_rate` y su `amount_base` no cambian

#### Scenario: Modificar el monto
- **WHEN** se le cambia el monto a un movimiento
- **THEN** `amount_base` se recalcula con la tasa de su fecha

#### Scenario: Moneda sin ninguna tasa
- **WHEN** la moneda del movimiento no tiene ninguna fila en `exchange_rates`
- **THEN** la escritura se rechaza con 502 `EXTERNAL_SERVICE_ERROR` en el campo `server`, sin detalle interno, y no se guarda nada

### Requirement: Tasa del día al registrar
El alta en lote SHALL pedir al proveedor la tasa de hoy, una vez y solo si falta, cuando algún
elemento tiene fecha de hoy o posterior en la zona de la app y su moneda no tiene fila de hoy. Un
fallo del proveedor MUST NOT rechazar el alta: el movimiento usa la última tasa guardada.

#### Scenario: Falta la tasa de hoy
- **WHEN** no hay `USD→COP` de hoy y se registra un gasto con fecha de hoy
- **THEN** se consulta al proveedor una vez antes de guardar, y el movimiento usa la tasa de hoy

#### Scenario: Proveedor caído
- **WHEN** no hay `USD→COP` de hoy, existe la de ayer, el proveedor falla y se registra un gasto de hoy
- **THEN** el alta responde 201 y el movimiento usa la tasa de ayer

#### Scenario: Solo fechas pasadas
- **WHEN** todos los elementos del lote tienen fecha anterior a hoy
- **THEN** no se consulta al proveedor

### Requirement: Movimiento en la moneda de su cuenta
El alta en lote SHALL admitir cuentas activas del usuario en cualquier moneda, y cada movimiento SHALL
registrarse con `currencyCode` igual a la moneda de su cuenta origen. `currencyCode` en el elemento
SHALL ser opcional. Ausente o igual a la moneda de la cuenta, el movimiento SHALL registrarse con el
monto recibido y en el estado que corresponde a su origen. Un `currencyCode` que no tenga el formato
de 3 letras SHALL dar 400 `VALIDATION_ERROR` en `[i].currencyCode`, y uno que no sea una moneda activa
del catálogo SHALL dar lo mismo.

#### Scenario: Gasto en una cuenta en USD
- **WHEN** el usuario registra un gasto de 25 sobre su cuenta en USD, sin `currencyCode`
- **THEN** la respuesta es 201 con `"currencyCode": "USD"`, `"amount": 25`, `"status": "CONFIRMED"` y `originalAmount` y `originalCurrencyCode` en null, y el saldo de la cuenta baja 25

#### Scenario: currencyCode igual al de la cuenta
- **WHEN** el elemento trae `"currencyCode": "usd"` sobre una cuenta en USD
- **THEN** el movimiento se registra como si no lo trajera

#### Scenario: Moneda mal formada
- **WHEN** el elemento `[3]` trae `"currencyCode": "DOLAR"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[3].currencyCode`, junto con los demás errores de formato del lote

#### Scenario: Moneda que no está en el catálogo
- **WHEN** el elemento `[0]` trae `"currencyCode": "XYZ"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].currencyCode` y nada se guarda

### Requirement: Conversión a la moneda de la cuenta
Si `currencyCode` difiere de la moneda de la cuenta origen, el monto SHALL convertirse a la moneda de
la cuenta con la tasa de `GET /api/exchange-rates` para la fecha del movimiento en la zona de la app.
El resultado SHALL redondearse a los decimales de esa moneda en el catálogo, con un mínimo de una
unidad de esa escala. El movimiento SHALL quedar `PENDING` sea cual sea su origen. `originalAmount` y
`originalCurrencyCode` SHALL guardar el monto y la moneda recibidos, y no SHALL modificarse después.
Si el par no tiene ninguna tasa, la respuesta SHALL ser 502 `EXTERNAL_SERVICE_ERROR` en `server`, y
nada SHALL guardarse.

#### Scenario: Gasto en pesos sobre una cuenta en dólares
- **WHEN** la tasa `USD→COP` de hoy es 4100 y el usuario registra un gasto de 41000 con `"currencyCode": "COP"` sobre su cuenta en USD
- **THEN** la respuesta es 201 con `"currencyCode": "USD"`, `"amount": 10`, `"status": "PENDING"`, `"originalAmount": 41000` y `"originalCurrencyCode": "COP"`, y el saldo de la cuenta no cambia hasta aprobarlo

#### Scenario: Convertido que redondea a cero
- **WHEN** la conversión de un monto a una moneda de 2 decimales da menos de 0.005
- **THEN** el movimiento se registra con `"amount": 0.01`

#### Scenario: Sin ninguna tasa del par
- **WHEN** una de las dos monedas no tiene ninguna fila en `exchange_rates` y el proveedor no responde
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server` y nada se guarda

#### Scenario: Ajustar el convertido antes de aprobarlo
- **WHEN** el usuario cambia con PATCH el `amount` de un gasto convertido pendiente a 9.8 y luego lo aprueba
- **THEN** el saldo de la cuenta baja 9.8, y `originalAmount` y `originalCurrencyCode` siguen siendo 41000 y COP

### Requirement: Transferencia entre cuentas de monedas distintas
En una transferencia entre cuentas de monedas distintas, `destinationAmount` SHALL estar en la moneda
del destino y ser lo que entra al destino.

- Con `destinationAmount`, la transferencia SHALL quedar en el estado de su origen, salvo que su monto
  se haya convertido.
- Sin `destinationAmount`, lo que entra al destino SHALL calcularse convirtiendo `amount`, ya en la
  moneda del origen, a la moneda del destino con la misma regla de tasa y redondeo. La transferencia
  SHALL quedar `PENDING`.

`destinationAmount` SHALL tener el formato de `amount`. SHALL dar 400 `VALIDATION_ERROR` en
`[i].destinationAmount` en un gasto o un ingreso, y en una transferencia entre cuentas de la misma
moneda.

#### Scenario: Con el monto de destino
- **WHEN** el usuario transfiere 410000 de su cuenta en COP a su cuenta en USD con `"destinationAmount": 95`
- **THEN** la respuesta es 201 con `"status": "CONFIRMED"` y `"destinationAmount": 95`; el saldo de la cuenta en COP baja 410000 y el de la cuenta en USD sube 95

#### Scenario: Sin el monto de destino
- **WHEN** la tasa `USD→COP` de hoy es 4100 y el usuario transfiere 10 de su cuenta en USD a su cuenta en COP sin `destinationAmount`
- **THEN** la respuesta es 201 con `"status": "PENDING"` y `"destinationAmount": 41000`, y ningún saldo cambia hasta aprobarla

#### Scenario: Monto de destino entre cuentas de la misma moneda
- **WHEN** el elemento `[0]` es una transferencia entre dos cuentas en COP con `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].destinationAmount`

#### Scenario: Monto de destino en un gasto
- **WHEN** el elemento `[0]` es un gasto con `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].destinationAmount`

### Requirement: Campos de moneda en la respuesta de un movimiento
La respuesta del alta, del PATCH, de la aprobación y de la lista de pendientes SHALL incluir
`destinationAmount`, `originalAmount` y `originalCurrencyCode`, en null cuando no aplican.

#### Scenario: Pendiente convertido en la lista
- **WHEN** el usuario tiene un gasto convertido pendiente y pide `GET /api/transactions/pending`
- **THEN** ese movimiento trae `originalAmount` y `originalCurrencyCode` con lo recibido

#### Scenario: Movimiento sin conversión
- **WHEN** el movimiento es un gasto en la moneda de su cuenta
- **THEN** `destinationAmount`, `originalAmount` y `originalCurrencyCode` vienen en null

### Requirement: Moneda de las cuentas en la modificación
`currencyCode` no SHALL ser modificable.

- Un `accountId` en el parche SHALL estar en la moneda del movimiento. Si no, SHALL dar 400
  `VALIDATION_ERROR` en `accountId`.
- Si la transferencia resultante es entre cuentas de la misma moneda, o el tipo resultante no es
  transferencia, `destinationAmount` SHALL quedar en null. Un `destinationAmount` en el parche SHALL
  dar 400 `VALIDATION_ERROR` en `destinationAmount`.
- Si la transferencia resultante es entre monedas distintas, SHALL valer el `destinationAmount` del
  parche. Si el parche no lo trae, SHALL conservarse el guardado, siempre que el destino guardado
  estuviera en la misma moneda que el resultante. Si no hay ninguno aplicable, SHALL dar 400
  `VALIDATION_ERROR` en `destinationAmount`.
- `destinationAmount` en el parche SHALL tener el formato de `amount`.

#### Scenario: Ajustar el destino de una transferencia convertida
- **WHEN** el parche trae `{"destinationAmount": 40000}` sobre la transferencia pendiente de USD a COP
- **THEN** la respuesta es 200 con `"destinationAmount": 40000` y al aprobarla la cuenta en COP sube 40000

#### Scenario: Cuenta en otra moneda
- **WHEN** el parche trae como `accountId` la cuenta en USD sobre un gasto en COP
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId` y el movimiento no cambia

#### Scenario: Monto de destino en un gasto
- **WHEN** el parche trae `destinationAmount` sobre un gasto
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAmount`

#### Scenario: Pasar a transferencia entre monedas sin monto de destino
- **WHEN** el parche convierte un gasto en COP en una transferencia hacia la cuenta en USD, sin `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAmount`

#### Scenario: Transferencia entre monedas que pasa a gasto
- **WHEN** el parche cambia a `EXPENSE` con categoría una transferencia de COP a USD que tenía `destinationAmount`
- **THEN** la respuesta es 200 con `"destinationAmount": null` y los saldos quedan coherentes

### Requirement: Saldo suficiente en el alta en lote
`POST /api/transactions` SHALL rechazar el lote entero, sin guardar ninguno, si su efecto neto sobre
el `currentBalance` de una cuenta no `CREDIT` lo baja y lo deja por debajo de cero. SHALL responder
409 con un error `INSUFFICIENT_FUNDS` por cuenta, en `[i].amount` del primer elemento, en el orden del
lote, que saca dinero de esa cuenta y la deja en negativo. Solo cuentan los elementos que mueven el
saldo vigente: ni los programados ni los que quedan pendientes.

#### Scenario: Un gasto mayor que el saldo
- **WHEN** el usuario envía un lote con un gasto de 150000 sobre la cuenta `DEBIT` `Nómina`, con `currentBalance` 100000
- **THEN** la respuesta es 409 con un único error `INSUFFICIENT_FUNDS` en `[0].amount`, y el saldo de `Nómina` sigue en 100000

#### Scenario: Transferencia desde una cuenta sin saldo
- **WHEN** el usuario envía un lote con una transferencia de 120000 de `Nómina` (saldo 100000) a una tarjeta `CREDIT`
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `[0].amount` que nombra `Nómina`, y ninguna de las dos cuentas cambia

#### Scenario: Un ingreso del mismo lote compensa
- **WHEN** el lote trae, sobre `Nómina` con saldo 100000, un gasto de 150000 y luego un ingreso de 80000, los dos con fecha de hoy
- **THEN** la respuesta es 201 con los dos movimientos y el `currentBalance` de `Nómina` queda en 30000

#### Scenario: El error va en el elemento que cruza el cero
- **WHEN** el lote trae, sobre `Nómina` con saldo 100000, un gasto de 60000, uno de 70000 y uno de 10000
- **THEN** la respuesta es 409 con un único error `INSUFFICIENT_FUNDS` en `[1].amount` y no se guarda ninguno

#### Scenario: Dos cuentas sin saldo
- **WHEN** el lote deja en negativo a `Nómina` en el elemento 0 y a `Bolsillo` en el elemento 2
- **THEN** la respuesta es 409 con dos errores `INSUFFICIENT_FUNDS`, en `[0].amount` y en `[2].amount`

#### Scenario: Gasto programado mayor que el saldo
- **WHEN** el usuario registra sobre `Nómina`, con saldo 100000, un gasto de 150000 con `occurredAt` dentro de 30 días
- **THEN** la respuesta es 201 con `"scheduled": true` y el `currentBalance` sigue en 100000

#### Scenario: Gasto que queda pendiente
- **WHEN** el asistente registra un gasto pendiente de 150000 sobre `Nómina`, con saldo 100000
- **THEN** el pendiente se guarda; el saldo se valida al aprobarlo

### Requirement: Saldo suficiente al modificar un movimiento
`PATCH /api/transactions/{id}` SHALL responder 409 con un error `INSUFFICIENT_FUNDS` en `amount` por
cada cuenta no `CREDIT` cuyo `currentBalance` el movimiento resultante bajaría por debajo de cero,
comparado con el guardado. El movimiento SHALL quedar como estaba. Aplica a subir un gasto, cambiar
de cuenta o de tipo, reducir un ingreso o una transferencia entrante y pasar un ingreso a fecha futura.

#### Scenario: Subir el monto de un gasto
- **WHEN** un gasto de 30000 sobre `Nómina`, que quedó con saldo 20000, pasa a 60000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount`, el gasto sigue en 30000 y el saldo en 20000

#### Scenario: Cambiar el gasto a una cuenta sin saldo
- **WHEN** un gasto de 45000 pasa de la cuenta A a `Bolsillo`, con saldo 10000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount` que nombra `Bolsillo`, y el gasto sigue en A

#### Scenario: Reducir un ingreso ya gastado
- **WHEN** `Nómina` recibió un ingreso de 100000, gastó 90000 (saldo 10000) y el ingreso pasa a 50000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount` y el ingreso sigue en 100000

#### Scenario: Pasar un ingreso a fecha futura
- **WHEN** en la misma situación el ingreso recibe `{"occurredAt": <dentro de 30 días>}`
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount` y el ingreso conserva su fecha

#### Scenario: Bajar el monto de un gasto
- **WHEN** un gasto sobre una cuenta con saldo -5000 pasa de 30000 a 20000
- **THEN** la respuesta es 200 y el saldo sube 10000

### Requirement: Saldo suficiente al eliminar un movimiento
`DELETE /api/transactions/{id}` sobre un ingreso o una transferencia confirmados, cuyo borrado bajaría
el `currentBalance` de una cuenta no `CREDIT` por debajo de cero, SHALL responder 409 con un error
`INSUFFICIENT_FUNDS` en `id`, sin borrar nada. Borrar un gasto siempre SHALL permitirse.

#### Scenario: Borrar un ingreso ya gastado
- **WHEN** `Nómina` recibió un ingreso de 100000, gastó 90000 (saldo 10000) y el usuario borra el ingreso
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `id`, y el ingreso sigue existiendo

#### Scenario: Borrar una transferencia que la cuenta destino ya gastó
- **WHEN** una transferencia de 50000 llegó a `Bolsillo`, que gastó 45000 (saldo 5000), y el usuario la borra
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `id` que nombra `Bolsillo`

#### Scenario: Borrar un ingreso programado
- **WHEN** el usuario borra un ingreso con fecha futura
- **THEN** la respuesta es 204: el ingreso todavía no movía el saldo

### Requirement: Saldo suficiente al aprobar un pendiente
`POST /api/transactions/{id}/approve` sobre un pendiente con fecha ya llegada, que bajaría el
`currentBalance` de una cuenta no `CREDIT` por debajo de cero, SHALL responder 409 con un error
`INSUFFICIENT_FUNDS` en `amount`. El movimiento SHALL seguir pendiente y ningún saldo SHALL cambiar.
Con la cuenta desactivada SHALL responder solo el error `INVALID_STATE`.

#### Scenario: Aprobar un gasto sin saldo
- **WHEN** el usuario aprueba un gasto pendiente de 150000 sobre `Nómina`, con saldo 100000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `amount`, el gasto sigue en `GET /api/transactions/pending` y el saldo sigue en 100000

#### Scenario: Aprobar un gasto programado
- **WHEN** el usuario aprueba un gasto pendiente de 150000 con fecha futura sobre la misma cuenta
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"scheduled": true`

### Requirement: Lo programado no se valida al llegar su fecha
Un movimiento programado SHALL empezar a contar cuando llega su fecha aunque deje una cuenta no
`CREDIT` en negativo. Las ocurrencias de una serie que se crean solas al consultar SHALL registrarse
igual. Ninguna consulta SHALL fallar por esta regla. Desde ahí rige la de las cuentas que ya están en
negativo.

#### Scenario: Llega un gasto programado mayor que el saldo
- **WHEN** `Nómina`, con saldo 100000, tiene un gasto programado de 150000 y llega su fecha
- **THEN** `GET /api/accounts` responde 200 con `currentBalance` -50000 para `Nómina`

### Requirement: Aprobar un pendiente con una categoría que ya no sirve
`POST /api/transactions/{id}/approve` sobre un gasto o un ingreso pendiente cuya categoría está
borrada, o ya no aplica a su tipo, SHALL responder 409 con un error `INVALID_STATE` en `categoryId`,
junto con los de sus cuentas desactivadas. El pendiente SHALL seguir pendiente y ningún saldo SHALL
cambiar. Corregida la categoría con `PATCH`, SHALL aprobarse como cualquier otro.

#### Scenario: Categoría borrada
- **WHEN** el usuario borra la categoría de un gasto pendiente y luego lo aprueba
- **THEN** la respuesta es 409 con un único error `INVALID_STATE` en el campo `categoryId`, y el gasto sigue en `GET /api/transactions/pending`

#### Scenario: Categoría corregida
- **WHEN** el usuario cambia con PATCH la categoría del pendiente por una de gasto viva y lo aprueba
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"`

### Requirement: Grupo de un pendiente
Todo pendiente SHALL pertenecer a un grupo: los movimientos que entraron juntos sin confirmar. Los
que una misma petición deja pendientes SHALL compartir grupo, y dos peticiones SHALL NOT compartirlo.
La respuesta de un movimiento SHALL incluir `groupId` en el alta, la modificación, la aprobación y la
lista de pendientes: el id del grupo, o `null` si entró confirmado. El grupo SHALL conservarse al
confirmar y al modificar. El cliente no lo envía: si llega en el cuerpo, se ignora.

#### Scenario: Alta confirmada
- **WHEN** el usuario registra un gasto en la moneda de su cuenta
- **THEN** la respuesta es 201 con `"groupId": null`

#### Scenario: Convertidos de un mismo lote
- **WHEN** el usuario envía un lote con dos gastos en otra moneda que la de su cuenta y uno en la misma
- **THEN** los dos convertidos devuelven `"status": "PENDING"` y el mismo `groupId`, y el tercero `"groupId": null`

#### Scenario: Dos lotes
- **WHEN** el usuario envía dos lotes, cada uno con un gasto convertido
- **THEN** los dos pendientes tienen `groupId` distintos

#### Scenario: groupId en el cuerpo
- **WHEN** un elemento del lote trae un `groupId`
- **THEN** la respuesta es 201 y el movimiento no lo toma: `groupId` es `null` si entra confirmado

#### Scenario: El grupo en la lista de pendientes
- **WHEN** el usuario pide `GET /api/transactions/pending`
- **THEN** cada pendiente trae su `groupId`, no nulo

#### Scenario: El grupo se conserva
- **WHEN** el usuario corrige con PATCH un pendiente y después lo aprueba uno a uno
- **THEN** las dos respuestas traen el mismo `groupId` que tenía

### Requirement: Confirmar un grupo
`POST /api/transactions/groups/{groupId}/approve` SHALL confirmar todos los pendientes del grupo que
siguen pendientes y responder 200 con ellos, en el orden en que entraron, cada uno con
`"status": "CONFIRMED"` y la forma de la respuesta del alta. Desde ese momento SHALL mover los saldos
y contar en los reportes. Los que ya se aprobaron o rechazaron uno a uno SHALL NOT cambiar.

#### Scenario: Confirmar dos gastos
- **WHEN** el usuario confirma un grupo con un gasto de 20000 y otro de 15000 sobre la misma cuenta
- **THEN** la respuesta es 200 con los dos movimientos `CONFIRMED`, el `currentBalance` de la cuenta baja 35000 y ninguno sale en `GET /api/transactions/pending`

#### Scenario: Grupo con un movimiento ya aprobado uno a uno
- **WHEN** el usuario aprueba uno a uno uno de los dos pendientes del grupo y luego confirma el grupo
- **THEN** la respuesta es 200 con solo el otro movimiento, y el primero no vuelve a mover el saldo

#### Scenario: Grupo con un movimiento ya rechazado
- **WHEN** el usuario rechaza uno de los dos pendientes del grupo y luego confirma el grupo
- **THEN** la respuesta es 200 con solo el otro movimiento

### Requirement: Validaciones al confirmar un grupo
Confirmar un grupo SHALL aplicar a cada pendiente las reglas de la aprobación uno a uno, y SHALL
validar el saldo sobre el efecto conjunto del grupo, como el alta en lote. Si alguna falla, SHALL
responder 409 con todos los errores, cada uno con el índice del pendiente en el orden del grupo
(`[i].accountId`, `[i].destinationAccountId`, `[i].categoryId`, `[i].amount`), y SHALL NOT confirmar
ninguno. Los errores `INVALID_STATE` SHALL salir sin los de saldo. Un pendiente sin cuenta SHALL dar
`INVALID_STATE` en `[i].accountId`: se aprueba uno a uno, con cuenta y categoría.

#### Scenario: Una cuenta desactivada
- **WHEN** el usuario confirma un grupo de dos gastos y la cuenta del segundo está desactivada
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `[1].accountId`, los dos siguen en `GET /api/transactions/pending` y ningún saldo cambia

#### Scenario: Categoría borrada
- **WHEN** el usuario confirma un grupo cuyo primer gasto tiene la categoría borrada
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `[0].categoryId` y nada se confirma

#### Scenario: Saldo que alcanza para cada uno pero no para los dos
- **WHEN** el usuario confirma un grupo de dos gastos de 60000 sobre una cuenta `DEBIT` con saldo 100000
- **THEN** la respuesta es 409 con un error `INSUFFICIENT_FUNDS` en `[1].amount`, los dos siguen pendientes y el saldo sigue en 100000

#### Scenario: Un ingreso del grupo compensa un gasto
- **WHEN** el usuario confirma un grupo con un gasto de 150000 y un ingreso de 100000 sobre una cuenta con saldo 100000
- **THEN** la respuesta es 200 y el saldo queda en 50000

#### Scenario: Gasto con fecha futura
- **WHEN** el grupo tiene un gasto con fecha futura mayor que el saldo
- **THEN** la respuesta es 200 con el gasto `"scheduled": true`, como al aprobarlo uno a uno

#### Scenario: Pendiente sin cuenta
- **WHEN** el usuario confirma el grupo de un pendiente compartido, que no tiene cuenta
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en `[0].accountId`, y nada se confirma

### Requirement: Descartar un grupo
`POST /api/transactions/groups/{groupId}/reject` SHALL borrar los pendientes del grupo que siguen
pendientes y responder 204 sin cuerpo. No SHALL quedar rastro de ellos en saldos, reportes ni en la
lista de pendientes. Los que ya se aprobaron uno a uno SHALL NOT cambiar.

#### Scenario: Descartar dos gastos
- **WHEN** el usuario descarta un grupo de dos gastos pendientes
- **THEN** la respuesta es 204, ninguno sale en `GET /api/transactions/pending` y el saldo de la cuenta no cambia

#### Scenario: Descartar con uno ya aprobado
- **WHEN** el usuario aprueba uno a uno uno de los dos pendientes del grupo y luego descarta el grupo
- **THEN** la respuesta es 204, el aprobado sigue confirmado y el otro deja de existir

### Requirement: Confirmar o descartar lo que no es un grupo propio con pendientes
Sobre un grupo inexistente, de otro usuario o cuyos movimientos ya no existen, confirmar y descartar
SHALL responder 404 `NOT_FOUND` en `groupId`, sin revelar si existe. Sobre un grupo propio sin
pendientes porque ya se confirmaron SHALL responder 409 `INVALID_STATE` en `status`. Un `{groupId}`
que no es UUID SHALL responder 400 `VALIDATION_ERROR` en `groupId`. Ninguno SHALL cambiar nada.

#### Scenario: Grupo de otro usuario
- **WHEN** el usuario confirma o descarta el grupo de los pendientes de otro usuario
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `groupId`, y los pendientes del otro usuario siguen pendientes

#### Scenario: Grupo que no existe
- **WHEN** el `groupId` es un UUID válido que no corresponde a ningún movimiento
- **THEN** confirmar y descartar responden 404 con un error `NOT_FOUND` en el campo `groupId`

#### Scenario: Confirmar dos veces
- **WHEN** el usuario confirma un grupo que ya confirmó
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `status`, y los saldos no vuelven a moverse

#### Scenario: Descartar un grupo confirmado
- **WHEN** el usuario descarta un grupo que ya confirmó
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `status`, y los movimientos siguen confirmados

#### Scenario: Descartar dos veces
- **WHEN** el usuario descarta un grupo que ya descartó
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `groupId`

#### Scenario: groupId mal formado
- **WHEN** el `{groupId}` de la ruta es `abc`
- **THEN** confirmar y descartar responden 400 con un error `VALIDATION_ERROR` en el campo `groupId`

### Requirement: Grupos autenticados con JWT
Confirmar y descartar un grupo SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial
Basic compartida, SHALL responder 401 sin cambiar nada.

#### Scenario: Confirmar sin credencial
- **WHEN** se envía `POST /api/transactions/groups/{groupId}/approve` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`, y el grupo sigue pendiente

#### Scenario: Confirmar con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions/groups/{groupId}/approve` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Descartar sin credencial
- **WHEN** se envía `POST /api/transactions/groups/{groupId}/reject` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`, y el grupo sigue existiendo

#### Scenario: Descartar con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions/groups/{groupId}/reject` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

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
