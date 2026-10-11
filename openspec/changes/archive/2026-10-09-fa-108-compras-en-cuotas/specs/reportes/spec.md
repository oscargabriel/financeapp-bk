## MODIFIED Requirements

### Requirement: Cada movimiento del reporte
Cada elemento de `transactions` SHALL traer `id`, `type`, `accountId`, `destinationAccountId`,
`categoryId`, `categoryName`, `amount`, `currencyCode`, `amountBase`, `description`, `notes`,
`occurredAt` en UTC, `scheduled`, `recurrenceId` e `installment`. `categoryId` y `categoryName` SHALL ir
en null en las transferencias, y `destinationAccountId` en null en gastos e ingresos. `scheduled` SHALL
ser `true` si `occurredAt` es posterior al momento en que se atiende la petición. `recurrenceId` SHALL
ser el id de la serie de la que el movimiento es ocurrencia, o null si no pertenece a ninguna.
`installment` SHALL ser `{purchaseId, number, count}` si el movimiento es una cuota de una compra en
cuotas, o null si no lo es.

#### Scenario: Gasto y transferencia en el mismo reporte
- **WHEN** el reporte incluye un gasto de Mercado y una transferencia
- **THEN** el gasto trae `categoryName` "Mercado" y `destinationAccountId` null, y la transferencia trae `categoryId` y `categoryName` null y su `destinationAccountId`

#### Scenario: Un movimiento programado en la lista
- **WHEN** el rango incluye un gasto con fecha posterior al momento actual
- **THEN** ese gasto aparece en `transactions` con `"scheduled": true`, y los demás con `"scheduled": false`

#### Scenario: Ocurrencias de una serie en la lista
- **WHEN** el rango incluye las ocurrencias de una serie y un gasto suelto
- **THEN** las ocurrencias traen el `recurrenceId` de su serie y el gasto suelto `"recurrenceId": null`

#### Scenario: Cuotas de una compra en la lista
- **WHEN** el rango incluye las cuotas 1 y 2 de una compra a 3 cuotas y un gasto suelto
- **THEN** las cuotas traen `installment` con el `purchaseId` de la compra, `number` 1 y 2 y `count` 3, y el gasto suelto `"installment": null`
