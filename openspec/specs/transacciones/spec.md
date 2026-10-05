# transacciones Specification

## Purpose
Movimientos (gastos, ingresos y transferencias) del usuario autenticado: el alta en lote con
`POST /api/transactions`, y la modificación parcial y la eliminación de uno con `PATCH` y `DELETE`
sobre `/api/transactions/{id}`.

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
