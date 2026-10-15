# Contrato del API — afiliaciones

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### Afiliaciones

Dos personas registradas se afilian para compartir gastos (FIN-65, epic FIN-9). Una envía una
**solicitud** a un correo, la otra la acepta o la rechaza, y cualquiera de las dos desvincula una
afiliación aceptada. Lo que cada persona y el usuario se deben sale por persona (FIN-67). Las ocho
rutas son **Bearer**.

| Método | Ruta | Respuesta | Quién |
|---|---|---|---|
| `GET` | `/api/affiliations` | 200 con la lista | — |
| `POST` | `/api/affiliations` | 201 con la relación | — |
| `POST` | `/api/affiliations/{id}/accept` | 200 con la relación | Quien la recibió, mientras sigue `REQUESTED` |
| `POST` | `/api/affiliations/{id}/reject` | 204 | Quien la recibió, mientras sigue `REQUESTED` |
| `POST` | `/api/affiliations/{id}/cancel` | 204 | Quien la envió, mientras sigue `REQUESTED` |
| `POST` | `/api/affiliations/{id}/unlink` | 204 | Cualquiera de los dos, sobre una `ACCEPTED` |
| `GET` | `/api/affiliations/balances` | 200 con el saldo por persona | — |
| `GET` | `/api/affiliations/{id}/shares` | 200 con las partes entre los dos | Cualquiera de los dos, sobre una `ACCEPTED` o `UNLINKED` |

Las cuatro acciones van sin cuerpo.

**Estados.** `REQUESTED` y `ACCEPTED` son las abiertas. `REJECTED`, `CANCELLED` y `UNLINKED` la cierran:
la relación deja de salir en el listado, y entre las mismas personas se puede enviar una solicitud
nueva, en cualquier sentido. La nueva es otra relación, con otro `id`.

**El API no revela qué correos tienen cuenta.** Una solicitud a un correo sin usuario —o de un usuario
desactivado— responde igual que a uno registrado: 201 con la misma forma. Nadie la recibe, aunque la
persona se registre después, y quien la envió la puede cancelar. Por lo mismo, una solicitud enviada
que no está aceptada nunca trae el nombre de la otra persona.

