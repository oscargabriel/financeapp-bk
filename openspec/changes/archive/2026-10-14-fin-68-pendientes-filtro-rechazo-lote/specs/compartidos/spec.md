## MODIFIED Requirements

### Requirement: Rechazar una parte
Rechazar el gasto pendiente de una parte SHALL dejar la parte `REJECTED`, borrar el pendiente y
responder 204. La parte SHALL dejar de ser deuda. El gasto de quien pagó SHALL NOT cambiar.

#### Scenario: C rechaza su parte
- **WHEN** C rechaza su pendiente de 30000
- **THEN** la respuesta es 204, el pendiente deja de salir en su lista, la parte sale `REJECTED` en el gasto de A, y el saldo y el monto del gasto de A no cambian

#### Scenario: Descartar el grupo de una parte
- **WHEN** C descarta con `POST /api/transactions/groups/{groupId}/reject` el grupo de su pendiente compartido
- **THEN** la respuesta es 204 y la parte queda `REJECTED`, igual que al rechazarlo uno a uno

#### Scenario: Rechazar partes en lote
- **WHEN** A rechaza con `POST /api/transactions/pending/reject` los pendientes de su parte de un gasto de B y de su parte de un gasto de C
- **THEN** la respuesta es 204, las dos partes quedan `REJECTED`, dejan de ser deuda en `GET /api/affiliations/balances`, y los gastos de B y de C no cambian

#### Scenario: Un lote que falla no rechaza partes
- **WHEN** A rechaza en lote el pendiente de su parte de un gasto de B junto con un id que no existe
- **THEN** la respuesta es 404 y la parte sigue `PENDING`
