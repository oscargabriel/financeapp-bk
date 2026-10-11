## ADDED Requirements

### Requirement: Ingresos de cada mes en el gasto mensual
Cada mes de `GET /api/monthly-spending` SHALL traer `totalIncome`: la suma de los ingresos (`INCOME`)
confirmados del mes que ya ocurrieron, con el mes decidido en la zona horaria del usuario. Las
transferencias MUST NOT sumar. `totalSpent`, la meta, `remaining`, `percentUsed` y
`transactionCount` SHALL conservar su significado; `transactionCount` cuenta solo los gastos.

#### Scenario: Mes en curso del escenario
- **WHEN** el usuario del escenario, con gastos por 1905500 y dos ingresos de 4500000 y 800000 en el mes en curso, pide `GET /api/monthly-spending`
- **THEN** el mes en curso trae `totalIncome` 5300000, `totalSpent` 1905500 y `transactionCount` 11

#### Scenario: Las transferencias no son ingresos
- **WHEN** el mes en curso del escenario tiene además dos transferencias que entran a la cuenta de débito
- **THEN** su `totalIncome` sigue siendo 5300000

#### Scenario: Meses anteriores con salario
- **WHEN** el usuario del escenario tiene un salario de 4500000 en cada uno de los dos meses anteriores
- **THEN** los dos meses traen `totalIncome` 4500000, y el de hace dos meses conserva `totalSpent` 1445000 y `transactionCount` 3

#### Scenario: Mes con meta y sin movimientos
- **WHEN** el usuario pide el mes siguiente, que solo tiene meta
- **THEN** el mes trae `totalIncome` 0 y `totalSpent` 0

### Requirement: Meses que trae el gasto mensual
`GET /api/monthly-spending` SHALL devolver, del más reciente al más antiguo, cada mes del rango
pedido que tenga al menos un gasto, un ingreso o una meta. Un mes sin ninguno de los tres MUST NOT
venir. Sin `from` ni `to` el rango SHALL ser los 12 meses que terminan en el mes en curso; con solo
`from`, hasta el mes en curso; con solo `to`, los 12 meses que terminan ahí.

#### Scenario: Mes con solo ingresos
- **WHEN** la persona tiene en un mes un ingreso y ningún gasto ni meta
- **THEN** ese mes viene con su `totalIncome`, `totalSpent` 0, `transactionCount` 0 y `budgetAmount`, `remaining` y `percentUsed` en `null`

#### Scenario: Rango por defecto del escenario
- **WHEN** el usuario del escenario pide `GET /api/monthly-spending` sin parámetros
- **THEN** recibe el mes en curso y los dos anteriores, en ese orden

#### Scenario: Usuario sin movimientos ni metas
- **WHEN** un usuario recién registrado pide `GET /api/monthly-spending`
- **THEN** la respuesta es 200 con un arreglo vacío

### Requirement: Validación de los parámetros del gasto mensual
`from` y `to` de `GET /api/monthly-spending` SHALL venir en formato `YYYY-MM`. Un mes mal escrito, o
un `from` posterior a `to`, SHALL responder 400 `VALIDATION_ERROR`.

#### Scenario: Mes mal escrito
- **WHEN** se pide `GET /api/monthly-spending?from=2026-13`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `from`

#### Scenario: Mes de cierre mal escrito
- **WHEN** se pide `GET /api/monthly-spending?to=2026-9`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `to`

#### Scenario: Rango invertido
- **WHEN** se pide `GET /api/monthly-spending` con `from` posterior a `to`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `from`

## MODIFIED Requirements

### Requirement: Los pendientes no cuentan en los reportes
Un movimiento `PENDING` SHALL quedar fuera de `GET /api/reports/transactions` (su lista, totales y
neto), de `GET /api/reports/balance` (el neto del rango y el histórico) y de
`GET /api/monthly-spending` (el gasto y el ingreso del mes, y el avance contra la meta). Al
aprobarse, SHALL contar en los tres como cualquier movimiento confirmado.

#### Scenario: Reporte de movimientos con un pendiente
- **WHEN** el usuario tiene en el rango un gasto confirmado de 30000 y un gasto pendiente de 45000
- **THEN** `GET /api/reports/transactions` devuelve solo el confirmado, y el total de `EXPENSE` es 30000

#### Scenario: Consulta de saldo con un pendiente
- **WHEN** el usuario tiene en el mes un gasto confirmado de 30000 y un gasto pendiente de 45000
- **THEN** `GET /api/reports/balance` devuelve `period.expense` 30000 y `allTime.expense` sin los 45000

