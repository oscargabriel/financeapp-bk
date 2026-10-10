## ADDED Requirements

### Requirement: Cuota del movimiento en la respuesta
La respuesta de un movimiento SHALL incluir `installment`: `{purchaseId, number, count}` si es una cuota
de una compra en cuotas —el id de la compra, el número de la cuota y el `installmentCount` de la
compra—, o `null` si no lo es. SHALL ir en el alta, la modificación, la aprobación y la lista de
pendientes. El cliente no lo envía: si llega en el cuerpo del alta o del parche, se ignora, y un
movimiento no se puede agregar a una compra ni sacar de ella.

#### Scenario: Movimiento suelto
- **WHEN** el usuario registra un gasto con `POST /api/transactions`
- **THEN** la respuesta trae `"installment": null`

#### Scenario: installment en el cuerpo
- **WHEN** el usuario envía un elemento del lote con un `installment`
- **THEN** la respuesta es 201 con `"installment": null`

#### Scenario: Cuota modificada
- **WHEN** el usuario modifica con `PATCH /api/transactions/{id}` la descripción de la cuota 2 de una compra a 3 cuotas
- **THEN** la respuesta es 200 con `installment` `{"purchaseId": <la compra>, "number": 2, "count": 3}`
