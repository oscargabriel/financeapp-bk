# Contrato del API — transacciones

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `POST /api/transactions`

Registra un **lote** de movimientos del usuario del token. **Bearer.** Para uno solo, se manda un
arreglo de un elemento.

**Reglas del lote**

- Entre **1 y 500** elementos. Un arreglo vacío o de más de 500 es 400 en `body`, sin revisar los
  elementos. Cuidado también con el límite de 1 MB: notas largas en un lote grande lo alcanzan
  antes que los 500.
- **Todo o nada.** Si un solo elemento es inválido, no se guarda ninguno, y la respuesta trae los
  errores de **todos** los elementos, cada uno con su índice (`[0].amount`, `[3].categoryId`). El
  cliente puede marcarlos todos de una vez.
- **Saldo suficiente** (FA-133). Con todos los elementos válidos, el lote se rechaza entero con 409
  `INSUFFICIENT_FUNDS` si deja por debajo de cero una cuenta que no es de crédito (ver
  [Saldo no negativo](generalidades.md#saldo-no-negativo)). Cuenta el efecto neto del lote: un ingreso del mismo lote
  compensa un gasto. Sale un error por cuenta, en `[i].amount` del primer elemento que saca dinero de
  ella y la deja en negativo, en el orden del lote. Los programados y los que quedan pendientes no
  cuentan.
- **No es idempotente.** Reenviar el mismo lote crea los movimientos otra vez. Si la petición
  falla por red sin respuesta, revisa los saldos antes de reintentar. Un duplicado se borra con
  [`DELETE /api/transactions/{id}`](#delete-apitransactionsid).

**Elemento**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `type` | string | sí | `EXPENSE`, `INCOME` o `TRANSFER` |
| `accountId` | string (UUID) | sí | Cuenta del usuario y activa, en cualquier moneda. En una transferencia, la de origen |
| `destinationAccountId` | string (UUID) | solo `TRANSFER` | Cuenta del usuario, activa y distinta del origen. Prohibido en los otros tipos |
| `categoryId` | string (UUID) | `EXPENSE` / `INCOME` | Categoría activa del usuario y compatible con el tipo. **Prohibido en `TRANSFER`** |
| `amount` | number | sí | Mayor que cero, hasta 4 decimales. En la moneda de `currencyCode` |
| `currencyCode` | string | no | Moneda en que viene `amount`: código de 3 letras activo en el catálogo, sin importar mayúsculas. Ausente, la de la cuenta |
| `description` | string | sí | Hasta 255 caracteres. Se recortan los espacios de los extremos |
| `notes` | string | no | Hasta 1000 caracteres |
| `occurredAt` | string | no | ISO-8601 con offset. Sin fecha (ausente, `null` o vacío), el instante en que el servidor atiende la petición, el mismo para todo el lote |
| `destinationAmount` | number | no | Solo en una `TRANSFER` entre cuentas de monedas distintas: lo que entra al destino, en su moneda. Mayor que cero, hasta 4 decimales |
| `shares` | array | no | Solo en `EXPENSE` y `TRANSFER`: las personas afiliadas con quienes se comparte, cada una `{affiliationId, amount}`. Ausente, `null` o `[]` es un movimiento sin compartir. Ver [Movimientos compartidos](compartidos.md#movimientos-compartidos) |

Una cuenta que no existe y una de otro usuario responden lo mismo ("La cuenta no existe"). El API
no confirma que un id exista fuera de tus datos.

Una `TRANSFER` con `shares` no va a una cuenta propia: no lleva `destinationAccountId` ni
`destinationAmount`, y sí `categoryId` de una categoría de gasto. Se registra como `EXPENSE` (ver
[Transferencia compartida](compartidos.md#transferencia-compartida)).

**Monedas** (FA-51). El movimiento se registra siempre en la moneda de su cuenta (`currencyCode` de
la respuesta), que es lo que cobra el banco.

- Si `currencyCode` es otra moneda, `amount` se convierte a la de la cuenta con la tasa de
  [`GET /api/exchange-rates`](tasas-de-cambio.md#get-apiexchange-rates) de la fecha del movimiento, redondeado a los
  decimales de esa moneda (COP sin decimales, USD con 2). El movimiento entra **`PENDING`**, y
  `originalAmount` y `originalCurrencyCode` dicen lo que llegó. Se ajusta al cargo real con el PATCH
  y se aprueba como cualquier pendiente.
- En una transferencia entre cuentas de monedas distintas, con `destinationAmount` el destino recibe
  ese monto. Sin él, se calcula con la misma tasa y la transferencia entra `PENDING`.
- `destinationAmount` entre cuentas de la misma moneda, o en un gasto o un ingreso, es 400.
- Si una de las dos monedas no tiene ninguna tasa y el proveedor no responde, la respuesta es 502
  `EXTERNAL_SERVICE_ERROR` en `server` y no se guarda nada.

```json
[
  {
    "type": "EXPENSE",
    "accountId": "0199a1b3-...",
    "categoryId": "0199a1c0-...",
    "amount": 52300.50,
    "description": "Mercado de la semana",
    "occurredAt": "2026-09-20T10:15:00-05:00"
  },
  {
    "type": "TRANSFER",
    "accountId": "0199a1b3-...",
    "destinationAccountId": "0199a1b4-...",
    "amount": 100000,
    "description": "Ahorro del mes"
  }
]
```

**201 Created** — arreglo con los movimientos creados, en el mismo orden del lote:

```json
[
  {
    "id": "0199a1d0-...",
    "type": "EXPENSE",
    "accountId": "0199a1b3-...",
    "destinationAccountId": null,
    "categoryId": "0199a1c0-...",
    "amount": 52300.50,
    "currencyCode": "COP",
    "description": "Mercado de la semana",
    "notes": null,
    "occurredAt": "2026-09-20T15:15:00Z",
    "status": "CONFIRMED",
    "origin": "WEB",
    "scheduled": false,
    "recurrenceId": null,
    "installment": null,
    "destinationAmount": null,
    "originalAmount": null,
    "originalCurrencyCode": null,
    "groupId": null,
    "sharedBy": null,
    "sharedFrom": null,
    "shares": null
  }
]
```

`status` es `CONFIRMED` en todo lo que entra por aquí, salvo lo que se convierte de otra moneda; si el
cuerpo trae un `status`, se ignora. Un movimiento queda `PENDING` porque necesita que el usuario lo
confirme, sea cual sea su origen: aquí, lo convertido; desde el asistente, todo lo que registra; y lo
que otra persona comparte con el usuario (ver [Movimientos pendientes](#movimientos-pendientes)).

`sharedBy`, `sharedFrom` y `shares` dicen qué tiene de compartido el movimiento (ver
[Lo compartido en la respuesta](compartidos.md#lo-compartido-en-la-respuesta)). Van en toda respuesta de un
movimiento y en cada fila de `reports/transactions`, en `null` cuando no aplican. Los pone el
servidor: si llegan en el cuerpo, se ignoran.

`groupId` es el grupo de pendientes con que entró el movimiento: los que una misma petición deja
pendientes comparten uno (aquí, los convertidos de un mismo lote), y se confirman o se descartan juntos.
Es `null` si entró confirmado, y se conserva al confirmar. Va en toda respuesta de un movimiento: alta,
modificación, pendientes y aprobación. Lo pone el servidor: si el cuerpo trae un `groupId`, se ignora.

`destinationAmount`, `originalAmount` y `originalCurrencyCode` van en toda respuesta de un movimiento,
en `null` cuando no aplican: el primero solo en una transferencia entre monedas distintas, y los otros
dos solo si el movimiento se convirtió. El [reporte de movimientos](reportes.md#get-apireportstransactions) tiene
su propia forma: trae `destinationAmount`, pero no `originalAmount` ni `originalCurrencyCode`.

`origin` dice por dónde entró el movimiento, y no cambia después. No decide si entra pendiente:

| Valor | Quién lo pone |
|---|---|
| `WEB` | Este endpoint, las ocurrencias de una [serie](recurrentes.md#series-recurrentes) y las cuotas de una [compra en cuotas](cuotas.md#compras-en-cuotas) |
| `TELEGRAM` | El bot de Telegram (FIN-61). Hoy ningún endpoint lo produce |
| `IMPORT` | Reservado para una importación de archivos. Hoy ningún endpoint lo produce |
| `EMAIL` | Reservado para la ingesta desde correo (FIN-12). Hoy ningún endpoint lo produce: solo lo traen los datos de demo y de prueba |
| `WEB_CHAT` | El [asistente](asistente.md#post-apiassistantmessages), desde el chat de la web |

Va en las mismas respuestas que `status`. Lo pone el servidor: si el cuerpo trae un `origin`, se
ignora.

`scheduled` es `true` si `occurredAt` es posterior al momento en que el servidor responde: el
movimiento está **programado** (ver [Movimientos programados](#movimientos-programados)). Lo calcula
el servidor; si el cuerpo trae un `scheduled`, se ignora.

`recurrenceId` es el id de la serie de la que el movimiento es ocurrencia (ver
[Series recurrentes](recurrentes.md#series-recurrentes)), o `null` si no es de ninguna. Va en toda respuesta de un
movimiento: alta, modificación, pendientes y aprobación. Lo pone el servidor: si el cuerpo trae un
`recurrenceId`, se ignora, y un movimiento no se puede agregar a una serie ni sacar de ella.

`installment` dice si el movimiento es una cuota de una [compra en cuotas](cuotas.md#compras-en-cuotas):
`{"purchaseId": "…", "number": 3, "count": 12}` es la cuota 3 de 12 de esa compra. Es `null` si no es
una cuota. Va en las mismas respuestas que `recurrenceId` y, como él, se ignora si llega en el cuerpo.

**Efecto en los saldos.** La base los aplica en la misma transacción. Después del 201, un
`GET /accounts` ya los muestra, salvo los programados, que esperan a su fecha:

| Tipo | `accountId` | `destinationAccountId` |
|---|---|---|
| `EXPENSE` | resta `amount` | — |
| `INCOME` | suma `amount` | — |
| `TRANSFER` | resta `amount` | suma `destinationAmount`, o `amount` si es `null` |

**Ejemplo de 400:**

```json
{
  "errors": [
    { "code": "VALIDATION_ERROR", "description": "El monto debe ser mayor que cero: el signo lo da el tipo", "field": "[0].amount" },
    { "code": "VALIDATION_ERROR", "description": "Una transferencia no lleva categoria", "field": "[1].categoryId" }
  ]
}
```

Un `null` dentro del arreglo es un error de ese índice ("El elemento no puede ser nulo"), no del
lote completo.

### `PATCH /api/transactions/{id}`

Modifica un movimiento del usuario del token. **Bearer.** Se envían solo los campos que cambian.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `type` | string | `EXPENSE`, `INCOME` o `TRANSFER` |
| `accountId` | string (UUID) | Cuenta del usuario, activa y en la moneda del movimiento (`currencyCode`) |
| `destinationAccountId` | string (UUID) | Solo si el movimiento queda como `TRANSFER`. Cuenta del usuario, activa y distinta del origen |
| `categoryId` | string (UUID) | Solo si el movimiento queda como `EXPENSE` o `INCOME`. Categoría activa del usuario y compatible con el tipo |
| `amount` | number | Mayor que cero, hasta 4 decimales |
| `description` | string | Hasta 255 caracteres, no en blanco. Se recortan los espacios de los extremos |
| `occurredAt` | string | ISO-8601 con offset, no en blanco. A diferencia del alta, vacío no significa "ahora" |
| `destinationAmount` | number | Solo si el movimiento queda como `TRANSFER` entre cuentas de monedas distintas. Mayor que cero, hasta 4 decimales |

- **Ausente o `null` es "no cambia".** No hay forma de vaciar un campo enviándolo en `null`.
- Un parche sin ningún campo (`{}`, o todo en `null`) es 400 en `body`.
- `notes`, `currencyCode`, `originalAmount` y `originalCurrencyCode` **no se modifican**: si vienen en
  el cuerpo, se ignoran sin error. Ajustar `amount` de un movimiento convertido no cambia su original.
- `destinationAmount` (FA-51) sale de cómo queda la transferencia:
  - entre cuentas de la misma moneda, o si deja de ser transferencia, queda en `null`, y mandarlo es
    400;
  - entre monedas distintas, vale el del parche o, si no trae, el que tenía, mientras el destino
    siga en la misma moneda. Sin ninguno es 400 en `destinationAmount`.
- Las reglas del tipo se miran sobre **cómo queda** el movimiento, no sobre el parche:
  - Pasar a `TRANSFER` exige `destinationAccountId`, salvo que ya fuera transferencia. La categoría
    se vacía sola.
  - Pasar de `TRANSFER` a `EXPENSE` o `INCOME` exige `categoryId`. La cuenta destino se vacía sola.
  - Pasar entre `EXPENSE` e `INCOME` sin `categoryId` revisa que la categoría que ya tenía sirva
    para el tipo nuevo.
- Lo que el parche no trae no se vuelve a validar: una cuenta desactivada después del alta no
  impide corregir la descripción de un movimiento viejo.

```json
{
  "type": "TRANSFER",
  "destinationAccountId": "0199a1b4-...",
  "amount": 120000
}
```

**200 OK** — el movimiento completo como quedó, con la misma forma que un elemento de la respuesta
del alta.

**Efecto en los saldos.** La base revierte el movimiento anterior y aplica el nuevo en la misma
operación, también si cambian la cuenta, el tipo o el monto. Un pendiente se puede corregir antes
de aprobarlo: sigue `PENDING` y no mueve saldos. Cambiar `occurredAt` de una fecha futura a una pasada
aplica el movimiento, y al revés lo retira hasta que llegue; `scheduled` sale según la fecha nueva.

**Errores propios**

| HTTP | `field` | Cuándo |
|---|---|---|
| 400 | `id` | El id de la ruta no es un UUID |
| 400 | `body` | El parche no trae ningún campo |
| 400 | el campo | Formato inválido, o una regla del tipo resultante, sin índice (`amount`, `categoryId`) |
| 404 | `id` | El movimiento no existe o es de otro usuario: la respuesta es la misma |
| 409 | `amount` | `INSUFFICIENT_FUNDS`: el movimiento resultante bajaría por debajo de cero una cuenta que no es de crédito, un error por cuenta. Pasa al subir un gasto, cambiar de cuenta o de tipo, reducir un ingreso o una transferencia entrante, o pasar un ingreso a fecha futura. No cambia nada |
| 409 | `amount` / `type` | `INVALID_STATE`: el gasto tiene partes compartidas pendientes y el parche cambia su monto o su tipo. Lo demás sí se modifica (ver [Movimientos compartidos](compartidos.md#movimientos-compartidos)) |
| 409 | `status` | `INVALID_STATE`: es un pendiente que otra persona compartió con el usuario; solo se aprueba o se rechaza |

### `DELETE /api/transactions/{id}`

Borra un movimiento del usuario del token. **Bearer.** El borrado es físico: no hay papelera.

**204 No Content**, sin cuerpo. La base revierte su efecto en los saldos. Borrar un gasto compartido
anula sus partes pendientes, y los pendientes de esas partes desaparecen; las pagadas y las
rechazadas quedan como estaban.

| HTTP | `field` | Cuándo |
|---|---|---|
| 400 | `id` | El id de la ruta no es un UUID |
| 404 | `id` | El movimiento no existe, ya se borró, o es de otro usuario |
| 409 | `id` | `INSUFFICIENT_FUNDS`: borrar un ingreso o una transferencia ya gastados dejaría una cuenta que no es de crédito en negativo. Un gasto, un pendiente o un programado siempre se pueden borrar |
| 409 | `status` | `INVALID_STATE`: es un pendiente que otra persona compartió con el usuario; se rechaza con `POST /api/transactions/{id}/reject` |

### Movimientos pendientes

Un movimiento entra **pendiente** (`"status": "PENDING"`) cuando necesita que el usuario lo confirme,
sea cual sea su origen (FIN-58). Mientras tanto no mueve saldos ni cuenta en `monthly-spending`,
`reports/transactions` ni `reports/balance`. Hoy entran así:

- todo lo que registra [`POST /api/assistant/messages`](asistente.md#post-apiassistantmessages), con
  `"origin": "WEB_CHAT"`;
- lo que [`POST /api/transactions`](#post-apitransactions) convierte de otra moneda (FA-51), con
  `"origin": "WEB"`;
- lo que otra persona comparte con el usuario: su parte de un gasto, el ingreso de una transferencia o
  el pago de una parte (ver [Movimientos compartidos](compartidos.md#movimientos-compartidos)), con
  `"origin": "WEB"`, `sharedBy` con quien lo compartió, y **sin cuenta ni categoría**
  (`accountId` y `categoryId` en `null`).

**Todo pendiente tiene un `groupId`**: los que una misma petición dejó pendientes (los movimientos de
un mensaje al asistente, los convertidos de un lote) comparten grupo, y se confirman o se descartan
juntos con las dos rutas de grupo. También se pueden aprobar o rechazar uno a uno: las rutas de grupo
actúan sobre lo que siga pendiente. Cada pendiente compartido tiene un grupo propio.

Los seis endpoints son **Bearer**.

**`GET /api/transactions/pending`** — **200 OK** con los pendientes del usuario, del más reciente al
más antiguo por `occurredAt`, con la misma forma que un elemento de la respuesta del alta. Sin
pendientes, `[]`.

Con `?sharedBy=<correo>` devuelve solo los que compartió la persona de ese correo (FIN-68), en el
mismo orden: el correo es el que sale en `sharedBy.email`, el actual de esa persona, y se compara sin
espacios alrededor y sin distinguir mayúsculas. Sirve también para una persona desvinculada. Un correo
con el que no hay pendientes da `[]`, tenga o no cuenta. Si el correo está vacío, pasa de 255
caracteres o no tiene formato de correo: **400** `VALIDATION_ERROR` en `sharedBy`.

**`POST /api/transactions/{id}/approve`** — **200 OK** con el movimiento completo y
`"status": "CONFIRMED"`. Desde ese momento mueve los saldos como en el alta y cuenta en los reportes.
Un pendiente con cuenta se aprueba **sin cuerpo**. Uno sin cuenta (un pendiente compartido) exige la
cuenta y la categoría con que se registra:

```json
{ "accountId": "0199a1b3-...", "categoryId": "0199a1c0-..." }
```

| Campo | Tipo | Reglas |
|---|---|---|
| `accountId` | string (UUID) | Obligatorio si el pendiente no tiene cuenta, prohibido si la tiene. Cuenta del usuario y activa, en cualquier moneda |
| `categoryId` | string (UUID) | Obligatorio si el pendiente no tiene cuenta, prohibido si la tiene. Categoría activa del usuario y del tipo del pendiente: de gasto para un gasto, de ingreso para un ingreso |

Si la cuenta está en otra moneda que el pendiente, el monto se convierte a la moneda de la cuenta con
la tasa de la fecha del pendiente, redondeado a los decimales de esa moneda, y la respuesta trae
`originalAmount` y `originalCurrencyCode` con lo que llegó. A diferencia del alta, queda
`CONFIRMED`: si el banco cobró otra cosa, se corrige con `PATCH`.

**`POST /api/transactions/{id}/reject`** — sin cuerpo. **204 No Content.** El pendiente se borra y no
deja rastro.

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | No existe, ya se rechazó, o es de otro usuario: la respuesta es la misma |
| 409 | `INVALID_STATE` | `status` | Ya está confirmado. Para borrar un confirmado está `DELETE /api/transactions/{id}` |
| 409 | `INVALID_STATE` | `accountId` / `destinationAccountId` | Solo al aprobar: la cuenta de ese campo está desactivada, un error por cuenta. Sigue pendiente y ningún saldo cambia; se aprueba después de reactivarla |
| 409 | `INVALID_STATE` | `categoryId` | Solo al aprobar un gasto o un ingreso: su categoría se borró. Sale junto con los de las cuentas. Sigue pendiente; se aprueba después de cambiarle la categoría con `PATCH` |
| 409 | `INSUFFICIENT_FUNDS` | `amount` | Solo al aprobar, sin errores `INVALID_STATE`: aprobarlo dejaría por debajo de cero una cuenta que no es de crédito. Sigue pendiente y ningún saldo cambia. Un pendiente con fecha futura se aprueba sin este chequeo |
| 400 | `VALIDATION_ERROR` | `accountId` / `categoryId` | Solo al aprobar con cuerpo: falta en un pendiente sin cuenta, sobra en uno con cuenta, la cuenta no existe, es ajena o está desactivada, o la categoría no existe o no es del tipo. Un error por campo. Nada cambia |
| 400 | `JSON_PARSING_ERROR` | `body` | Solo al aprobar: el cuerpo no es JSON válido |
| 502 | `EXTERNAL_SERVICE_ERROR` | `server` | Solo al aprobar con una cuenta en otra moneda: no hay ninguna tasa del par y el proveedor no responde. Nada cambia |

**`POST /api/transactions/groups/{groupId}/approve`** — sin cuerpo. Confirma **todo lo que siga
pendiente del grupo, o nada**. **200 OK** con un arreglo de esos movimientos, completos y
`"status": "CONFIRMED"`, en el orden en que entraron (el del mensaje o el del lote). Lo que ya se
aprobó o se rechazó uno a uno no cambia ni sale en la respuesta. Aplica las reglas de aprobar uno a
uno a cada pendiente, y el saldo no negativo sobre el efecto de todos juntos, como el alta en lote:
un ingreso del grupo compensa un gasto.

**`POST /api/transactions/groups/{groupId}/reject`** — sin cuerpo. **204 No Content.** Borra lo que
siga pendiente del grupo; lo ya aprobado no cambia.

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `groupId` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `groupId` | No existe, es de otro usuario, o ya se descartó: la respuesta es la misma |
| 409 | `INVALID_STATE` | `status` | El grupo ya no tiene pendientes porque se confirmó |
| 409 | `INVALID_STATE` | `[i].accountId` / `[i].destinationAccountId` / `[i].categoryId` | Solo al confirmar: los mismos de aprobar uno a uno, con `i`, la posición del pendiente entre los que siguen pendientes del grupo, en el orden en que entraron. No se confirma ninguno |
| 409 | `INSUFFICIENT_FUNDS` | `[i].amount` | Solo al confirmar, sin errores `INVALID_STATE`: el grupo entero dejaría en negativo una cuenta que no es de crédito; `i` es el primer pendiente que la cruza. No se confirma ninguno |
| 409 | `INVALID_STATE` | `[i].accountId` | Solo al confirmar: el pendiente no tiene cuenta (es compartido). Se aprueba uno a uno, con cuenta y categoría. No se confirma ninguno |

**`POST /api/transactions/pending/reject`** — rechaza en una petición varios pendientes elegidos,
de cualquier grupo (FIN-68). **204 No Content.** Cada uno se rechaza como con
`POST /api/transactions/{id}/reject`: se borra, y si es la parte de un gasto compartido la parte
queda `REJECTED` (ver [Gasto compartido](compartidos.md#gasto-compartido)). Es **todo o nada**: si alguno no se
puede rechazar, no se rechaza ninguno.

```json
{ "ids": ["0199b2d0-...", "0199b2e4-..."] }
```

| Campo | Tipo | Reglas |
|---|---|---|
| `ids` | array de string (UUID) | Obligatorio, de 1 a 100 elementos, sin repetir ninguno |

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `ids` | Falta, está vacío, trae más de 100, o repite un id (en cualquier combinación de mayúsculas) |
| 400 | `VALIDATION_ERROR` | `ids[i]` | El elemento `i` es `null`, está en blanco o no es un UUID |
| 400 | `JSON_PARSING_ERROR` | `body` | El cuerpo no es JSON válido |
| 404 | `NOT_FOUND` | `ids[i]` | El id `i` no existe, ya se rechazó o es de otro usuario: la respuesta es la misma. Uno por id. Si también hay confirmados, sale solo este error |
| 409 | `INVALID_STATE` | `ids[i]` | El movimiento `i` es del usuario pero ya está confirmado. Uno por id |
| 404 | `NOT_FOUND` | `ids` | Otra petición aprobó o rechazó alguno mientras se rechazaba el lote |

Ninguno de esos errores rechaza nada.

Un pendiente también se corrige con `PATCH` y se borra con `DELETE`, igual que uno confirmado, y
conserva su `groupId`. La excepción es un pendiente compartido: solo se aprueba o se rechaza, y
`PATCH` o `DELETE` responden 409 `INVALID_STATE` en `status`.

### Movimientos programados

Un movimiento confirmado con `occurredAt` posterior al momento actual está **programado**
(`"scheduled": true`). Hasta su fecha:

- no mueve el `currentBalance` de sus cuentas, en `GET /accounts` ni en `reports/balance`, ni el
  `availableCredit` de una tarjeta;
- no cuenta en `monthly-spending`, ni en `period` o `allTime` de `reports/balance`;
- **sí sale** en la lista de `reports/transactions`, marcado, pero no suma en sus totales ni en `net`.

Cuando llega la fecha empieza a contar solo: nada lo "activa", y no hace falta modificarlo ni que el
servidor haya estado encendido. Un pendiente con fecha futura sigue `PENDING` y además sale con
`"scheduled": true`; al aprobarlo, queda programado hasta su fecha.

Un programado **no se valida contra el saldo** (FA-133): ni al registrarlo ni al llegar su fecha. Un
gasto programado mayor que el saldo se acepta y, si al llegar la fecha deja la cuenta en negativo,
queda así; desde ahí la cuenta no acepta salidas hasta recibir dinero (ver
[Saldo no negativo](generalidades.md#saldo-no-negativo)).

`scheduled` no es un estado que se guarde ni que el cliente envíe: el servidor lo calcula en cada
respuesta comparando `occurredAt` con su reloj. Una misma respuesta puede marcarlo `true` y, unos
segundos después, otra marcarlo `false`.
