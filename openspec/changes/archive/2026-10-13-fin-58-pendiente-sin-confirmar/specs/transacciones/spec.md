## MODIFIED Requirements

### Requirement: Estado de aprobación de un movimiento
Todo movimiento SHALL tener un estado, `PENDING` o `CONFIRMED`. Un movimiento SHALL quedar `PENDING`
cuando necesita que el usuario lo confirme, sea cual sea su origen: lo que registra el asistente y lo
que el alta convierte de otra moneda. Lo demás que entra por `POST /api/transactions` SHALL quedar
`CONFIRMED`. La respuesta de un movimiento SHALL incluir el campo `status` en el alta, la
modificación, la aprobación y la lista de pendientes.

#### Scenario: El alta entra confirmada
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido en la moneda de sus cuentas
- **THEN** la respuesta es 201 y cada movimiento devuelve `"status": "CONFIRMED"`

#### Scenario: El cliente no puede elegir el estado
- **WHEN** un elemento del lote trae `"status": "PENDING"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"status": "CONFIRMED"`

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

## ADDED Requirements

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
ninguno. Los errores `INVALID_STATE` SHALL salir sin los de saldo.

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
