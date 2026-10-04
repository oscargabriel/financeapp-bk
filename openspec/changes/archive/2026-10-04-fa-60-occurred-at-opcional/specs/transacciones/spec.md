# Spec Delta

## Purpose

Alta de movimientos (gastos, ingresos y transferencias) del usuario autenticado a través de
`POST /api/transactions`, que recibe un lote de uno o más elementos.

## ADDED Requirements

### Requirement: Fecha del movimiento por defecto
Cuando un elemento del lote llega sin `occurredAt` (ausente, `null`, vacío o solo espacios), el
sistema SHALL guardarlo con el instante en que atiende la petición. Todos los elementos sin fecha de
un mismo lote SHALL recibir el mismo instante. La respuesta SHALL devolver ese instante en UTC.

#### Scenario: Elemento sin occurredAt
- **WHEN** el usuario envía `POST /api/transactions` con un elemento válido que no trae `occurredAt`
- **THEN** la respuesta es 201 y el movimiento devuelve un `occurredAt` en UTC igual al instante en que se atendió la petición

#### Scenario: occurredAt nulo, vacío o en blanco
- **WHEN** un elemento trae `"occurredAt": null`, `"occurredAt": ""` o `"occurredAt": "   "`
- **THEN** se trata igual que un elemento sin `occurredAt`: 201 y el instante de la petición

#### Scenario: Varios elementos sin fecha en el mismo lote
- **WHEN** un lote trae tres elementos sin `occurredAt`
- **THEN** los tres movimientos devuelven exactamente el mismo `occurredAt`

#### Scenario: Lote mixto
- **WHEN** un lote trae un elemento con `"occurredAt": "2026-09-20T10:15:00-05:00"` y otro sin fecha
- **THEN** el primero devuelve `2026-09-20T15:15:00Z` y el segundo el instante de la petición

### Requirement: Fecha explícita con offset
Cuando un elemento trae `occurredAt` con contenido, el sistema SHALL exigir una fecha ISO-8601 con
offset y SHALL guardar ese instante, devolviéndolo en UTC.

#### Scenario: Fecha con offset
- **WHEN** un elemento trae `"occurredAt": "2026-09-20T10:15:00-05:00"`
- **THEN** la respuesta es 201 y el movimiento devuelve `"occurredAt": "2026-09-20T15:15:00Z"`

#### Scenario: Fecha sin offset
- **WHEN** el elemento de índice 4 trae `"occurredAt": "2026-09-21T10:00:00"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[4].occurredAt` y no se guarda ningún elemento del lote

#### Scenario: Fecha mal formada
- **WHEN** el elemento de índice 0 trae `"occurredAt": "ayer"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].occurredAt` y no se guarda ningún elemento del lote

### Requirement: Alta autenticada con JWT
`POST /api/transactions` SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial
Basic compartida de `/auth/*` y `/status`, SHALL responder 401 sin guardar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `POST /api/transactions` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se envía `POST /api/transactions` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