**Desvincular** solo cierra la afiliación: no cambia movimientos ni partes compartidas entre los dos.
Compartir con una persona exige una afiliación `ACCEPTED` (ver [Movimientos compartidos](compartidos.md#movimientos-compartidos)).

#### `GET /api/affiliations`

**200 OK** con las relaciones abiertas en las que participa el usuario, enviadas o recibidas, de la
más reciente a la más antigua por `createdAt`. Sin relaciones, `[]`.

```json
[
  {
    "id": "0199e1a2-3c4d-7e5f-8a9b-0c1d2e3f4a5b",
    "status": "REQUESTED",
    "direction": "RECEIVED",
    "person": { "email": "laura@correo.com", "firstName": "Laura", "lastName": "Pérez" },
    "createdAt": "2026-10-13T15:00:00Z",
    "updatedAt": "2026-10-13T15:00:00Z"
  }
]
```

- `direction` es `SENT` si la envió el usuario y `RECEIVED` si la recibió.
- `person` es la otra persona:

| Relación | `person.email` | `firstName` y `lastName` |
|---|---|---|
| Enviada, `REQUESTED` | El correo escrito, normalizado | `null`, tenga o no cuenta |
| Recibida, `REQUESTED` | El correo actual de quien la envió | Los de quien la envió (`lastName` puede ser `null`) |
| `ACCEPTED`, en cualquier sentido | El correo actual de la otra persona | Los de la otra persona |

#### `POST /api/affiliations`

```json
{ "email": "laura@correo.com" }
```

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `email` | string | sí | Formato de correo, hasta 255 caracteres. Se normaliza como en el registro: sin espacios alrededor y en minúsculas |

**201 Created** con la relación, con `"status": "REQUESTED"`, `"direction": "SENT"` y `person` con el
correo normalizado y el nombre en `null`.

#### `POST /api/affiliations/{id}/accept`

**200 OK** con la relación, ya `ACCEPTED` y con el nombre de quien la envió.

#### `GET /api/affiliations/balances`

**200 OK** con lo que el usuario y cada persona se deben, en las [partes](compartidos.md#gasto-compartido) que
siguen pendientes. Una entrada por persona, de la afiliación más reciente a la más antigua por su
fecha de creación. Sin personas, `[]`.

```json
[
  {
    "affiliationId": "0199e1a2-3c4d-7e5f-8a9b-0c1d2e3f4a5b",
    "status": "ACCEPTED",
    "person": { "email": "laura@correo.com", "firstName": "Laura", "lastName": "Pérez" },
    "balances": [
      { "currencyCode": "COP", "owedToMe": 30000, "iOwe": 10000, "net": 20000 },
      { "currencyCode": "USD", "owedToMe": 0, "iOwe": 20, "net": -20 }
    ]
  }
]
```

- **Quién sale:** toda persona con una afiliación `ACCEPTED`, aunque no se deban nada, y toda persona
  desvinculada con la que quede alguna parte pendiente, con `"status": "UNLINKED"`. Una desvinculada
  sin nada pendiente no sale.
- `affiliationId` es la afiliación `ACCEPTED` con esa persona, o la desvinculada más reciente si no
  hay. Sirve para pedir el detalle. Una persona sale una sola vez, aunque entre los dos haya varias
  afiliaciones a lo largo del tiempo.
- `person` trae el correo y el nombre actuales de la otra persona.
- `balances` trae una entrada por moneda con partes pendientes, ordenadas por código, **sin
  convertir entre monedas**:

| Campo | Qué es |
|---|---|
| `owedToMe` | Suma de las partes pendientes que la persona le debe al usuario: él pagó el gasto |
| `iOwe` | Suma de las partes pendientes que el usuario le debe a ella |
| `net` | `owedToMe − iOwe`. Positivo, la persona le debe; negativo, el usuario le debe; cero, quedan a mano en esa moneda |

`balances: []` es estar **a paz y salvo**. Las partes pagadas, rechazadas o anuladas no cuentan, y las
transferencias compartidas tampoco, porque no generan deuda. Solo cuentan las partes entre el usuario y
esa persona: las que ella tenga con terceros no suman, aunque el usuario esté afiliado con ellos.

#### `GET /api/affiliations/{id}/shares`

**200 OK** con las partes entre el usuario y la otra persona de esa afiliación, en cualquier estado,
de la más reciente a la más antigua por `occurredAt`. Salen todas las partes entre los dos, sin
importar con cuál de sus afiliaciones se crearon. Sin partes, `[]`.

```json
[
  {
    "id": "0199b2e0-...",
    "description": "Cena",
    "occurredAt": "2026-10-13T01:30:00Z",
    "amount": 30000,
    "currencyCode": "COP",
    "status": "PAID",
    "resolvedAt": "2026-10-14T14:00:00Z",
    "direction": "OWED_TO_ME"
  }
]
```

| Campo | Qué es |
|---|---|
| `description`, `occurredAt` | La descripción y la fecha con que se compartió el gasto. Se conservan aunque el gasto se haya modificado o borrado |
| `amount`, `currencyCode` | Lo que se debe, en la moneda en que se registró el gasto |
| `status` | `PENDING`, `PAID`, `REJECTED` o `VOIDED` (ver [Gasto compartido](compartidos.md#gasto-compartido)). Los dos ven el mismo estado: una parte rechazada sale `REJECTED` también para quien pagó |
| `resolvedAt` | Cuándo dejó de estar pendiente. `null` mientras sigue `PENDING` |
| `direction` | `OWED_TO_ME` si el usuario pagó el gasto, `I_OWE` si lo pagó la otra persona |

El `id` puede ser cualquier afiliación `ACCEPTED` o `UNLINKED` del usuario con esa persona. Una
solicitud que nunca se aceptó (`REQUESTED`, `REJECTED` o `CANCELLED`) responde 409.

#### Errores

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `email` | Solo al enviar: el correo falta, está en blanco, no tiene formato de correo o es el del propio usuario |
| 400 | `JSON_PARSING_ERROR` | `body` | Solo al enviar: el cuerpo no es JSON válido |
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 401 | `UNAUTHENTICATED` | `authorization` | Sin token, token inválido o la credencial Basic |
| 404 | `NOT_FOUND` | `id` | La relación no existe o el usuario no participa en ella: la respuesta es la misma |
| 409 | `DUPLICATE_RESOURCE` | `email` | Solo al enviar: ya hay con esa persona una solicitud abierta, en cualquier sentido, o una afiliación aceptada. No se crea nada |
| 409 | `INVALID_STATE` | `status` | El usuario participa, pero la acción no corresponde a su papel o al estado: aceptar la que envió, cancelar la que recibió, aceptar una ya aceptada, desvincular una sin aceptar, o pedir el detalle de partes de una que nunca se aceptó. No cambia nada |

El 409 `DUPLICATE_RESOURCE` solo mira las relaciones que el usuario ya ve en su listado, así que
tampoco revela si el correo tiene cuenta.
