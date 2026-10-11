## ADDED Requirements

### Requirement: Montos en la moneda de la persona
Estos montos SHALL estar en la moneda base de la persona (`currencyCode`):
- `amountBase` de cada movimiento del reporte;
- los totales de `reports/transactions` y de `reports/balance`;
- `totalSpent` de `monthly-spending`.

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
