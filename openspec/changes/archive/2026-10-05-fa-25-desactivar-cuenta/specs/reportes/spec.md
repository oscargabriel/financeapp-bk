# Spec Delta

## ADDED Requirements

### Requirement: Movimientos de una cuenta borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una cuenta borrada, con
su `accountId`, y el filtro `accountId` con el id de esa cuenta SHALL seguir encontrándolos.

#### Scenario: Gasto de una cuenta borrada
- **WHEN** el usuario borra `Caja chica`, que tiene un gasto de hoy, y pide el reporte de hoy filtrado por esa cuenta
- **THEN** el gasto aparece con el `accountId` de la cuenta borrada y cuenta en los totales
