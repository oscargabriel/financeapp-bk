# Spec Delta

## MODIFIED Requirements

### Requirement: Crear un movimiento desde el asistente
Con `crear_movimiento`, el asistente SHALL registrar un movimiento del usuario del token con
`"status": "PENDING"` y `"origin": "TELEGRAM"`, en la moneda de su cuenta, y responder
`"intent": "CREATE_TRANSACTION"` con el movimiento en `transaction`. `transaction` SHALL traer solo
`id`, `type`, `status`, `amount`, `currencyCode`, `description`, `occurredAt` en UTC, y los nombres
`account`, `destinationAccount` y `category`, en `null` cuando no aplican. La cuenta, la cuenta
destino y la categoría SHALL resolverse por nombre, solo entre las activas del usuario, sin
distinguir mayúsculas ni tildes. El movimiento SHALL NOT mover saldos hasta aprobarse.

#### Scenario: Gasto con cuenta y categoría propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE`, `monto` 20000, una cuenta y una categoría de gasto del usuario escritas en minúsculas
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y un `transaction` con `"status": "PENDING"`, `amount` 20000, y en `account` y `category` los nombres tal como los guardó el usuario; el movimiento aparece en `GET /api/transactions/pending` con `"origin": "TELEGRAM"`

#### Scenario: Solo los campos livianos
- **WHEN** el asistente registra un gasto
- **THEN** `transaction` no trae `accountId`, `categoryId`, `origin`, `notes`, `originalAmount` ni ningún otro campo fuera de la lista del requisito

#### Scenario: El saldo no cambia
- **WHEN** el asistente registra un gasto sobre una cuenta
- **THEN** el `currentBalance` de esa cuenta en `GET /api/accounts` sigue igual

#### Scenario: Transferencia entre cuentas propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `TRANSFER`, una cuenta y una `cuenta_destino` del usuario
- **THEN** la respuesta es 200 con un `transaction` pendiente de tipo `TRANSFER`, los nombres de las dos cuentas en `account` y `destinationAccount`, y `category` en `null`

#### Scenario: Sin fecha
- **WHEN** el modelo no envía `fecha`
- **THEN** el movimiento tiene como `occurredAt` el instante en que se atendió la petición

#### Scenario: Fecha de otro día
- **WHEN** el modelo envía `fecha` `2026-10-05` y hoy es otro día
- **THEN** el movimiento tiene como `occurredAt` el 2026-10-05 a las 12:00 en la zona de la aplicación, expresado en UTC

### Requirement: Consultar movimientos desde el asistente
Con `consultar_movimientos`, el asistente SHALL responder `"intent": "LIST_TRANSACTIONS"` con un
`report` liviano del usuario del token para el rango y los filtros dados: `from`, `to`,
`currencyCode` (la moneda base del usuario), `income`, `expense`, `net` y `transactions`. Cada
elemento de `transactions` SHALL traer solo `id`, `type`, `amount`, `currencyCode`, `description`,
`occurredAt` en UTC y los nombres `account` y `category`. Los totales y la lista SHALL coincidir con
`GET /api/reports/transactions` con los mismos parámetros, sin los programados. Sin rango, SHALL
usar el mes en curso. Antes de leer, SHALL poner al día las series del usuario, igual que
`/reports`.

#### Scenario: Gastos de un rango
- **WHEN** el modelo llama `consultar_movimientos` con `desde`, `hasta` y `tipo` `EXPENSE`
- **THEN** la respuesta es 200 con `"intent": "LIST_TRANSACTIONS"` y un `report` cuyos `income`, `expense` y `net` son los totales `INCOME` y `EXPENSE` (0 si no hay) y el `net` de `GET /api/reports/transactions` con esos parámetros

#### Scenario: La lista es la del reporte sin los programados
- **WHEN** el asistente responde un `report`
- **THEN** sus `transactions` tienen los mismos `id`, en el mismo orden, que los movimientos con `"scheduled": false` de `GET /api/reports/transactions` con los mismos parámetros

#### Scenario: Sin rango
- **WHEN** el modelo no envía `desde` ni `hasta`
- **THEN** el `report` cubre del primer al último día del mes en curso

#### Scenario: Serie vencida
- **WHEN** el usuario tiene una serie activa con una ocurrencia vencida que todavía no se registró, y el modelo llama `consultar_movimientos` sobre un rango que la incluye
- **THEN** la ocurrencia aparece en `transactions` y cuenta en los totales, como en `GET /api/reports/transactions`

### Requirement: Consultar el saldo desde el asistente
Con `consultar_saldo`, el asistente SHALL responder `"intent": "GET_BALANCE"` con un `balance`
liviano del usuario del token para el rango dado, o el mes en curso sin rango: `from`, `to`,
`currencyCode`, `income`, `expense`, `net`, `allTimeNet` y `accounts`, cada cuenta con solo `name`,
`currencyCode` y `currentBalance`. Los valores SHALL coincidir con los de
`GET /api/reports/balance` con el mismo rango. Antes de leer, SHALL poner al día las series del
usuario.

#### Scenario: Saldo del mes
- **WHEN** el modelo llama `consultar_saldo` sin argumentos
- **THEN** la respuesta es 200 con `"intent": "GET_BALANCE"` y un `balance` cuyos `income`, `expense` y `net` son los de `period`, cuyo `allTimeNet` es `allTime.net`, y cuyas `accounts` tienen el `name`, `currencyCode` y `currentBalance` de las de `GET /api/reports/balance` sin parámetros

#### Scenario: Solo los campos livianos del saldo
- **WHEN** el asistente responde un `balance`
- **THEN** sus cuentas no traen `id`, `type`, `creditLimit` ni `availableCredit`
