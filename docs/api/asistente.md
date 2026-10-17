# Contrato del API — asistente

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `POST /api/assistant/messages`

Recibe un mensaje en lenguaje natural ("gasté 20 mil en almuerzo con la Nequi", "¿cuánto gasté este
mes?") y lo resuelve con un modelo de IA (FA-77). **Bearer.** Registra uno o varios movimientos,
consulta los movimientos o consulta el saldo, siempre del usuario del token. El mensaje puede ser un
texto, una nota de voz o la foto de un recibo (FIN-57).

**Cuerpo — solo texto**, en `application/json`:

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `data` | string | sí | El mensaje. No en blanco, hasta 1000 caracteres |

```json
{ "data": "gaste 20000 en almuerzo con la Nequi" }
```

**Cuerpo — con un audio o una imagen**, en `multipart/form-data`, por la misma ruta:

| Parte | Obligatoria | Reglas |
|---|---|---|
| `file` | no, si viene `data` | Un solo archivo, de 1 byte a 5 MB (5 242 880 bytes). Su `Content-Type` tiene que ser uno de los admitidos y coincidir con el contenido |
| `data` | no, si viene `file` | El texto que acompaña al archivo, hasta 1000 caracteres. En blanco cuenta como ausente |

| Tipo | `Content-Type` admitidos |
|---|---|
| Imagen | `image/jpeg`, `image/png`, `image/webp`, `image/heic`, `image/heif` |
| Audio | `audio/ogg`, `audio/mpeg` (MP3), `audio/wav`, `audio/aac`, `audio/flac` |

```
POST /api/assistant/messages
Authorization: Bearer <token>
Content-Type: multipart/form-data; boundary=b

--b
Content-Disposition: form-data; name="file"; filename="recibo.jpg"
Content-Type: image/jpeg

<bytes de la imagen>
--b
Content-Disposition: form-data; name="data"

con la Nequi
--b--
```

Con `curl`: `curl -H "Authorization: Bearer $TOKEN" -F "file=@recibo.jpg;type=image/jpeg" -F "data=con la Nequi" .../api/assistant/messages`.

- **La nota de voz** vale como si el usuario hubiera escrito lo que dice.
- **De la foto de un recibo, factura o voucher** se toman el monto total, la fecha y una descripción
  corta. **La cuenta nunca sale de la imagen**: va en `data` o en el audio, y si no viene la
  respuesta es `NEEDS_CLARIFICATION` pidiéndola. Una imagen que no es un comprobante es
  `UNSUPPORTED`.
- Con imagen y texto se usan los dos ("con la Nequi" junto a la foto).
- **El archivo no se guarda**: ni en la base ni en disco, y no aparece en los logs. Se manda al
  modelo dentro de la misma petición y se descarta al responder. Lo registrado guarda los mismos
  campos que con un texto.
- Video, cualquier otro tipo, varios archivos o un audio grabado en un formato que no está en la
  tabla (como el `audio/webm` del navegador) son 400.

**200 OK** — también cuando el asistente no pudo hacer lo pedido: `intent` dice qué pasó.

```json
{
  "intent": "CREATE_TRANSACTION",
  "message": "Registre 2 movimientos: un gasto de 20.000 COP en Nequi (Restaurantes); un gasto de 15.000 COP en Nequi (Transporte). Confirmalos o descartalos.",
  "group": {
    "id": "0199a1d1-...",
    "transactions": [
      {
        "id": "0199a1d0-...",
        "type": "EXPENSE",
        "status": "PENDING",
        "amount": 20000,
        "currencyCode": "COP",
        "description": "Almuerzo",
        "occurredAt": "2026-10-10T17:30:12.345Z",
        "account": "Nequi",
        "destinationAccount": null,
        "category": "Restaurantes"
      },
      {
        "id": "0199a1d0-...",
        "type": "EXPENSE",
        "status": "PENDING",
        "amount": 15000,
        "currencyCode": "COP",
        "description": "Taxi",
        "occurredAt": "2026-10-10T17:30:12.345Z",
        "account": "Nequi",
        "destinationAccount": null,
        "category": "Transporte"
      }
    ]
  },
  "report": null,
  "balance": null
}
```

| `intent` | Qué hizo | Objeto que trae |
|---|---|---|
| `CREATE_TRANSACTION` | Registró uno o varios gastos, ingresos o transferencias **sin confirmar**, en un grupo | `group`: el `id` del grupo y `transactions`, lo registrado en el orden del mensaje. Cada elemento trae solo `id`, `type`, `status`, `amount`, `currencyCode`, `description`, `occurredAt` y los nombres `account`, `destinationAccount` y `category` |
| `LIST_TRANSACTIONS` | Consultó los movimientos | `report`, con la forma de [`GET /api/reports/transactions`](reportes.md#get-apireportstransactions) |
| `GET_BALANCE` | Consultó ingresos, gastos y cuentas | `balance`, con la forma de [`GET /api/reports/balance`](reportes.md#get-apireportsbalance) |
| `NEEDS_CLARIFICATION` | Nada: le falta o no reconoce un dato, y `message` dice cuál | Ninguno |
| `UNSUPPORTED` | Nada: lo pedido no es ninguna de las tres acciones | Ninguno |

Solo viene el objeto del `intent`; los otros dos salen en `null`. `message` es un texto para
mostrar al usuario, escrito siempre por el servidor y nunca por el modelo. Como toda `description`,
no se compara: para decidir se usa `intent`.

**A tener en cuenta**

- **Lo que registra queda sin confirmar, en un grupo** (FIN-58). Entra con `"status": "PENDING"` y
  `"origin": "WEB_CHAT"`, y no mueve saldos hasta confirmarse. Se confirma o se descarta todo junto con
  `POST /api/transactions/groups/{group.id}/approve` o `/reject`, y lo que no se confirme queda en
  `GET /api/transactions/pending`, donde se aprueba o se rechaza uno a uno (ver
  [Movimientos pendientes](transacciones.md#movimientos-pendientes)). `message` pide confirmar o descartar.
- **El origen dice el canal** (FIN-112): lo que entra por este endpoint, en JSON o en multipart, es
  `WEB_CHAT`; lo que registre el bot de Telegram (FIN-61) será `TELEGRAM`
  (ver [`origin`](transacciones.md#post-apitransactions)).
- **Varios movimientos en un mensaje** ("almuerzo 20 mil y taxi 15 mil con la Nequi") se registran
  todos o ninguno: si uno no se puede resolver, es `NEEDS_CLARIFICATION`, `message` dice cuál
  ("Movimiento 2: …") y no se crea nada. Si el mensaje pide registrar y además consultar, solo se
  registra.
- **Los nombres se buscan exactos** entre las cuentas activas y las categorías del usuario, sin
  distinguir mayúsculas ni tildes ("nequi" encuentra "Nequi", pero "tarjeta" no encuentra
  "Tarjeta Débito"). Un nombre que no está, o que coincide con dos, es `NEEDS_CLARIFICATION`, y
  `message` lista los nombres válidos. Una cuenta desactivada no se encuentra.
- **Fechas.** Un movimiento sin fecha, o con la de hoy, queda en el instante de la petición; con otro
  día, a las 12:00 de ese día. Una consulta sin rango usa el mes en curso; con una sola de las dos
  fechas es `NEEDS_CLARIFICATION`.
- Lo que el alta o los reportes rechazarían (una cuenta en otra moneda, una categoría que no aplica
  al tipo, un monto inválido) no es un 400: es `NEEDS_CLARIFICATION`, con los motivos en `message`.
- Cada mensaje es independiente: el asistente no recuerda los anteriores.

**Errores**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `data` | Falta, está en blanco o pasa de 1000 caracteres. En multipart: no vienen ni `data` ni `file`, `data` pasa de 1000 caracteres o viene dos veces |
| 400 | `VALIDATION_ERROR` | `file` | Vacío, por encima de 5 MB, de un tipo que no está en la tabla, con un contenido que no corresponde a su `Content-Type`, o más de un archivo |
| 400 | `VALIDATION_ERROR` | el nombre de la parte | Una parte que no es `data` ni `file` |
| 401 | `UNAUTHENTICATED` | `authorization` | Sin token, token inválido o la credencial Basic |
| 415 | `VALIDATION_ERROR` | `request` | El cuerpo no es `application/json` ni `multipart/form-data` |
| 502 | `EXTERNAL_SERVICE_ERROR` | `server` | El modelo falló o no respondió a tiempo. No se registró nada: se puede reintentar |
