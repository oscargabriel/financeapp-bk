# reportes Specification

## Purpose
Reportes sobre los movimientos del usuario autenticado: el reporte de movimientos por rango de días
con `GET /api/reports/transactions` (la lista, sus totales por tipo y por categoría, y el neto), y la
consulta de saldo con `GET /api/reports/balance` (ingresos menos gastos de un rango y de siempre,
con las cuentas activas y el cupo disponible de las tarjetas).

## Requirements

### Requirement: Reporte de movimientos por rango de días
`GET /api/reports/transactions` SHALL devolver los movimientos del usuario del token cuya fecha, en
la zona horaria del usuario, cae entre `from` y `to`, ambos inclusive. La lista SHALL ir ordenada por
`occurredAt` descendente, y SHALL traer además la moneda base del usuario y los totales.

#### Scenario: Mes completo sin filtros
- **WHEN** el usuario pide `from` el primer día y `to` el último día del mes en curso, sin filtros
- **THEN** la respuesta es 200 con todos sus gastos, ingresos y transferencias del mes, del más reciente al más antiguo, y `currencyCode` igual a su moneda base

#### Scenario: Un solo día
- **WHEN** el usuario pide `from` y `to` iguales
- **THEN** la respuesta trae solo los movimientos de ese día

#### Scenario: Rango sin movimientos
- **WHEN** el usuario pide un rango en el que no tiene movimientos
- **THEN** la respuesta es 200 con `transactions` vacía, `totalsByType` con los tres tipos en cero y `totalsByCategory` vacía

### Requirement: Día cortado en la zona horaria del usuario
El sistema SHALL decidir a qué día pertenece un movimiento con la zona horaria del usuario, no con
UTC.

#### Scenario: Movimiento de la noche que en UTC ya es del día siguiente
- **WHEN** el usuario de `America/Bogota` tiene un gasto el último día del mes a las 21:30 locales (02:30 UTC del día siguiente) y pide `from` y `to` iguales a ese último día
- **THEN** el gasto aparece en el reporte

#### Scenario: El día siguiente no lo incluye
- **WHEN** el mismo usuario pide `from` y `to` iguales al primer día del mes siguiente
- **THEN** ese gasto no aparece

### Requirement: Cada movimiento del reporte
Cada elemento de `transactions` SHALL traer `id`, `type`, `accountId`, `destinationAccountId`,
`categoryId`, `categoryName`, `amount`, `currencyCode`, `amountBase`, `description`, `notes` y
`occurredAt` en UTC. `categoryId` y `categoryName` SHALL ir en null en las transferencias, y
`destinationAccountId` en null en gastos e ingresos.

#### Scenario: Gasto y transferencia en el mismo reporte
- **WHEN** el reporte incluye un gasto de Mercado y una transferencia
- **THEN** el gasto trae `categoryName` "Mercado" y `destinationAccountId` null, y la transferencia trae `categoryId` y `categoryName` null y su `destinationAccountId`

### Requirement: Totales del reporte
`totalsByType` SHALL traer una entrada por cada tipo consultado —los tres sin filtro de tipo, en el
orden `EXPENSE`, `INCOME`, `TRANSFER`—, con `total` (suma de `amountBase`) y `count`, en cero si no
hubo movimientos. `totalsByCategory` SHALL traer una entrada por categoría con movimientos, con
`categoryId`, `categoryName`, `total` y `count`, ordenada por `total` descendente.

#### Scenario: Totales del mes
- **WHEN** el reporte del mes trae gastos, ingresos y transferencias
- **THEN** cada `total` de `totalsByType` es la suma de `amountBase` de los movimientos de ese tipo en la lista, y `count` su cantidad

#### Scenario: Las transferencias no entran en los totales por categoría
- **WHEN** el reporte incluye transferencias
- **THEN** `totalsByCategory` no tiene ninguna entrada por ellas, y la suma de sus `total` es la de gastos más ingresos

#### Scenario: Totales con filtro de tipo
- **WHEN** el usuario filtra por `type=EXPENSE`
- **THEN** `totalsByType` trae una sola entrada, la de `EXPENSE`

### Requirement: Filtro opcional por categorías
El parámetro `categoryId` SHALL aceptar uno o varios ids, repitiendo el parámetro o separados por
coma, y SHALL limitar el reporte a los movimientos de esas categorías. Con este filtro las
transferencias SHALL quedar fuera, porque no tienen categoría.

#### Scenario: Una categoría
- **WHEN** el usuario pide el mes con `categoryId` de Mercado
- **THEN** la respuesta trae solo sus movimientos de Mercado

#### Scenario: Varias categorías
- **WHEN** el usuario pide `categoryId` de Mercado y de Restaurantes
- **THEN** la respuesta trae los movimientos de las dos y `totalsByCategory` tiene exactamente esas dos entradas

#### Scenario: Categoría de otro usuario
- **WHEN** el usuario pide un `categoryId` bien formado que no es suyo
- **THEN** la respuesta es 200 con la lista vacía: nunca devuelve movimientos ajenos ni revela si la categoría existe

### Requirement: Filtro opcional por cuentas
El parámetro `accountId` SHALL aceptar uno o varios ids, repitiendo el parámetro o separados por coma,
y SHALL limitar el reporte a los movimientos en los que alguna de esas cuentas es el origen o el
destino. Cada movimiento SHALL aparecer una sola vez, y los totales SHALL calcularse sobre la lista
filtrada con las mismas reglas de siempre. Se combina con `categoryId` y `type` con Y.

