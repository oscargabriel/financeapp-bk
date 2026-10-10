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
condiciones que en el alta (cuenta activa y en COP, categoría vigente y compatible). Una
referencia ajena, inexistente o mal formada SHALL dar el mismo error, sin revelar si existe para
otro usuario. Las referencias que el parche no envía y que siguen correspondiendo al tipo
resultante SHALL conservarse sin volver a validarse, salvo la categoría cuando cambia el tipo.

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

### Requirement: Origen de un movimiento
Todo movimiento SHALL tener un origen: `WEB`, `TELEGRAM` o `IMPORT`. Los que entran por
`POST /api/transactions` SHALL tener origen `WEB`, y los que registra el asistente, `TELEGRAM`. Un
movimiento de origen `TELEGRAM` SHALL entrar `PENDING`. La respuesta de un movimiento SHALL incluir
el campo `origin` en el alta, la modificación, la aprobación y la lista de pendientes.

#### Scenario: El alta es WEB
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido
- **THEN** la respuesta es 201 y cada movimiento devuelve `"origin": "WEB"`

#### Scenario: El cliente no puede elegir el origen
- **WHEN** un elemento del lote trae `"origin": "TELEGRAM"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"` y `"status": "CONFIRMED"`

#### Scenario: El origen se conserva al aprobar
- **WHEN** el usuario aprueba un pendiente creado por el asistente
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "TELEGRAM"`

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