#### Scenario: Usuario con solo pendientes
- **WHEN** el usuario no tiene movimientos confirmados y sí tiene pendientes
- **THEN** `GET /api/reports/balance` responde 200 con `period` y `allTime` en cero

#### Scenario: Gasto mensual con un pendiente
- **WHEN** el usuario tiene en el mes un gasto pendiente
- **THEN** `GET /api/monthly-spending` no lo suma al gasto del mes

#### Scenario: Ingreso pendiente en el gasto mensual
- **WHEN** el usuario tiene en el mes un ingreso pendiente
- **THEN** `GET /api/monthly-spending` no lo suma a `totalIncome`

#### Scenario: El pendiente cuenta al aprobarse
- **WHEN** el usuario aprueba el gasto pendiente de 45000
- **THEN** `GET /api/reports/transactions` lo devuelve y `GET /api/reports/balance` suma 45000 a `period.expense`

### Requirement: Los programados no cuentan en los reportes
Un movimiento con `occurredAt` posterior al momento actual SHALL quedar fuera de los totales de
`GET /api/reports/transactions` (aunque aparezca en su lista), de `GET /api/reports/balance` (el
neto del rango, el histórico y el `currentBalance` de cada cuenta) y de `GET /api/monthly-spending`
(el gasto y el ingreso del mes, y el avance contra la meta). Cuando llega su fecha SHALL contar en
los tres sin que nadie lo modifique.

#### Scenario: Consulta de saldo con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/reports/balance` del mes siguiente trae `period.expense` 0, y `allTime.expense` no lo incluye

#### Scenario: Gasto mensual con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/monthly-spending` de ese mes no lo suma al gasto del mes

#### Scenario: Ingreso programado en el gasto mensual
- **WHEN** la persona tiene un ingreso programado para el mes siguiente y ningún otro movimiento ni meta ese mes
- **THEN** `GET /api/monthly-spending` de ese mes devuelve un arreglo vacío

#### Scenario: El programado cuenta al llegar su fecha
- **WHEN** el usuario registra un gasto de 40000 con `occurredAt` unos segundos en el futuro y pide `GET /api/reports/balance` después de esa fecha
- **THEN** `period.expense` y `allTime.expense` ya incluyen los 40000

### Requirement: Montos en la moneda de la persona
Estos montos SHALL estar en la moneda base de la persona (`currencyCode`):
- `amountBase` de cada movimiento del reporte;
- los totales de `reports/transactions` y de `reports/balance`;
- `totalSpent` y `totalIncome` de `monthly-spending`.

Cada movimiento SHALL aportar su `amount` si su moneda es la de la persona. Si no, SHALL aportar su
equivalente en USD multiplicado por la tasa `USD→moneda de la persona` de su fecha local, elegida con
la regla de `tasas-de-cambio`, a 4 decimales. Cambiar la moneda de la persona MUST NOT requerir
reescribir movimientos.

#### Scenario: Persona en COP con movimientos en COP
- **WHEN** el usuario del escenario, con moneda COP, pide sus reportes
- **THEN** los totales son la suma de los `amount` de sus movimientos en COP, sin diferencias de redondeo

#### Scenario: Persona en USD con movimientos en COP
- **WHEN** una persona con moneda USD tiene en una cuenta COP un gasto de 41000 de hoy (tasa 4100) y uno de 39000 de hace 50 días (antes de la tasa más antigua, 3900), y pide `reports/transactions` de hace 50 días a hoy
- **THEN** la respuesta trae `currencyCode` USD, `amountBase` 10 en cada gasto y el total de gastos en 20

#### Scenario: Movimiento en otra moneda con la tasa de su fecha
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes una transferencia de 100 USD y la tasa `USD→COP` de esa fecha es 4100
- **THEN** su `amountBase` en el reporte es 410000

#### Scenario: Gasto mensual en la moneda de la persona
- **WHEN** la persona con moneda USD pide `monthly-spending` del mes en curso
- **THEN** `currencyCode` es USD y `totalSpent` incluye el gasto de hoy como 10

#### Scenario: Ingresos del gasto mensual en la moneda de la persona
- **WHEN** la persona con moneda USD tiene en su cuenta COP un ingreso de 82000 de hoy (tasa 4100) y otro de 39000 de hace 100 días (antes de la tasa más antigua, 3900)
- **THEN** `monthly-spending` trae `totalIncome` 20 en el mes en curso y 10 en el mes de hace 100 días