#### Scenario: Una cuenta con gastos y una transferencia que entra
- **WHEN** el usuario pide el mes con el `accountId` de su cuenta Efectivo, que tiene cuatro gastos y recibe el retiro de cajero desde Bancolombia
- **THEN** la respuesta es 200 con esos cinco movimientos, la transferencia con `destinationAccountId` igual a Efectivo, y `totalsByType` con `EXPENSE` en 4 movimientos y `TRANSFER` en 1

#### Scenario: Una cuenta que solo origina una transferencia
- **WHEN** el usuario pide el mes con el `accountId` de su cuenta Ahorros USD, cuyo único movimiento del mes es el cambio de dólares hacia Bancolombia
- **THEN** la respuesta trae solo esa transferencia, con `accountId` igual a Ahorros USD

#### Scenario: Varias cuentas con una transferencia entre ellas
- **WHEN** el usuario pide `accountId` de Efectivo y de Bancolombia
- **THEN** la respuesta trae los movimientos de las dos cuentas, y el retiro de cajero entre ellas aparece una sola vez

#### Scenario: Cuenta y tipo combinados
- **WHEN** el usuario pide el `accountId` de Bancolombia con `type=EXPENSE,TRANSFER`
- **THEN** la respuesta trae los gastos de Bancolombia y las dos transferencias del mes, la que sale de Bancolombia y la que entra, y `totalsByType` trae solo `EXPENSE` y `TRANSFER`, sumados sobre esa lista

#### Scenario: Cuenta y categoría combinadas
- **WHEN** el usuario pide el `accountId` de Efectivo con el `categoryId` de Mercado
- **THEN** la respuesta trae solo los gastos de Mercado pagados desde Efectivo

#### Scenario: Cuenta de otro usuario
- **WHEN** el usuario pide un `accountId` bien formado que pertenece a otro usuario y tiene movimientos en el rango
- **THEN** la respuesta es 200 con la lista vacía: nunca devuelve movimientos ajenos ni revela si la cuenta existe

#### Scenario: Sin filtro de cuenta
- **WHEN** el usuario pide el reporte sin `accountId`
- **THEN** la respuesta es la misma que antes de existir el filtro

### Requirement: Filtro opcional por tipo
El parámetro `type` SHALL aceptar uno o varios de `EXPENSE`, `INCOME` y `TRANSFER`, sin distinguir
mayúsculas, repitiendo el parámetro o separados por coma, y SHALL limitar el reporte a esos tipos.

#### Scenario: Un tipo
- **WHEN** el usuario pide el mes con `type=INCOME`
- **THEN** la respuesta trae solo sus ingresos del mes

#### Scenario: Categoría y tipo combinados
- **WHEN** el usuario pide `categoryId` de Mercado y de Salario con `type=EXPENSE`
- **THEN** la respuesta trae solo los gastos de Mercado

### Requirement: Validación de los parámetros
`from` y `to` SHALL ser obligatorios con formato `YYYY-MM-DD`. Un parámetro inválido SHALL
responder 400 con un error `VALIDATION_ERROR` en el campo del parámetro, sin consultar movimientos.

#### Scenario: Falta from o to
- **WHEN** el usuario pide el reporte sin `from`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: Formato inválido
- **WHEN** `to` vale `2026-10-1`, `2026/10/01` o `01-10-2026`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `to`

#### Scenario: Fecha inexistente
- **WHEN** `from` vale `2026-02-30`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: from posterior a to
- **WHEN** `from` es `2026-10-31` y `to` es `2026-10-01`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: categoryId mal formado
- **WHEN** `categoryId` vale `abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: accountId mal formado
- **WHEN** `accountId` vale `abc`, o uno de los valores separados por coma no es un UUID
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId`

#### Scenario: type fuera del enum
- **WHEN** `type` vale `PAGO`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `type`

### Requirement: Aislamiento por usuario
El reporte SHALL contener únicamente movimientos del usuario del token, sin aceptar un `userId` por
la URL ni por el query.

#### Scenario: Dos usuarios con movimientos en el mismo rango
- **WHEN** otro usuario registra un movimiento en el mes y el usuario del escenario pide el reporte del mes
- **THEN** el movimiento del otro usuario no aparece, y el reporte del otro usuario trae solo el suyo

### Requirement: Reporte autenticado con JWT
`GET /api/reports/transactions` SHALL exigir un Bearer JWT válido. Sin credencial, o con la
credencial Basic compartida de `/auth/*` y `/status`, SHALL responder 401.

#### Scenario: Sin credencial
- **WHEN** se pide el reporte sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se pide el reporte con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Movimientos de una categoría borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una categoría borrada,
con su `categoryId` y el `categoryName` que tenía al borrarse, tanto en `transactions` como en
`totalsByCategory`.

#### Scenario: Gasto de una categoría borrada
- **WHEN** el usuario borra `Huerta`, que tiene un gasto de hoy, y pide el reporte de hoy
- **THEN** el gasto aparece con el `categoryId` y el `categoryName` que tenía `Huerta` al borrarse, y `totalsByCategory` trae una entrada de esa categoría con ese gasto

### Requirement: Movimientos de una cuenta borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una cuenta borrada, con
su `accountId`, y el filtro `accountId` con el id de esa cuenta SHALL seguir encontrándolos.

#### Scenario: Gasto de una cuenta borrada
- **WHEN** el usuario borra `Caja chica`, que tiene un gasto de hoy, y pide el reporte de hoy filtrado por esa cuenta
- **THEN** el gasto aparece con el `accountId` de la cuenta borrada y cuenta en los totales

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
