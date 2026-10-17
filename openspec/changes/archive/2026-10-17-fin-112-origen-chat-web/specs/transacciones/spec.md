# Spec Delta

## MODIFIED Requirements

### Requirement: Origen de un movimiento
Todo movimiento SHALL tener un origen: `WEB`, `TELEGRAM`, `IMPORT`, `EMAIL` o `WEB_CHAT`. Los que
entran por `POST /api/transactions` SHALL tener origen `WEB`, y los que registra el asistente por
`POST /api/assistant/messages`, `WEB_CHAT`. `TELEGRAM` queda para lo que registre el bot. Ningún
endpoint SHALL producir `TELEGRAM`, `IMPORT` ni `EMAIL`, reservado este para la ingesta desde correo. El
origen SHALL NOT decidir el estado: solo dice por dónde entró el movimiento. La respuesta de un
movimiento SHALL incluir el campo `origin` en el alta, la modificación, la aprobación y la lista de
pendientes.

#### Scenario: El alta es WEB
- **WHEN** el usuario envía `POST /api/transactions` con un lote válido
- **THEN** la respuesta es 201 y cada movimiento devuelve `"origin": "WEB"`

#### Scenario: El cliente no puede elegir el origen
- **WHEN** un elemento del lote trae `"origin": "TELEGRAM"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"` y `"status": "CONFIRMED"`

#### Scenario: El cliente no puede declarar un correo
- **WHEN** un elemento del lote trae `"origin": "EMAIL"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"`

#### Scenario: El origen se conserva al aprobar
- **WHEN** el usuario aprueba un pendiente creado por el asistente desde `POST /api/assistant/messages`
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "WEB_CHAT"`

#### Scenario: El cliente no puede declarar el chat web
- **WHEN** un elemento del lote de `POST /api/transactions` trae `"origin": "WEB_CHAT"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"origin": "WEB"`

#### Scenario: Un pendiente que llegó por Telegram
- **WHEN** el usuario tiene un gasto pendiente guardado con origen `TELEGRAM` y lo aprueba
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "TELEGRAM"`

#### Scenario: Un pendiente WEB
- **WHEN** el usuario registra con `POST /api/transactions` un gasto en otra moneda que la de su cuenta
- **THEN** la respuesta es 201 con `"status": "PENDING"` y `"origin": "WEB"`

#### Scenario: Un pendiente que llegó por correo
- **WHEN** el usuario tiene un gasto pendiente guardado con origen `EMAIL` y pide `GET /api/transactions/pending`
- **THEN** la respuesta es 200 y ese gasto sale con `"status": "PENDING"` y `"origin": "EMAIL"`

#### Scenario: Aprobar un pendiente que llegó por correo
- **WHEN** el usuario aprueba uno a uno, o con el grupo, un pendiente con origen `EMAIL`
- **THEN** la respuesta es 200 con `"status": "CONFIRMED"` y `"origin": "EMAIL"`, y el mismo `groupId` que tenía

#### Scenario: Rechazar un pendiente que llegó por correo
- **WHEN** el usuario rechaza uno a uno, con el grupo o en lote un pendiente con origen `EMAIL`
- **THEN** la respuesta es 204 y deja de salir en `GET /api/transactions/pending`, igual que con cualquier otro origen
