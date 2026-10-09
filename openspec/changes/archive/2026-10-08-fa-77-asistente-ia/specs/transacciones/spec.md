## ADDED Requirements

### Requirement: Origen de un movimiento
Todo movimiento SHALL tener un origen: `WEB`, `TELEGRAM` o `IMPORT`. Los que entran por
`POST /api/transactions` SHALL tener origen `WEB`, y los que registra el asistente, `TELEGRAM`. Un
movimiento de origen `TELEGRAM` SHALL entrar `PENDING`. La respuesta de un movimiento SHALL incluir
el campo `origin` en el alta, la modificación, la aprobación y la lista de pendientes.

#### Scenario: El alta es WEB
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido
- **THEN** la respuesta es 201 y cada movimiento devuelve `"origin": "WEB"`

#### Scenario: El cliente no puede elegir el origen
- **WHEN** un elemento del lote trae `"origin": "TELEGRAM"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"` y `"status": "CONFIRMED"`

#### Scenario: El origen se conserva al aprobar
- **WHEN** el usuario aprueba un pendiente creado por el asistente
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "TELEGRAM"`
