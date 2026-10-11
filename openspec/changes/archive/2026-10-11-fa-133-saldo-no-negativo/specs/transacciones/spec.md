# Spec Delta

## ADDED Requirements

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
