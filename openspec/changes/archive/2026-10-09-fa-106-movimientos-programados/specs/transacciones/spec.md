## ADDED Requirements

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
