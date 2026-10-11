# Spec Delta

## ADDED Requirements

### Requirement: Movimientos de una categoría borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una categoría borrada,
con su `categoryId` y el `categoryName` que tenía al borrarse, tanto en `transactions` como en
`totalsByCategory`.

#### Scenario: Gasto de una categoría borrada
- **WHEN** el usuario borra `Huerta`, que tiene un gasto de hoy, y pide el reporte de hoy
- **THEN** el gasto aparece con el `categoryId` y el `categoryName` que tenía `Huerta` al borrarse, y `totalsByCategory` trae una entrada de esa categoría con ese gasto
