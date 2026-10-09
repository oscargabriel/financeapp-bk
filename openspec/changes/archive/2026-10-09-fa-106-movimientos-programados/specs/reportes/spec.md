## MODIFIED Requirements

### Requirement: Día cortado en la zona horaria del usuario
El sistema SHALL decidir a qué día pertenece un movimiento con la zona horaria del usuario, no con
UTC.

#### Scenario: Movimiento de la noche que en UTC ya es del día siguiente
- **WHEN** el usuario de `America/Bogota` tiene un gasto el último día de un mes ya cumplido a las 21:30 locales (02:30 UTC del día siguiente) y pide `from` y `to` iguales a ese último día
- **THEN** el gasto aparece en el reporte

#### Scenario: El día siguiente no lo incluye
- **WHEN** el mismo usuario pide `from` y `to` iguales al primer día del mes siguiente
- **THEN** ese gasto no aparece

### Requirement: Cada movimiento del reporte
Cada elemento de `transactions` SHALL traer `id`, `type`, `accountId`, `destinationAccountId`,
`categoryId`, `categoryName`, `amount`, `currencyCode`, `amountBase`, `description`, `notes`,
`occurredAt` en UTC y `scheduled`. `categoryId` y `categoryName` SHALL ir en null en las
transferencias, y `destinationAccountId` en null en gastos e ingresos. `scheduled` SHALL ser `true`
si `occurredAt` es posterior al momento en que se atiende la petición.

#### Scenario: Gasto y transferencia en el mismo reporte
- **WHEN** el reporte incluye un gasto de Mercado y una transferencia
- **THEN** el gasto trae `categoryName` "Mercado" y `destinationAccountId` null, y la transferencia trae `categoryId` y `categoryName` null y su `destinationAccountId`

#### Scenario: Un movimiento programado en la lista
- **WHEN** el rango incluye un gasto con fecha posterior al momento actual
- **THEN** ese gasto aparece en `transactions` con `"scheduled": true`, y los demás con `"scheduled": false`

### Requirement: Totales del reporte
`totalsByType` SHALL traer una entrada por cada tipo consultado —los tres sin filtro de tipo, en el
orden `EXPENSE`, `INCOME`, `TRANSFER`—, con `total` (suma de `amountBase`) y `count`, en cero si no
hubo movimientos. `totalsByCategory` SHALL traer una entrada por categoría con movimientos, con
`categoryId`, `categoryName`, `total` y `count`, ordenada por `total` descendente. Los dos SHALL
contar solo los movimientos de la lista con `scheduled` en `false`.

#### Scenario: Totales del mes
- **WHEN** el reporte del mes trae gastos, ingresos y transferencias
- **THEN** cada `total` de `totalsByType` es la suma de `amountBase` de los movimientos de ese tipo en la lista con `scheduled` en `false`, y `count` su cantidad

#### Scenario: Las transferencias no entran en los totales por categoría
- **WHEN** el reporte incluye transferencias
- **THEN** `totalsByCategory` no tiene ninguna entrada por ellas, y la suma de sus `total` es la de gastos más ingresos

#### Scenario: Totales con filtro de tipo
- **WHEN** el usuario filtra por `type=EXPENSE`
- **THEN** `totalsByType` trae una sola entrada, la de `EXPENSE`

#### Scenario: Los programados no suman
- **WHEN** el rango trae un gasto vigente de 30000 y un gasto programado de 40000 en la misma categoría
- **THEN** el total de `EXPENSE` es 30000 con `count` 1, y la categoría trae `total` 30000 y `count` 1

#### Scenario: Rango con solo programados
- **WHEN** el usuario pide un rango futuro en el que solo tiene un gasto programado
- **THEN** `transactions` trae ese gasto, `totalsByType` trae los tres tipos en cero y `totalsByCategory` va vacía

### Requirement: Neto del reporte de movimientos
`GET /api/reports/transactions` SHALL traer un campo `net` igual al total de `INCOME` menos el total
de `EXPENSE` de `totalsByType`, sobre la misma lista que devuelve. Las transferencias no entran. Un
tipo que el filtro deja fuera SHALL contar como cero. El neto SHALL devolverse aunque sea negativo.
Como sale de `totalsByType`, los programados no entran.

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

#### Scenario: Rango con solo programados
- **WHEN** el usuario pide un rango futuro en el que solo tiene un gasto programado
- **THEN** la respuesta trae `net` 0

### Requirement: Día cortado en la zona horaria del usuario en la consulta de saldo
`GET /api/reports/balance` SHALL asignar cada movimiento a un día con la zona horaria del usuario,
con el mismo corte que el reporte de movimientos.

#### Scenario: Gasto de la última noche del mes
- **WHEN** el usuario de `America/Bogota` tiene un gasto de 35.000 el último día de un mes ya cumplido a las 21:30 locales y pide `from` y `to` iguales a ese día
- **THEN** `period` trae `expense` 35000

## ADDED Requirements

### Requirement: Los programados no cuentan en los reportes
Un movimiento con `occurredAt` posterior al momento actual SHALL quedar fuera de los totales de
`GET /api/reports/transactions` (aunque aparezca en su lista), de `GET /api/reports/balance` (el
neto del rango, el histórico y el `currentBalance` de cada cuenta) y de `GET /api/monthly-spending`
(el gasto del mes y su avance contra la meta). Cuando llega su fecha SHALL contar en los tres sin que
nadie lo modifique.

#### Scenario: Consulta de saldo con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/reports/balance` del mes siguiente trae `period.expense` 0, y `allTime.expense` no lo incluye

#### Scenario: Gasto mensual con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/monthly-spending` de ese mes no lo suma al gasto del mes

#### Scenario: El programado cuenta al llegar su fecha
- **WHEN** el usuario registra un gasto de 40000 con `occurredAt` unos segundos en el futuro y pide `GET /api/reports/balance` después de esa fecha
- **THEN** `period.expense` y `allTime.expense` ya incluyen los 40000

### Requirement: Gasto mensual autenticado con JWT
`GET /api/monthly-spending` SHALL exigir un Bearer válido. Sin credencial o con la credencial Basic
compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`.

#### Scenario: Sin credencial
- **WHEN** se envía `GET /api/monthly-spending` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `GET /api/monthly-spending` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`
