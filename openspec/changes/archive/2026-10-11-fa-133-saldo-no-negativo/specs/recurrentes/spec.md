# Spec Delta

## ADDED Requirements

### Requirement: Las series no validan el saldo
`POST /api/recurrences` y `PATCH /api/recurrences/{id}` SHALL aceptarse aunque sus ocurrencias con
fecha ya llegada dejen una cuenta no `CREDIT` en negativo. Eso ocurre con un `startDate` pasado o
con un `scope: ALL` que sube el monto o cambia la cuenta. Es una excepción provisional a la regla del
saldo no negativo de las cuentas, pendiente de análisis.

#### Scenario: Serie de gastos con inicio pasado
- **WHEN** el usuario crea una serie mensual de gastos de 60000 sobre la cuenta `CASH` `Bolsillo`, con saldo 100000 y `startDate` hace dos meses
- **THEN** la respuesta es 201 y el `currentBalance` de `Bolsillo` queda en -80000
