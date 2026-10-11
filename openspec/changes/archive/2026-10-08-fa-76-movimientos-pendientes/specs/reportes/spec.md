## ADDED Requirements

### Requirement: Los pendientes no cuentan en los reportes
Un movimiento `PENDING` SHALL quedar fuera de `GET /api/reports/transactions` (su lista, totales y
neto), de `GET /api/reports/balance` (el neto del rango y el histórico) y de
`GET /api/monthly-spending` (el gasto del mes y su avance contra la meta). Al aprobarse, SHALL
contar en los tres como cualquier movimiento confirmado.

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

#### Scenario: El pendiente cuenta al aprobarse
- **WHEN** el usuario aprueba el gasto pendiente de 45000
- **THEN** `GET /api/reports/transactions` lo devuelve y `GET /api/reports/balance` suma 45000 a `period.expense`
