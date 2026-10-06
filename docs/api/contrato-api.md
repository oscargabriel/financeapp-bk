# Contrato del API — financeapp-bk

Este documento es la referencia para consumir el API: qué endpoints hay, cómo se autentica cada uno,
qué reciben y qué devuelven, y qué hay que tener en cuenta del lado del cliente. Describe el
comportamiento del código en `dev`. Si un endpoint cambia, se actualiza aquí en el mismo PR.

## Índice

| Método | Ruta | Autenticación | Para qué |
|---|---|---|---|
| `POST` | `/api/auth/register` | Basic | Crear un usuario |
| `POST` | `/api/auth/login` | Basic | Obtener el token de un usuario |
| `GET` | `/api/status` | Basic | Estado del servicio y de la base |
| `GET` | `/api/accounts` | Bearer | Listar las cuentas del usuario |
| `POST` | `/api/accounts` | Bearer | Crear una cuenta |
| `GET` | `/api/categories` | Bearer | Listar las categorías del usuario |
| `POST` | `/api/categories` | Bearer | Crear una categoría |
| `GET` | `/api/catalogs/account-types` | Bearer | Tipos de cuenta válidos |
| `GET` | `/api/catalogs/transaction-types` | Bearer | Tipos de movimiento válidos |
| `GET` | `/api/catalogs/currencies` | Bearer | Monedas activas |
| `GET` | `/api/catalogs/categories` | Bearer | Categorías del usuario para elegir `categoryId` |
| `POST` | `/api/transactions` | Bearer | Registrar un lote de movimientos |
| `PATCH` | `/api/transactions/{id}` | Bearer | Modificar un movimiento |
| `DELETE` | `/api/transactions/{id}` | Bearer | Eliminar un movimiento |
| `GET` | `/api/monthly-spending` | Bearer | Gasto mensual contra la meta |
| `GET` | `/api/reports/transactions` | Bearer | Movimientos y totales de un rango de días |

## Generalidades

### URL base

Todas las rutas cuelgan de `/api`:

| Ambiente | URL base |
|---|---|
| Local | `http://localhost:8080/api` |
| Producción | `https://<servicio>.run.app/api` (la URL de Cloud Run se entrega por aparte) |

Una ruta sin el prefijo `/api` no existe: responde 404.

### Formato

- Cuerpos en JSON, con `Content-Type: application/json`. Los campos van en `camelCase`.
- Los identificadores son UUID en texto (versión 7, ordenables por fecha de creación).
- Los campos sin valor salen como `null`. No se omiten.
- Los enums se escriben en mayúsculas (`EXPENSE`, `CREDIT`…). En el cuerpo de un `POST` o un `PATCH`, el
  API acepta minúsculas y espacios alrededor y los normaliza. En los query params tiene que llegar exacto.
