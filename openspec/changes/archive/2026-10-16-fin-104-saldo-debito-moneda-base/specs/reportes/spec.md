# Spec Delta

## ADDED Requirements

### Requirement: Total de las cuentas débito en la consulta de saldo
`GET /api/reports/balance` SHALL traer `debitBalance`, en `currencyCode`: la suma de los saldos
vigentes de las cuentas `DEBIT` activas y no borradas del usuario, de cualquier moneda, convertidos
a su moneda base. Las cuentas de otros tipos, las desactivadas y las borradas MUST NOT sumar. Un
usuario sin cuentas débito SHALL recibir `debitBalance` 0.

#### Scenario: Usuario del escenario con una sola cuenta débito activa
- **WHEN** el usuario del escenario, con la cuenta débito Bancolombia activa, la débito Nequi desactivada, y Efectivo, Ahorros USD y Visa de otros tipos, pide la consulta de saldo
- **THEN** `debitBalance` es igual al `currentBalance` de Bancolombia

#### Scenario: Cuentas débito en dos monedas
- **WHEN** el usuario `saldo@financeapp.local`, con moneda COP, tiene la débito «Débito pesos» en COP y la débito «Débito dólares» en USD, y pide la consulta de saldo
- **THEN** `currencyCode` es COP y `debitBalance` es 1551000: 790000 de la cuenta en pesos más 761000 de la cuenta en dólares

#### Scenario: Usuario sin cuentas débito
- **WHEN** la persona en USD, que solo tiene una cuenta de efectivo, pide la consulta de saldo
- **THEN** `debitBalance` es 0

#### Scenario: Cuenta débito desactivada o de otro tipo
- **WHEN** `saldo@financeapp.local` tiene además la débito desactivada «Débito apagada» con 80000 y la de efectivo «Efectivo saldo» con 400000
- **THEN** ninguna de las dos está en `debitBalance` 1551000

### Requirement: Conversión del total débito a la moneda base
Cada aporte a `debitBalance` SHALL convertirse con la tasa de su propia fecha, elegida con la regla de `tasas-de-cambio`, nunca con la de hoy:
- el saldo inicial de la cuenta, con la tasa de la fecha local en que se creó;
- cada movimiento confirmado y ya ocurrido, con la regla de *Montos en la moneda de la persona*;
- lo que entra por una transferencia en otra moneda, su `destinationAmount` con la tasa de su fecha.

Lo que ya está en la moneda base MUST sumar su monto tal cual.

#### Scenario: Saldo inicial con la tasa del día en que se creó la cuenta
- **WHEN** «Débito dólares» se creó hace 50 días con saldo inicial 100 USD, la tasa `USD→COP` más antigua es 3900 (de hace 45 días) y la de hoy 4100
- **THEN** su saldo inicial aporta 390000 a `debitBalance`, no 410000

#### Scenario: Movimiento en otra moneda con la tasa de su fecha
- **WHEN** «Débito dólares» tiene un gasto de 10 USD de hace 40 días, cuando regía la tasa 3900
- **THEN** ese gasto resta 39000 de `debitBalance`

#### Scenario: Transferencia entre cuentas débito de monedas distintas
- **WHEN** hoy sale una transferencia de 410000 COP de «Débito pesos» que entra como 100 USD a «Débito dólares», con la tasa de hoy en 4100
- **THEN** resta 410000 de la cuenta en pesos, suma 410000 a la cuenta en dólares: su efecto neto sobre `debitBalance` es 0

#### Scenario: Montos en la moneda base sin pasar por el dólar
- **WHEN** «Débito pesos» tiene saldo inicial 1000000 COP, un ingreso de 500000, un gasto de 200000, la transferencia a «Débito dólares» y una de 100000 a «Efectivo saldo», todo en COP
- **THEN** la cuenta aporta exactamente 790000, sin diferencias de redondeo

#### Scenario: Pendientes y programados no cuentan
- **WHEN** «Débito pesos» tiene además un gasto pendiente de 50000 y un gasto programado de 70000 para el mes siguiente
- **THEN** `debitBalance` sigue en 1551000

#### Scenario: Después de cambiar la moneda base
- **WHEN** `saldo@financeapp.local` cambia su moneda base a USD desde `PATCH /api/users/me` y pide la consulta de saldo
- **THEN** la respuesta trae `currencyCode` USD y `debitBalance` 382.6829, y los movimientos y las cuentas conservan sus montos guardados

## MODIFIED Requirements

### Requirement: Cuentas en la consulta de saldo
`GET /api/reports/balance` SHALL traer en `accounts` las cuentas activas y no borradas del usuario,
ordenadas por nombre, cada una con `id`, `name`, `type`, `currencyCode`, `currentBalance`,
`creditLimit` y `availableCredit`. El `currentBalance` de cada cuenta SHALL ir en la moneda de la
cuenta, sin convertir; el único total entre cuentas es `debitBalance`. En una cuenta que no es
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

#### Scenario: Cuenta débito en otra moneda sin convertir
- **WHEN** `saldo@financeapp.local` pide la consulta de saldo
- **THEN** «Débito dólares» trae `currencyCode` USD y su `currentBalance` en dólares, aunque su aporte a `debitBalance` vaya en COP
