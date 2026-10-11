## ADDED Requirements

### Requirement: Saldo vigente de una cuenta
El `currentBalance` de una cuenta, en `GET`, `POST` y `PATCH /api/accounts` y en
`GET /api/reports/balance`, SHALL ser el saldo vigente: el saldo inicial más el efecto de los
movimientos confirmados cuyo `occurredAt` ya llegó. Los programados no lo mueven hasta su fecha.
`availableCredit` SHALL calcularse con ese mismo saldo vigente.

#### Scenario: Tarjeta con un gasto programado
- **WHEN** la tarjeta Mastercard, con cupo 3000000 y saldo vigente -200000, tiene además un gasto programado de 100000
- **THEN** `GET /api/accounts` la trae con `currentBalance` -200000 y `availableCredit` 2800000