- El cuerpo de una petición no puede pasar de **1 MB**. Si lo supera, la respuesta es 413
  (ver [Errores](#errores)).

### Montos

- Los montos son números JSON con hasta **4 decimales** y menos de **14 dígitos enteros**
  (columna `NUMERIC(18,4)`).
- En los movimientos el monto siempre es **positivo**: el signo lo da el tipo. Un gasto resta y un
  ingreso suma.
- `number` de JavaScript es un `double` y pierde precisión pasados ~15 dígitos significativos. Para
  saldos normales alcanza. Si el frontend va a sumar o comparar montos, conviene una librería
  decimal (`decimal.js`, `big.js`) y no la aritmética nativa.
- Por ahora **toda la operación es en COP**. Las cuentas se pueden crear en otra moneda del
  catálogo, pero no se les pueden registrar movimientos.

### Fechas

| Dato | Formato | Ejemplo |
|---|---|---|
| Fecha de un movimiento (entrada) | ISO-8601 **con offset** | `2026-09-20T10:15:00-05:00` |
| Fecha de un movimiento (salida) | ISO-8601 en UTC | `2026-09-20T15:15:00Z` |
| Mes | `YYYY-MM` | `2026-09` |

Un movimiento sin offset se rechaza: no se sabría a qué mes pertenece. La respuesta sale en UTC, y
el cliente la convierte a la zona que quiera mostrar.

## Autenticación

**No hay rutas públicas.** El API tiene dos esquemas y cada ruta acepta exactamente uno. Si llega el
otro, la respuesta es 401 aunque sea válido.

### Basic — `register`, `login`, `status`

Es una credencial **compartida de la aplicación**, no la del usuario. La entrega quien administra el
servicio.

```
Authorization: Basic base64(<usuario>:<clave>)
```

Su función es frenar escaneos y registros automatizados, no autenticar personas. En un frontend de
navegador cualquiera que abra las DevTools puede verla, y es un costo aceptado (FA-43). No la
trates como un secreto del usuario ni la guardes junto a sus datos.

### Bearer — todo lo demás

El token JWT que devuelve `POST /auth/login`:

```
Authorization: Bearer <accessToken>
```

- **Dura 1 hora** por defecto. `expiresIn` de la respuesta de login dice cuántos segundos le
  quedan, así el cliente no tiene que decodificar el token ni fiarse de su reloj.
- **No hay refresh token.** Cuando vence, el API responde 401 y hay que volver a hacer login.
- El usuario sale del token (`sub`). Ninguna ruta lleva el id del usuario en la URL, y nadie puede
  pedir datos de otro.

### El 401

Todo 401 del API tiene el mismo cuerpo, falle lo que falle (token ausente, vencido, mal firmado o
credencial equivocada):

```json
{"errors":[{"code":"UNAUTHENTICATED","description":"Autenticacion requerida","field":"authorization"}]}
```

Lleva además la cabecera `WWW-Authenticate: Basic` o `WWW-Authenticate: Bearer`, según la ruta. El
motivo concreto solo queda en el log del servidor. El cliente no lo puede saber y no debe
intentarlo: ante un 401 en una ruta Bearer, se vuelve al login.

La excepción es el login con credenciales del usuario incorrectas, que tiene su propio código
(ver [`POST /auth/login`](#post-apiauthlogin)).

## Errores

Todos los errores salen con la misma forma:

```json
{
  "errors": [
    { "code": "VALIDATION_ERROR", "description": "El nombre es obligatorio", "field": "name" }
  ]
}
```

- `code` es estable y es lo que el cliente debe usar para decidir qué hacer.
- `description` es texto para mostrar o registrar. Puede cambiar de redacción y no debe compararse.
- `field` dice dónde está el problema: el nombre del campo, `[3].amount` para el elemento 3 de un
  lote, o un valor genérico (`body`, `request`, `authorization`, `server`).
- Las validaciones **acumulan**: un 400 trae todos los campos inválidos a la vez, no solo el
  primero.

| HTTP | `code` | Cuándo |
|---|---|---|
| 400 | `VALIDATION_ERROR` | Un campo o parámetro no cumple las reglas del endpoint |
| 400 | `JSON_PARSING_ERROR` | El cuerpo no es JSON válido o un campo tiene un tipo incompatible (`"amount": "abc"`) |
| 401 | `UNAUTHENTICATED` | Falta la credencial o no es válida para esa ruta |
| 401 | `INVALID_CREDENTIALS` | Login con correo o contraseña incorrectos |
| 404 | `NOT_FOUND` | La ruta no existe (con token válido), o el recurso de la ruta no existe o es de otro usuario (`field`: `id`) |
| 405 | `VALIDATION_ERROR` | Método no soportado en esa ruta (`description`: "La peticion no pudo ser procesada") |
| 409 | `DUPLICATE_RESOURCE` | Ya existe: correo registrado o nombre de cuenta repetido |
| 413 | `PAYLOAD_TOO_LARGE` | El cuerpo pasa de 1 MB |
| 500 | `INTERNAL_SERVER_ERROR` | Error inesperado. Nunca trae detalle técnico |
| 503 | — | Solo en `/status`, cuando la base no responde (ver su sección) |

## Endpoints

### `POST /api/auth/register`

Crea un usuario y le copia las categorías por defecto. **Basic.** No inicia sesión: después hay que
llamar a login.

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `email` | string | sí | Formato de correo, hasta 255 caracteres. Se guarda en minúsculas y sin espacios alrededor |
| `password` | string | sí | Al menos 8 caracteres y no más de 72 bytes en UTF-8 |
| `firstName` | string | sí | Hasta 100 caracteres |
| `lastName` | string | no | Hasta 100 caracteres |
| `baseCurrencyCode` | string | no | Código de 3 letras que exista en el catálogo. Por defecto `COP` |
| `timezone` | string | no | Zona IANA (`America/Bogota`, `Europe/Madrid`). Por defecto `America/Bogota` |

```json
{
  "email": "ana@correo.com",
  "password": "unaClaveLarga",
  "firstName": "Ana",
  "lastName": "Pérez"
}
```

**201 Created**

```json
{
  "id": "0199a1b2-3c4d-7e5f-8a9b-0c1d2e3f4a5b",
  "email": "ana@correo.com",
  "firstName": "Ana",
  "lastName": "Pérez",
  "baseCurrencyCode": "COP",
  "timezone": "America/Bogota",
  "defaultCategories": 22
}
```

`defaultCategories` es cuántas categorías quedaron creadas para el usuario. La respuesta nunca
incluye la contraseña ni su hash.

**Errores:** 400 `VALIDATION_ERROR` (por campo; la moneda inexistente sale en `baseCurrencyCode`),
409 `DUPLICATE_RESOURCE` en `email` si el correo ya está registrado. Las mayúsculas no cuentan:
`Ana@correo.com` y `ana@correo.com` son el mismo correo.

**A tener en cuenta**

- La zona horaria del usuario decide a qué mes pertenece cada gasto en el reporte mensual. Conviene
  mandarla desde el navegador (`Intl.DateTimeFormat().resolvedOptions().timeZone`) en vez de
  dejar el valor por defecto.
- La regla de los 72 bytes importa con emojis o tildes: una contraseña de 72 caracteres puede
  pasarse de 72 bytes.

### `POST /api/auth/login`

Cambia correo y contraseña por un token. **Basic** (la credencial compartida va en la cabecera y
la del usuario en el cuerpo).

**Cuerpo**

| Campo | Tipo | Obligatorio |
|---|---|---|
| `email` | string | sí |
| `password` | string | sí |

```json
{ "email": "ana@correo.com", "password": "unaClaveLarga" }
```

**200 OK**

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

**Errores**

- 400 `VALIDATION_ERROR` si falta `email` o `password`.
- 401 `INVALID_CREDENTIALS`, `field: "credentials"`, "Correo o contrasena incorrectos". Sale igual
  si el correo no existe, si la contraseña está mal o si el usuario está desactivado: así el login
  no sirve para averiguar qué correos están registrados. El cliente debe mostrar un único mensaje
  genérico.

### `GET /api/status`

Estado del servicio y de sus dependencias. **Basic.** Está pensado para monitoreo, no para el
frontend.

**200 OK** — todo arriba:

```json
{ "status": "UP", "services": { "postgres": "UP" } }
```

**503 Service Unavailable** — mismo cuerpo con `"status": "DOWN"` y el servicio caído en `DOWN`.
Cada dependencia tiene 2 segundos para responder antes de contarse como caída.

### `GET /api/accounts`

Las cuentas del usuario del token. **Bearer.**

**Query params**

| Param | Valores | Por defecto | Efecto |
|---|---|---|---|
| `includeInactive` | `true` / `false` | `false` | Incluye las cuentas desactivadas |

Cualquier otro valor (`1`, `yes`, `TRUE`) es 400 `VALIDATION_ERROR` en `includeInactive`.

**200 OK** — arreglo, vacío si el usuario no tiene cuentas. Primero las activas, luego por nombre
sin distinguir mayúsculas.

```json
[
  {
    "id": "0199a1b2-...",
    "name": "Tarjeta Visa",
    "type": "CREDIT",
    "currencyCode": "COP",
    "currentBalance": -350000.0000,
    "availableCredit": 4650000.0000,
    "isActive": true
  },
  {
    "id": "0199a1b3-...",
    "name": "Efectivo",
    "type": "CASH",
    "currencyCode": "COP",
    "currentBalance": 120000.0000,
    "availableCredit": null,
    "isActive": true
  }
]
```

| Campo | Significado |
|---|---|
| `type` | `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`, `INVESTMENT` u `OTHER` |
| `currentBalance` | Saldo vigente. Lo calcula la base con cada movimiento y el cliente nunca lo escribe. **En una tarjeta de crédito, negativo es deuda** |
| `availableCredit` | Solo en `CREDIT`: `creditLimit + currentBalance`. `null` en los demás tipos o si la tarjeta no tiene cupo |

### `POST /api/accounts`

Crea una cuenta del usuario del token. **Bearer.**

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `name` | string | sí | Hasta 80 caracteres. Único por usuario sin distinguir mayúsculas |
| `type` | string | sí | `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`, `INVESTMENT`, `OTHER` |
| `currencyCode` | string | sí | 3 letras, debe existir y estar activa en el catálogo |
| `initialBalance` | number | no | Saldo con el que entra la cuenta. Por defecto `0`. Puede ser negativo (la deuda actual de una tarjeta) |
| `creditLimit` | number | no | **Solo `CREDIT`.** Mayor que cero |
| `statementDay` | integer | no | **Solo `CREDIT`.** Día de corte, 1 a 31 |
| `paymentDueDay` | integer | no | **Solo `CREDIT`.** Día de pago, 1 a 31 |
| `currentBalance` | — | **no se envía** | Si llega, es 400: el saldo vigente lo calcula el sistema. Para fijar el saldo de arranque se usa `initialBalance` |

En una cuenta que no es `CREDIT`, mandar `creditLimit`, `statementDay` o `paymentDueDay` es 400,
uno por campo.

```json
{
  "name": "Tarjeta Visa",
  "type": "CREDIT",
  "currencyCode": "COP",
  "initialBalance": -350000,
  "creditLimit": 5000000,
  "statementDay": 15,
  "paymentDueDay": 30
}
```

**201 Created** — la cuenta con la misma forma de un elemento de `GET /accounts`. `currentBalance`
arranca igual a `initialBalance`.

**Errores:** 400 `VALIDATION_ERROR` por campo, 409 `DUPLICATE_RESOURCE` en `name` si ya existe una
cuenta del usuario con ese nombre.

### `GET /api/categories`

Las categorías activas del usuario del token. **Bearer.**

**Query params**

| Param | Valores | Por defecto | Efecto |
|---|---|---|---|
| `appliesTo` | `EXPENSE`, `INCOME`, `BOTH` | todas | Filtra por el tipo de movimiento que admiten |

El filtro devuelve **las que sirven para ese tipo**. `EXPENSE` trae las de gasto y las `BOTH`.
`INCOME` trae las de ingreso y las `BOTH`. `BOTH` trae solo las `BOTH`. El valor tiene que ir en
mayúsculas: `expense` es 400 `VALIDATION_ERROR` en `appliesTo`.

**200 OK** — en el orden definido para el usuario y luego por nombre.

```json
[
  {
    "id": "0199a1c0-...",
    "name": "Mercado",
    "appliesTo": "EXPENSE",
    "icon": "shopping-cart",
    "color": "#2E7D32",
    "isSystem": true
  }
]
```

| Campo | Significado |
|---|---|
| `icon` | Nombre de un ícono, o `null`. Las categorías por defecto usan nombres del set [Lucide](https://lucide.dev/icons) (`shopping-cart`, `heart-pulse`, `wallet`); el API no los valida |
| `color` | `#RRGGBB`, o `null` |
| `isSystem` | `true` si vino de las categorías por defecto al registrarse |

Para el selector de categoría de un movimiento, pide `appliesTo=EXPENSE` en un gasto e
`appliesTo=INCOME` en un ingreso. Así solo aparecen las que el alta va a aceptar.

### `POST /api/categories`

Crea una categoría del usuario del token. **Bearer.**

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `name` | string | sí | Hasta 60 caracteres. Se recorta. Único entre las categorías vivas del usuario sin distinguir mayúsculas |
| `appliesTo` | string | sí | `EXPENSE`, `INCOME` o `BOTH`. Aquí, a diferencia del filtro de `GET`, se acepta en minúsculas |
| `icon` | string | no | Hasta 40 caracteres. En blanco se guarda como `null` |
| `color` | string | no | `#RRGGBB`. Se guarda en mayúsculas. En blanco se guarda como `null` |

```json
{
  "name": "Plantas",
  "appliesTo": "EXPENSE",
  "icon": "sprout",
  "color": "#7CB342"
}
```

**201 Created** — la categoría con la misma forma de un elemento de `GET /categories`, con
`isSystem` en `false`. Queda **al final** de la lista del usuario: después de todas las que ya
tenía, incluidas las de la semilla.

**Errores:** 400 `VALIDATION_ERROR` por campo, todos en la misma respuesta; 409
`DUPLICATE_RESOURCE` en `name` si otra categoría viva del usuario ya usa ese nombre, también una de
la semilla. El nombre de una categoría borrada sí se puede reutilizar.

### Catálogos — `GET /api/catalogs/*`

Los valores que aceptan los formularios de cuenta y de movimiento, para que el cliente no los copie
de este documento. **Bearer** en las cuatro rutas. Ninguna tiene parámetros salvo `categories`.

**`GET /api/catalogs/account-types`** y **`GET /api/catalogs/transaction-types`** — 200 OK, en este
orden:

```json
[{ "code": "CASH", "description": "Efectivo" }]
```

| Catálogo | `code` | `description` |
|---|---|---|
| account-types | `CASH` | Efectivo |
| account-types | `DEBIT` | Cuenta débito |
| account-types | `CREDIT` | Tarjeta de crédito |
| account-types | `SAVINGS` | Cuenta de ahorros |
| account-types | `INVESTMENT` | Inversión |
| account-types | `OTHER` | Otra |
| transaction-types | `EXPENSE` | Gasto |
| transaction-types | `INCOME` | Ingreso |
| transaction-types | `TRANSFER` | Transferencia |

El API recibe y devuelve siempre el `code`; `description` es solo para mostrar.

**`GET /api/catalogs/currencies`** — 200 OK, las monedas activas ordenadas por código. Son las que
acepta `currencyCode` en `POST /api/accounts`; el alta de movimientos, por ahora, solo admite COP.

```json
[{ "code": "COP", "name": "Peso colombiano", "symbol": "$" }]
```

**`GET /api/catalogs/categories`** — 200 OK, las categorías del usuario del token como
`{ "id", "name" }`, con el mismo orden y el mismo filtro `appliesTo` que
[`GET /api/categories`](#get-apicategories), y el mismo 400 si el valor no es válido. Para mostrar
ícono o color, usa ese endpoint.

```json
[{ "id": "0199a1c0-...", "name": "Mercado" }]
```

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
- **No es idempotente.** Reenviar el mismo lote crea los movimientos otra vez. Si la petición
  falla por red sin respuesta, revisa los saldos antes de reintentar. Un duplicado se borra con
  [`DELETE /api/transactions/{id}`](#delete-apitransactionsid).

**Elemento**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `type` | string | sí | `EXPENSE`, `INCOME` o `TRANSFER` |
| `accountId` | string (UUID) | sí | Cuenta del usuario, activa y en COP. En una transferencia, la de origen |
| `destinationAccountId` | string (UUID) | solo `TRANSFER` | Cuenta del usuario, activa, en COP y distinta del origen. Prohibido en los otros tipos |
| `categoryId` | string (UUID) | `EXPENSE` / `INCOME` | Categoría activa del usuario y compatible con el tipo. **Prohibido en `TRANSFER`** |
| `amount` | number | sí | Mayor que cero, hasta 4 decimales |
| `currencyCode` | string | no | Si se manda, tiene que ser `COP` |
| `description` | string | sí | Hasta 255 caracteres. Se recortan los espacios de los extremos |
| `notes` | string | no | Hasta 1000 caracteres |
| `occurredAt` | string | no | ISO-8601 con offset. Sin fecha (ausente, `null` o vacío), el instante en que el servidor atiende la petición, el mismo para todo el lote |
| `destinationAmount` | — | **no se envía** | Reservado para transferencias entre monedas. Hoy es 400 |

Una cuenta que no existe y una de otro usuario responden lo mismo ("La cuenta no existe"). El API
no confirma que un id exista fuera de tus datos.

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
    "occurredAt": "2026-09-20T15:15:00Z"
  }
]
```

**Efecto en los saldos.** La base los aplica en la misma transacción. Después del 201, un
`GET /accounts` ya los muestra:

| Tipo | `accountId` | `destinationAccountId` |
|---|---|---|
| `EXPENSE` | resta `amount` | — |
| `INCOME` | suma `amount` | — |
| `TRANSFER` | resta `amount` | suma `amount` |

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
| `accountId` | string (UUID) | Cuenta del usuario, activa y en COP |
| `destinationAccountId` | string (UUID) | Solo si el movimiento queda como `TRANSFER`. Cuenta del usuario, activa, en COP y distinta del origen |
| `categoryId` | string (UUID) | Solo si el movimiento queda como `EXPENSE` o `INCOME`. Categoría activa del usuario y compatible con el tipo |
| `amount` | number | Mayor que cero, hasta 4 decimales |
| `description` | string | Hasta 255 caracteres, no en blanco. Se recortan los espacios de los extremos |
| `occurredAt` | string | ISO-8601 con offset, no en blanco. A diferencia del alta, vacío no significa "ahora" |

- **Ausente o `null` es "no cambia".** No hay forma de vaciar un campo enviándolo en `null`.
- Un parche sin ningún campo (`{}`, o todo en `null`) es 400 en `body`.
- `notes`, `currencyCode` y `destinationAmount` **no se modifican**: si vienen en el cuerpo, se
  ignoran sin error.
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
operación, también si cambian la cuenta, el tipo o el monto.

**Errores propios**

| HTTP | `field` | Cuándo |
|---|---|---|
| 400 | `id` | El id de la ruta no es un UUID |
| 400 | `body` | El parche no trae ningún campo |
| 400 | el campo | Formato inválido, o una regla del tipo resultante, sin índice (`amount`, `categoryId`) |
| 404 | `id` | El movimiento no existe o es de otro usuario: la respuesta es la misma |

### `DELETE /api/transactions/{id}`

Borra un movimiento del usuario del token. **Bearer.** El borrado es físico: no hay papelera.

**204 No Content**, sin cuerpo. La base revierte su efecto en los saldos.

| HTTP | `field` | Cuándo |
|---|---|---|
| 400 | `id` | El id de la ruta no es un UUID |
| 404 | `id` | El movimiento no existe, ya se borró, o es de otro usuario |

### `GET /api/monthly-spending`

Gasto por mes del usuario del token comparado con su meta mensual. **Bearer.**

**Query params**

| Param | Formato | Por defecto |
|---|---|---|
| `from` | `YYYY-MM` | 11 meses antes de `to` |
| `to` | `YYYY-MM` | El mes actual |

Sin ninguno, la ventana son los **12 meses que terminan en el mes actual**. Con solo `from`, llega
hasta el mes actual. Con solo `to`, abarca los 12 meses que terminan ahí. El rango incluye los dos
extremos. Errores 400 `VALIDATION_ERROR`:

- Un mes mal escrito (`2026-9`, `2026-13`), en el campo `from` o `to` según cuál esté mal.
- `from` posterior a `to`, en el campo `from`.

**200 OK** — del mes más reciente al más antiguo:

```json
[
  {
    "periodMonth": "2026-09",
    "currencyCode": "COP",
    "totalSpent": 1830000.0000,
    "budgetAmount": 2500000.0000,
    "remaining": 670000.0000,
    "percentUsed": 73.20,
    "transactionCount": 42
  },
  {
    "periodMonth": "2026-08",
    "currencyCode": "COP",
    "totalSpent": 2100000.0000,
    "budgetAmount": null,
    "remaining": null,
    "percentUsed": null,
    "transactionCount": 37
  }
]
```

| Campo | Significado |
|---|---|
| `totalSpent` | Suma de los gastos (`EXPENSE`) del mes. Ingresos y transferencias no cuentan |
| `budgetAmount` | Meta de gasto total del mes, o `null` si no hay |
| `remaining` | `budgetAmount - totalSpent`. Negativo si se pasó de la meta. `null` sin meta |
| `percentUsed` | Porcentaje consumido de la meta, con 2 decimales. Puede pasar de 100. `null` sin meta |
| `currencyCode` | La moneda base del usuario |
| `transactionCount` | Cuántos gastos tuvo el mes |

**A tener en cuenta**

- **Los meses vacíos no vienen.** Un mes sin gastos y sin meta no aparece en el arreglo. Si el
  frontend dibuja una serie continua, tiene que rellenar los huecos con cero. Un mes con meta y
  sin gastos sí viene, con `totalSpent: 0`.
- El mes de cada gasto se decide con la **zona horaria del usuario** (la del registro), no con la
  del servidor ni la del navegador. Un gasto del 31 a las 23:00 en Bogotá es de ese mes aunque en
  UTC ya sea el siguiente.
- Hoy el API no tiene endpoint para crear metas, así que `budgetAmount` llega `null` hasta que se
  carguen por base.

### `GET /api/reports/transactions`

Movimientos del usuario del token en un rango de días, con sus totales por tipo y por categoría.
**Bearer.**

**Query params**

| Param | Formato | Obligatorio |
|---|---|---|
| `from` | `YYYY-MM-DD` | Sí |
| `to` | `YYYY-MM-DD` | Sí |
| `categoryId` | UUID, uno o varios | No |
| `accountId` | UUID, uno o varios | No |
| `type` | `EXPENSE`, `INCOME` o `TRANSFER`, uno o varios, sin distinguir mayúsculas | No |

Los filtros admiten varios valores repitiendo el parámetro (`?type=EXPENSE&type=INCOME`) o
separados por coma (`?type=EXPENSE,INCOME`). Varios juntos se combinan con Y. El rango incluye los
dos extremos. Errores 400 `VALIDATION_ERROR`, en el campo del parámetro:

- Falta `from` o `to`, o no viene como `YYYY-MM-DD` (`2026-10-1`, `2026/10/01`), o el día no existe
  (`2026-02-30`).
- `from` posterior a `to`, en el campo `from`.
- Un `categoryId` o un `accountId` que no es UUID, o un `type` fuera de los tres valores.

**200 OK** — `transactions` del más reciente al más antiguo:

```json
{
  "from": "2026-10-01",
  "to": "2026-10-31",
  "currencyCode": "COP",
  "transactions": [
    {
      "id": "0192a3b4-...",
      "type": "TRANSFER",
      "accountId": "0192a3b4-...",
      "destinationAccountId": "0192a3b4-...",
      "categoryId": null,
      "categoryName": null,
      "amount": 100.0000,
      "currencyCode": "USD",
      "amountBase": 410000.0000,
      "description": "Cambio de dólares",
      "notes": null,
      "occurredAt": "2026-10-10T20:00:00Z"
    },
    {
      "id": "0192a3b4-...",
      "type": "EXPENSE",
      "accountId": "0192a3b4-...",
      "destinationAccountId": null,
      "categoryId": "0192a3b4-...",
      "categoryName": "Mercado",
      "amount": 85000.0000,
      "currencyCode": "COP",
      "amountBase": 85000.0000,
      "description": "Carne y verduras",
      "notes": null,
      "occurredAt": "2026-10-02T15:00:00Z"
    }
  ],
  "totalsByType": [
    { "type": "EXPENSE", "total": 85000.0000, "count": 1 },
    { "type": "INCOME", "total": 0, "count": 0 },
    { "type": "TRANSFER", "total": 410000.0000, "count": 1 }
  ],
  "totalsByCategory": [
    { "categoryId": "0192a3b4-...", "categoryName": "Mercado", "total": 85000.0000, "count": 1 }
  ]
}
```

| Campo | Significado |
|---|---|
| `currencyCode` | La moneda base del usuario: la de `amountBase` y la de todos los totales |
| `amount` / `currencyCode` del movimiento | El monto en la moneda de la cuenta origen |
| `amountBase` | El mismo monto en la moneda base. Es lo que suman los totales |
| `totalsByType` | Una entrada por tipo consultado (los tres sin filtro de tipo), aunque sea en cero |
| `totalsByCategory` | Solo las categorías con movimientos, de mayor a menor total |

**A tener en cuenta**

- **Un rango sin movimientos no es 404**: responde 200 con `transactions` vacía, los tipos en cero
  y `totalsByCategory` vacía.
- **Las transferencias no tienen categoría.** Con filtro de categoría quedan fuera, y nunca entran
  en `totalsByCategory`.
- Un `categoryId` bien formado que no es del usuario no da error: el reporte sale vacío.
- **`accountId` toma la cuenta como origen o como destino.** El filtro de una cuenta trae también
  las transferencias que le llegan, y una transferencia entre dos cuentas filtradas sale una sola
  vez. Un `accountId` bien formado que no es del usuario, igual que una categoría ajena, deja el
  reporte vacío.
- **Los totales no llevan signo.** Con filtro de cuenta, `TRANSFER` suma lo que entró y lo que
  salió por ella: mide cuánto se movió, no su saldo neto.
- El día de cada movimiento se decide con la **zona horaria del usuario**, igual que el mes en
  `monthly-spending`: un gasto del 31 a las 21:30 en Bogotá es del 31 aunque en UTC ya sea el 1.
- No hay paginación: el rango entero viene en una respuesta.

## CORS

El servicio acepta los orígenes que declare su configuración (`CORS_ALLOWED_ORIGINS`). Mientras no
exista frontend está en `*`, una decisión provisional. Cuando haya frontend, se cambia por su URL.
Permite los métodos `GET`, `POST`, `PUT`, `PATCH` y `DELETE`, cualquier cabecera y credenciales. Para
desplegar un frontend en un dominio nuevo, hay que pedir que se agregue a la lista.

## Lo que el API todavía no tiene

Para que el frontend no lo busque:

- Editar o borrar cuentas o categorías.
- Consultar un movimiento por su id. Para listarlos está `GET /api/reports/transactions`.
- Crear metas de gasto.
- Refresh token o logout. El token simplemente vence.
- Movimientos en monedas distintas de COP.
