# Spec Delta

## ADDED Requirements

### Requirement: Neto del reporte de movimientos
`GET /api/reports/transactions` SHALL traer un campo `net` igual al total de `INCOME` menos el total
de `EXPENSE` de `totalsByType`, sobre la misma lista que devuelve. Las transferencias no entran. Un
tipo que el filtro deja fuera SHALL contar como cero. El neto SHALL devolverse aunque sea negativo.

#### Scenario: Neto del mes
- **WHEN** el usuario del escenario pide el reporte del mes en curso sin filtros, con ingresos por 5.300.000 y gastos por 1.905.500
- **THEN** la respuesta trae `net` 3394500, y los demás campos del reporte no cambian

#### Scenario: Neto negativo con filtro de gastos
- **WHEN** el usuario pide el mismo mes con `type=EXPENSE`
- **THEN** la respuesta trae `net` -1905500

#### Scenario: Neto con solo transferencias
- **WHEN** el usuario pide el mismo mes con `type=TRANSFER`
- **THEN** la respuesta trae `net` 0

#### Scenario: Rango sin movimientos
- **WHEN** el usuario pide un rango en el que no tiene movimientos
- **THEN** la respuesta trae `net` 0

### Requirement: Consulta de saldo
`GET /api/reports/balance` SHALL devolver, en la moneda base del usuario (`currencyCode`), un
`period` con `from`, `to`, `income`, `expense` y `net` del rango pedido, y un `allTime` con
`income`, `expense` y `net` de todos sus movimientos. `income` y `expense` SHALL sumar `amountBase`
de ingresos y gastos; `net` SHALL ser su resta, aunque dé negativa. Las transferencias no entran.

#### Scenario: Mes en curso e histórico
- **WHEN** el usuario del escenario pide `from` y `to` del primer al último día del mes en curso
- **THEN** la respuesta es 200 con `currencyCode` COP, `period` con `income` 5300000, `expense` 1905500 y `net` 3394500, y `allTime` con `income` 14300000, `expense` 5170500 y `net` 9129500

#### Scenario: Mes anterior
- **WHEN** el usuario pide el primer y el último día del mes anterior
- **THEN** `period` trae `income` 4500000, `expense` 1820000 y `net` 2680000, y `allTime` es el mismo que con el mes en curso

#### Scenario: Neto negativo
- **WHEN** el usuario pide del segundo al último día del mes anterior, donde solo tiene gastos por 620.000
- **THEN** `period` trae `income` 0, `expense` 620000 y `net` -620000

#### Scenario: Los gastos con tarjeta de crédito cuentan como egresos
- **WHEN** el rango incluye gastos pagados con la tarjeta Visa
- **THEN** esos gastos están sumados en `expense` igual que los de cualquier otra cuenta

#### Scenario: Las transferencias no cuentan
- **WHEN** el rango incluye el retiro de cajero y el cambio de dólares
- **THEN** ni `income` ni `expense` los incluyen

### Requirement: Día cortado en la zona horaria del usuario en la consulta de saldo
`GET /api/reports/balance` SHALL asignar cada movimiento a un día con la zona horaria del usuario,
con el mismo corte que el reporte de movimientos.

#### Scenario: Gasto de la última noche del mes
- **WHEN** el usuario de `America/Bogota` tiene un gasto de 50.000 el último día del mes a las 21:30 locales y pide `from` y `to` iguales a ese día
- **THEN** `period` trae `expense` 50000

### Requirement: Rango por defecto de la consulta de saldo
Sin `from` ni `to`, `GET /api/reports/balance` SHALL tomar el mes en curso en la zona horaria de la
aplicación, y `period.from` y `period.to` SHALL traer el primer y el último día de ese mes.

#### Scenario: Sin parámetros
- **WHEN** el usuario del escenario pide la consulta de saldo sin parámetros
- **THEN** la respuesta es la misma que pidiendo el primer y el último día del mes en curso, con esas dos fechas en `period.from` y `period.to`

### Requirement: Cuentas en la consulta de saldo
`GET /api/reports/balance` SHALL traer en `accounts` las cuentas activas y no borradas del usuario,
ordenadas por nombre, cada una con `id`, `name`, `type`, `currencyCode`, `currentBalance`,
`creditLimit` y `availableCredit`. Las cuentas no se suman entre sí. En una cuenta que no es
`CREDIT`, o en una `CREDIT` sin cupo, `creditLimit` y `availableCredit` SHALL ser `null`.

#### Scenario: Tarjeta de crédito con cupo
- **WHEN** el usuario del escenario pide la consulta de saldo, y su tarjeta Visa tiene cupo 5.000.000 y saldo vigente -658.000
- **THEN** la cuenta Visa trae `currentBalance` -658000, `creditLimit` 5000000 y `availableCredit` 4342000

#### Scenario: Cuenta en otra moneda
- **WHEN** el usuario tiene la cuenta Ahorros USD
- **THEN** esa cuenta trae `currencyCode` USD y su `currentBalance` en dólares, con `creditLimit` y `availableCredit` en `null`

#### Scenario: Cuentas desactivadas y borradas
- **WHEN** el usuario tiene la cuenta desactivada Nequi y la borrada Davivienda
- **THEN** ninguna de las dos aparece en `accounts`

### Requirement: Validación de los parámetros de la consulta de saldo
`from` y `to` SHALL venir los dos o ninguno, con formato `YYYY-MM-DD`. Un parámetro inválido o
faltante SHALL responder 400 con un error `VALIDATION_ERROR` en el campo del parámetro, sin
consultar movimientos.

#### Scenario: Solo from
- **WHEN** el usuario pide la consulta de saldo con `from` y sin `to`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `to`

#### Scenario: Solo to
- **WHEN** el usuario pide la consulta de saldo con `to` y sin `from`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: Formato inválido
- **WHEN** `from` vale `2026/10/01` o `2026-02-30`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: from posterior a to
- **WHEN** `from` es `2026-10-31` y `to` es `2026-10-01`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

### Requirement: Aislamiento por usuario en la consulta de saldo
`GET /api/reports/balance` SHALL calcular los netos y listar las cuentas únicamente con datos del
usuario del token, sin aceptar un `userId` por la URL ni por el query.

#### Scenario: Otro usuario con movimientos en el mismo mes
- **WHEN** otro usuario registra un gasto en el mes en curso y pide su consulta de saldo
- **THEN** su `period` y su `allTime` traen solo ese gasto, sus `accounts` solo las suyas, y la consulta del usuario del escenario no cambia

### Requirement: Consulta de saldo autenticada con JWT
`GET /api/reports/balance` SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial
Basic compartida de `/auth/*` y `/status`, SHALL responder 401.

#### Scenario: Sin credencial
- **WHEN** se pide la consulta de saldo sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se pide la consulta de saldo con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
