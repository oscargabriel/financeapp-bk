# Spec Delta

## ADDED Requirements

### Requirement: Aprobar un pendiente con una cuenta desactivada
`POST /api/transactions/{id}/approve` sobre un pendiente propio cuya cuenta origen o destino está
desactivada SHALL responder 409 con un error `INVALID_STATE` por cada cuenta desactivada: en
`accountId` si es el origen, en `destinationAccountId` si es el destino. El pendiente SHALL seguir
pendiente y ningún saldo SHALL cambiar. Rechazarlo SHALL seguir funcionando, y reactivada la cuenta,
SHALL aprobarse como cualquier otro.

#### Scenario: Cuenta origen desactivada
- **WHEN** el usuario aprueba un gasto pendiente cuya cuenta está desactivada
- **THEN** la respuesta es 409 con un único error `INVALID_STATE` en el campo `accountId`, el gasto sigue en `GET /api/transactions/pending` y el `currentBalance` de la cuenta no cambia

#### Scenario: Cuenta destino desactivada
- **WHEN** el usuario aprueba una transferencia pendiente cuya cuenta destino está desactivada y la de origen activa
- **THEN** la respuesta es 409 con un único error `INVALID_STATE` en el campo `destinationAccountId`, y ninguna de las dos cuentas cambia de saldo

#### Scenario: Las dos cuentas desactivadas
- **WHEN** el usuario aprueba una transferencia pendiente con las dos cuentas desactivadas
- **THEN** la respuesta es 409 con dos errores `INVALID_STATE`, uno en `accountId` y otro en `destinationAccountId`

#### Scenario: Rechazar con la cuenta desactivada
- **WHEN** el usuario rechaza un pendiente cuya cuenta está desactivada
- **THEN** la respuesta es 204 y el pendiente deja de salir en `GET /api/transactions/pending`

#### Scenario: Aprobar tras reactivar la cuenta
- **WHEN** el usuario reactiva la cuenta con `PATCH /api/accounts/{id}` y `{"isActive": true}`, y aprueba el pendiente
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y el saldo de la cuenta se mueve
