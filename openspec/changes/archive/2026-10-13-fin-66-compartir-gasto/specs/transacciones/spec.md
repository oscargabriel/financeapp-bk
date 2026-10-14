# Spec Delta

## MODIFIED Requirements

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
