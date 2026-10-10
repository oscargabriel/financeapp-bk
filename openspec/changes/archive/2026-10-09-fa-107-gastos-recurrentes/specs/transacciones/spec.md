## ADDED Requirements

### Requirement: Serie del movimiento en la respuesta
La respuesta de un movimiento SHALL incluir `recurrenceId`: el id de la serie de la que es ocurrencia,
o `null` si no pertenece a ninguna. SHALL ir en el alta, la modificación, la aprobación y la lista de
pendientes. El cliente no la envía: si llega en el cuerpo del alta o del parche, se ignora, y un
movimiento no se puede agregar a una serie ni sacar de ella.

#### Scenario: Movimiento suelto
- **WHEN** el usuario registra un gasto con `POST /api/transactions`
- **THEN** la respuesta trae `"recurrenceId": null`

#### Scenario: recurrenceId en el cuerpo
- **WHEN** el usuario envía un elemento del lote con un `recurrenceId`
- **THEN** la respuesta es 201 con `"recurrenceId": null`

#### Scenario: Ocurrencia modificada
- **WHEN** el usuario modifica con `PATCH /api/transactions/{id}` la descripción de una ocurrencia
- **THEN** la respuesta es 200 con el `recurrenceId` de su serie
