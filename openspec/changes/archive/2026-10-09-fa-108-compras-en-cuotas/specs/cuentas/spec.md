## MODIFIED Requirements

### Requirement: Saldo vigente de una cuenta
El `currentBalance` de una cuenta, en `GET`, `POST` y `PATCH /api/accounts` y en
`GET /api/reports/balance`, SHALL ser el saldo vigente: el saldo inicial más el efecto de los
movimientos confirmados cuyo `occurredAt` ya llegó. Los programados no lo mueven hasta su fecha.
`availableCredit` SHALL ser `creditLimit` más ese mismo saldo vigente, menos el capital de las cuotas de
compras en cuotas de la tarjeta cuyo `occurredAt` todavía no llega.

#### Scenario: Tarjeta con un gasto programado
- **WHEN** la tarjeta Mastercard, con cupo 3000000 y saldo vigente -200000, tiene además un gasto programado de 100000
- **THEN** `GET /api/accounts` la trae con `currentBalance` -200000 y `availableCredit` 2800000

#### Scenario: Compra en cuotas registrada hoy
- **WHEN** una tarjeta con cupo 5000000 y saldo vigente 0 registra una compra de 1200000 a 3 cuotas, todas futuras
- **THEN** `GET /api/accounts` la trae con `currentBalance` 0 y `availableCredit` 3800000, y `GET /api/reports/balance` con el mismo `availableCredit`

#### Scenario: Una cuota de la compra ya venció
- **WHEN** la misma compra tiene vencida la primera cuota, de 400000 de capital y 24000 de interés
- **THEN** la tarjeta trae `currentBalance` -424000 y `availableCredit` 3776000
