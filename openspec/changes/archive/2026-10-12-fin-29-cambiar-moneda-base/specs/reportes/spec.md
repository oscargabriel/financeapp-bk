# Spec Delta

## MODIFIED Requirements

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

#### Scenario: Después de cambiar la moneda base
- **WHEN** una persona con moneda COP cambia su moneda base a EUR desde `PATCH /api/users/me` y pide `reports/balance`
- **THEN** la respuesta trae `currencyCode` EUR, y sus movimientos conservan `amount`, `amount_base` y `exchange_rate`

## ADDED Requirements

### Requirement: Metas en la moneda de la persona
`budgetAmount`, `remaining` y `percentUsed` de `GET /api/monthly-spending` SHALL calcularse con la
meta en la moneda base de la persona. Una meta en esa moneda SHALL aportar su monto tal cual; una en
otra SHALL convertirse con las tasas `USD→X` del primer día de su mes, elegidas con la regla de
`tasas-de-cambio`, a 4 decimales. Los topes por categoría SHALL seguir la misma regla. Las metas
guardadas MUST NOT reescribirse.

#### Scenario: Meta en la moneda de la persona
- **WHEN** el usuario del escenario, con moneda COP y meta global de 2000000 COP en el mes en curso, pide `GET /api/monthly-spending`
- **THEN** el mes en curso trae `budgetAmount` 2000000

#### Scenario: Meta en otra moneda
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes siguiente una meta global de 500 USD y ningún movimiento, y la tasa `USD→COP` vigente el primer día de ese mes es 4100
- **THEN** ese mes trae `budgetAmount` 2050000, `remaining` 2050000 y `percentUsed` 0

#### Scenario: Tope por categoría en otra moneda
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes en curso un tope de 100 USD en Viajes y la tasa `USD→COP` vigente el primer día del mes es 4100
- **THEN** el desglose por categoría del mes trae para Viajes un tope de 410000
