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
| `GET` | `/api/users/me` | Bearer | Perfil del usuario del token |
| `PATCH` | `/api/users/me` | Bearer | Modificar el perfil |
| `PUT` | `/api/users/me/password` | Bearer | Cambiar la contraseña |
| `GET` | `/api/accounts` | Bearer | Listar las cuentas del usuario |
| `POST` | `/api/accounts` | Bearer | Crear una cuenta |
| `PATCH` | `/api/accounts/{id}` | Bearer | Modificar una cuenta |
| `DELETE` | `/api/accounts/{id}` | Bearer | Borrar una cuenta |
| `GET` | `/api/categories` | Bearer | Listar las categorías del usuario |
| `POST` | `/api/categories` | Bearer | Crear una categoría |
| `PATCH` | `/api/categories/{id}` | Bearer | Modificar una categoría |
| `DELETE` | `/api/categories/{id}` | Bearer | Borrar una categoría |
| `GET` | `/api/catalogs/account-types` | Bearer | Tipos de cuenta válidos |
| `GET` | `/api/catalogs/transaction-types` | Bearer | Tipos de movimiento válidos |
| `GET` | `/api/catalogs/currencies` | Bearer | Monedas activas |
| `GET` | `/api/catalogs/categories` | Bearer | Categorías del usuario para elegir `categoryId` |
| `GET` | `/api/exchange-rates` | Bearer | Tasa de cambio de un par de monedas en una fecha |
| `POST` | `/api/transactions` | Bearer | Registrar un lote de movimientos |
| `PATCH` | `/api/transactions/{id}` | Bearer | Modificar un movimiento |
| `DELETE` | `/api/transactions/{id}` | Bearer | Eliminar un movimiento |
| `GET` | `/api/transactions/pending` | Bearer | Movimientos pendientes de aprobación |
| `POST` | `/api/transactions/{id}/approve` | Bearer | Aprobar un pendiente |
| `POST` | `/api/transactions/{id}/reject` | Bearer | Rechazar un pendiente |
| `POST` | `/api/recurrences` | Bearer | Crear una serie de gastos o ingresos que se repiten |
| `GET` | `/api/recurrences` | Bearer | Series activas, con su próxima ocurrencia |
| `PATCH` | `/api/recurrences/{id}` | Bearer | Modificar una serie, en sus futuras o en todas |
| `DELETE` | `/api/recurrences/{id}` | Bearer | Cancelar una serie |
| `POST` | `/api/installment-purchases/preview` | Bearer | Simular las cuotas de una compra con tarjeta |
| `POST` | `/api/installment-purchases` | Bearer | Registrar una compra con tarjeta en cuotas |
| `GET` | `/api/installment-purchases` | Bearer | Compras con cuotas por venir |
| `PATCH` | `/api/installment-purchases/{id}` | Bearer | Modificar una compra, en sus cuotas futuras o en todas |
| `DELETE` | `/api/installment-purchases/{id}` | Bearer | Cancelar las cuotas que faltan |
| `GET` | `/api/monthly-spending` | Bearer | Gasto mensual contra la meta |
| `GET` | `/api/reports/transactions` | Bearer | Movimientos y totales de un rango de días |
| `GET` | `/api/reports/balance` | Bearer | Ingresos menos gastos de un rango y de siempre, con las cuentas |
| `POST` | `/api/assistant/messages` | Bearer | Registrar o consultar con un mensaje en lenguaje natural |

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
- Los campos sin valor salen como `null`. No se omiten, con una excepción: `installments` de una
  [compra en cuotas](#compras-en-cuotas) solo sale en el alta, y en las demás respuestas no viene.
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
- Cada movimiento está en la **moneda de su cuenta** (FA-51). Lo que llega en otra moneda se convierte
  y queda pendiente: ver [`POST /api/transactions`](#post-apitransactions). Las series y las compras
  en cuotas todavía solo admiten cuentas en COP.

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
| 403 | `REGISTRATION_NOT_ALLOWED` | Alta con un correo que no está en la lista de admitidos (`field`: `email`) |
| 404 | `NOT_FOUND` | La ruta no existe (con token válido), o el recurso de la ruta no existe o es de otro usuario (`field`: `id`) |
| 405 | `VALIDATION_ERROR` | Método no soportado en esa ruta (`description`: "La peticion no pudo ser procesada") |
| 409 | `DUPLICATE_RESOURCE` | Ya existe: correo registrado, o nombre de cuenta o de categoría repetido |
| 409 | `RESOURCE_IN_USE` | El cambio dejaría inconsistentes otros datos que usan el recurso: el alcance de una categoría con movimientos que no admitiría (`field`: `appliesTo`), la moneda de una cuenta con movimientos (`field`: `currencyCode`), o borrar una cuenta con saldo (`field`: `currentBalance`) |
| 409 | `INVALID_STATE` | El recurso no está en el estado que la acción exige: aprobar o rechazar un movimiento ya confirmado (`field`: `status`), o aprobar un pendiente con una cuenta desactivada (`field`: `accountId` o `destinationAccountId`) |
| 413 | `PAYLOAD_TOO_LARGE` | El cuerpo pasa de 1 MB |
| 500 | `INTERNAL_SERVER_ERROR` | Error inesperado. Nunca trae detalle técnico |
| 502 | `EXTERNAL_SERVICE_ERROR` | El modelo del asistente falló o no respondió a tiempo (`field`: `server`). Ver [`POST /api/assistant/messages`](#post-apiassistantmessages). También al registrar o modificar un movimiento cuya moneda no tiene ninguna tasa de cambio guardada y el proveedor no respondió: no se guardó nada y se puede reintentar |
| 503 | — | Solo en `/status`, cuando la base no responde (ver su sección) |

## Endpoints

### `POST /api/auth/register`

Crea un usuario y le copia las categorías por defecto. **Basic.** No inicia sesión: después hay que
llamar a login.

**Registro restringido (FA-103).** Solo se registran los correos de una lista de admitidos que
mantiene a mano el dueño del servicio. No hay endpoint para consultarla ni para modificarla. Una
entrada admite un correo exacto o un dominio completo, y se compara sin distinguir mayúsculas. Un
dominio admitido no admite sus subdominios. La restricción está encendida en producción, y en local
la lista admite `@front.local`.

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `email` | string | sí | Formato de correo, hasta 255 caracteres. Se guarda en minúsculas y sin espacios alrededor |
| `password` | string | sí | Al menos 8 caracteres y no más de 72 bytes en UTF-8 |
| `firstName` | string | sí | Hasta 100 caracteres |
| `lastName` | string | no | Hasta 100 caracteres |
| `baseCurrencyCode` | string | no | Código de 3 letras que exista en el catálogo. Por defecto `COP` |
| `timezone` | string | no | Zona IANA (`America/Bogota`, `Europe/Madrid`). Por defecto `America/Bogota` |
| `phone` | string | no | Un `+` opcional y de 7 a 15 dígitos, sin espacios ni separadores. Se recorta |

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
  "phone": null,
  "baseCurrencyCode": "COP",
  "timezone": "America/Bogota",
  "defaultCategories": 22
}
```

`defaultCategories` es cuántas categorías quedaron creadas para el usuario. La respuesta nunca
incluye la contraseña ni su hash.

`phone` sale en `null` si no se envió.

**Errores**, en el orden en que se resuelven:

1. 400 `VALIDATION_ERROR`, por campo. Primero el formato del cuerpo; la moneda inexistente sale en
   `baseCurrencyCode`.
2. 403 `REGISTRATION_NOT_ALLOWED` en `email` si el correo no está admitido. No se crea nada.
3. 409 `DUPLICATE_RESOURCE` en `email` si el correo ya está registrado. Las mayúsculas no cuentan:
   `Ana@correo.com` y `ana@correo.com` son el mismo correo.

Un correo no admitido recibe el 403 aunque ya tenga cuenta: el alta no dice qué correos están
registrados. Para el cliente, el 403 es un mensaje propio en el campo de correo, distinto del de
correo repetido.

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

El login **no consulta la lista de admitidos** del registro. Si un correo sale de la lista, el usuario
que ya se registró con él sigue entrando; para cortarle el acceso se desactiva el usuario.

### `GET /api/status`

Estado del servicio y de sus dependencias. **Basic.** Está pensado para monitoreo, no para el
frontend.

**200 OK** — todo arriba:

```json
{ "status": "UP", "services": { "postgres": "UP" } }
```

**503 Service Unavailable** — mismo cuerpo con `"status": "DOWN"` y el servicio caído en `DOWN`.
Cada dependencia tiene 2 segundos para responder antes de contarse como caída.

### `GET /api/users/me`

El perfil del usuario del token. **Bearer.**

**200 OK**

```json
{
  "id": "0199a1b2-3c4d-7e5f-8a9b-0c1d2e3f4a5b",
  "email": "ana@correo.com",
  "firstName": "Ana",
  "lastName": "Pérez",
  "phone": "3001234567",
  "baseCurrencyCode": "COP",
  "timezone": "America/Bogota"
}
```

`lastName` y `phone` pueden venir en `null`. La respuesta nunca trae la contraseña ni su hash.

**Errores:** 401 `UNAUTHENTICATED` también si el usuario fue desactivado o borrado y su token
todavía no vence: el cliente lo trata como cualquier otro 401 y vuelve al login.

### `PATCH /api/users/me`

Modifica el perfil del usuario del token. **Bearer.** Se envían solo los campos que cambian.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `firstName` | string | Hasta 100 caracteres, no en blanco. Se recorta |
| `lastName` | string | Hasta 100 caracteres. **En blanco lo borra** |
| `email` | string | Formato de correo, hasta 255 caracteres, no en blanco. Se guarda en minúsculas. **Si cambia, exige `currentPassword`** |
| `phone` | string | Las reglas del alta. **En blanco lo borra** |
| `timezone` | string | Zona IANA, no en blanco |
| `currentPassword` | string | La contraseña actual. Solo se mira si el correo cambia |
| `baseCurrencyCode` | — | **Se omite**: ni error ni cambio. Cambiar la moneda base todavía no existe (FA-91) |

- **Ausente o `null` es "no cambia".** Para vaciar el apellido o el celular se manda el texto en
  blanco (`""`).
- Un parche sin ningún campo modificable es 400 en `body`. `currentPassword` y `baseCurrencyCode`
  no cuentan: `{"baseCurrencyCode": "USD"}` solo también es 400 en `body`.
- El correo cuenta como cambiado si difiere del guardado después de recortarlo y pasarlo a
  minúsculas. Mandar el mismo en otra caja no exige la contraseña.
- Cambiar el correo no invalida el token: sigue sirviendo hasta que vence.

```json
{
  "firstName": "Ana María",
  "phone": "3109876543",
  "email": "ana.maria@correo.com",
  "currentPassword": "unaClaveLarga"
}
```

**200 OK** — el perfil completo como quedó, con la forma de `GET /api/users/me`.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `body` | El parche no trae ningún campo modificable |
| 400 | `VALIDATION_ERROR` | el campo | Formato inválido o campo obligatorio en blanco, uno por campo |
| 400 | `VALIDATION_ERROR` | `currentPassword` | El correo cambia y no vino la contraseña actual |
| 400 | `INVALID_CREDENTIALS` | `currentPassword` | El correo cambia y la contraseña actual no es correcta |
| 409 | `DUPLICATE_RESOURCE` | `email` | Otro usuario ya usa ese correo, sin distinguir mayúsculas |

**La contraseña equivocada es 400, no 401.** El token es válido: un 401 haría que el cliente cerrara
la sesión de alguien que solo se equivocó al escribir. Se verifica antes que el correo, así que con
la contraseña mal nunca se sabe si el correo nuevo estaba libre.

### `PUT /api/users/me/password`

Cambia la contraseña del usuario del token. **Bearer.**

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `currentPassword` | string | sí | La contraseña actual |
| `newPassword` | string | sí | Las reglas del alta: al menos 8 caracteres y no más de 72 bytes en UTF-8 |

```json
{ "currentPassword": "unaClaveLarga", "newPassword": "otraClaveLarga" }
```

**204 No Content** — sin cuerpo. Desde ese momento el login funciona con la nueva y no con la
anterior. Los tokens ya emitidos siguen sirviendo hasta que vencen: no hay forma de revocarlos.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | el campo | Falta una de las dos, o la nueva no cumple las reglas |
| 400 | `INVALID_CREDENTIALS` | `currentPassword` | La contraseña actual no es correcta. 400 y no 401, por lo mismo que en el `PATCH` |

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
    "initialBalance": 0.0000,
    "currentBalance": -350000.0000,
    "creditLimit": 5000000.0000,
    "availableCredit": 4650000.0000,
    "statementDay": 15,
    "paymentDueDay": 30,
    "monthlyInterestRate": 2.1500,
    "isActive": true
  },
  {
    "id": "0199a1b3-...",
    "name": "Efectivo",
    "type": "CASH",
    "currencyCode": "COP",
    "initialBalance": 100000.0000,
    "currentBalance": 120000.0000,
    "creditLimit": null,
    "availableCredit": null,
    "statementDay": null,
    "paymentDueDay": null,
    "monthlyInterestRate": null,
    "isActive": true
  }
]
```

| Campo | Significado |
|---|---|
| `type` | `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`, `INVESTMENT` u `OTHER` |
| `initialBalance` | Saldo con el que la cuenta entró al sistema |
| `currentBalance` | Saldo vigente: `initialBalance` más el efecto de los movimientos que ya ocurrieron; los [programados](#movimientos-programados) no cuentan hasta su fecha. Lo calcula la base y el cliente nunca lo escribe. **En una tarjeta de crédito, negativo es deuda** |
| `creditLimit` | Solo en `CREDIT`: el cupo. `null` en los demás tipos o si la tarjeta no lo tiene |
| `availableCredit` | Solo en `CREDIT`: `creditLimit + currentBalance` menos el capital de las cuotas de [compras en cuotas](#compras-en-cuotas) que todavía no llegan. `null` en los demás tipos o si la tarjeta no tiene cupo |
| `statementDay`, `paymentDueDay` | Solo en `CREDIT`: día de corte y día de pago, 1 a 31. `null` en los demás tipos |
| `monthlyInterestRate` | Solo en `CREDIT`: tasa de interés **mensual en porcentaje** (`2.15` es el 2,15 % mensual, no 0.0215). `null` en los demás tipos o si la tarjeta no la tiene cargada |
| `isActive` | `false` si la cuenta está [desactivada](#patch-apiaccountsid). Solo sale en `false` con `includeInactive=true` o en la respuesta del parche que la desactivó |

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
| `monthlyInterestRate` | number | no | **Solo `CREDIT`.** Tasa mensual en porcentaje, de 0 a 10 con hasta 4 decimales. 0 es una tarjeta sin interés |
| `currentBalance` | — | **no se envía** | Si llega, es 400: el saldo vigente lo calcula el sistema. Para fijar el saldo de arranque se usa `initialBalance` |

En una cuenta que no es `CREDIT`, mandar `creditLimit`, `statementDay`, `paymentDueDay` o
`monthlyInterestRate` es 400, uno por campo.

El tope de 10 en la tasa existe para atrapar la tasa **efectiva anual** del extracto (por ejemplo
28,5) escrita en el campo mensual: como mensual daría cuotas con diez veces el interés real.

```json
{
  "name": "Tarjeta Visa",
  "type": "CREDIT",
  "currencyCode": "COP",
  "initialBalance": -350000,
  "creditLimit": 5000000,
  "statementDay": 15,
  "paymentDueDay": 30,
  "monthlyInterestRate": 2.15
}
```

**201 Created** — la cuenta con la misma forma de un elemento de `GET /accounts`. `currentBalance`
arranca igual a `initialBalance`.

**Errores:** 400 `VALIDATION_ERROR` por campo, 409 `DUPLICATE_RESOURCE` en `name` si ya existe una
cuenta del usuario con ese nombre.

### `PATCH /api/accounts/{id}`

Modifica una cuenta del usuario del token. **Bearer.** Se envían solo los campos que cambian. Una
cuenta desactivada se modifica igual que una activa.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `name` | string | Hasta 80 caracteres, no en blanco. Se recorta. Único entre las cuentas vivas del usuario sin distinguir mayúsculas |
| `currencyCode` | string | 3 letras, debe existir y estar activa. **Solo si la cuenta no tiene movimientos** |
| `initialBalance` | number | Hasta 4 decimales. **Corre `currentBalance` en la misma diferencia**, con o sin movimientos |
| `creditLimit` | number | **Solo `CREDIT`.** Mayor que cero |
| `statementDay` | integer | **Solo `CREDIT`.** 1 a 31 |
| `paymentDueDay` | integer | **Solo `CREDIT`.** 1 a 31 |
| `monthlyInterestRate` | number | **Solo `CREDIT`.** Tasa mensual en porcentaje, de 0 a 10 con hasta 4 decimales |
| `isActive` | boolean | `false` desactiva la cuenta y `true` la reactiva (ver abajo) |
| `currentBalance`, `type` | — | **No se envían.** Si llegan con valor, 400 en su campo |

- **Ausente o `null` es "no cambia".** El parche no vacía campos: no hay forma de quitarle el cupo,
  las fechas o la tasa a una tarjeta.
- Un parche sin ningún campo modificable (`{}`, o todo en `null`) es 400 en `body`.
- El tipo es el de la cuenta guardada: `creditLimit`, `statementDay`, `paymentDueDay` o
  `monthlyInterestRate` sobre una cuenta que no es `CREDIT` son 400, uno por campo.
- Corregir el saldo inicial no descuadra nada: si la cuenta arrancó en 200000 y gastó 50000 (saldo
  150000), pasar el inicial a 300000 deja el saldo en 250000.
- Mandar la moneda que la cuenta ya tiene, en cualquier caja, no es un cambio y no da 409.
- Cambiar solo las mayúsculas del nombre de la propia cuenta no es un choque.

**Desactivar y reactivar (FA-68).** `{"isActive": false}` desactiva la cuenta y `{"isActive": true}`
la reactiva, con cualquier saldo: a favor, en deuda o en cero. El cambio de estado no toca el saldo
ni los movimientos. Pedir el estado que la cuenta ya tiene responde 200 sin cambios. Puede ir junto
con otros campos del parche, y si el parche se rechaza, el estado tampoco cambia. Mientras está
desactivada:

- No sale en `GET /api/accounts` salvo con `includeInactive=true`, ni en `accounts` de
  `reports/balance`.
- No se puede elegir como origen ni destino de un movimiento nuevo, una serie o una compra en cuotas:
  400 `VALIDATION_ERROR` en ese campo. Los movimientos que ya tiene se corrigen y se borran como
  siempre.
- Un pendiente con esa cuenta no se puede aprobar: 409 `INVALID_STATE` (ver
  [Movimientos pendientes](#movimientos-pendientes)). Sí se puede rechazar.
- El asistente no la reconoce por su nombre.
- Se sigue pudiendo modificar y borrar.

Al reactivarla, todo vuelve a funcionar como antes.

```json
{
  "name": "Tarjeta Visa Oro",
  "creditLimit": 8000000,
  "paymentDueDay": 28
}
```

**200 OK** — la cuenta completa como quedó, con la misma forma que un elemento de
`GET /accounts`.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 400 | `VALIDATION_ERROR` | `body` | El parche no trae ningún campo modificable |
| 400 | `VALIDATION_ERROR` | el campo | Formato inválido, campo en blanco, campo de crédito en una cuenta que no es `CREDIT`, o `currentBalance` o `type` en el cuerpo |
| 400 | `VALIDATION_ERROR` | `currencyCode` | La moneda nueva no existe o no está activa |
| 404 | `NOT_FOUND` | `id` | La cuenta no existe, está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `DUPLICATE_RESOURCE` | `name` | Otra cuenta viva del usuario ya usa ese nombre |
| 409 | `RESOURCE_IN_USE` | `currencyCode` | La cuenta tiene movimientos, como origen o destino. No cambia nada del parche |

### `DELETE /api/accounts/{id}`

Borra una cuenta del usuario del token. **Bearer.** Es un borrado lógico: la fila se queda, con
todos sus movimientos. Una cuenta desactivada se borra igual que una activa.

**Solo una cuenta en cero.** Con `currentBalance` distinto de cero, a favor o en deuda, responde 409
y no borra nada. Para dejarla en cero: una transferencia a otra cuenta, o corregir su
`initialBalance` con `PATCH /api/accounts/{id}`.

Lo que pasa después:

- Deja de aparecer en `GET /api/accounts`, también con `includeInactive=true`.
- Su nombre queda libre para otra cuenta.
- Sus movimientos no cambian. `GET /api/reports/transactions` los sigue mostrando, también
  filtrando con `accountId` por la cuenta borrada.
- Un `PATCH /api/transactions/{id}` de uno de esos movimientos que no elige cuenta se acepta.
- Ningún movimiento nuevo, ni un PATCH, puede elegirla: es 400 en `accountId` o
  `destinationAccountId`, como con una cuenta que no existe.

**204 No Content** — sin cuerpo.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La cuenta no existe, ya está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `RESOURCE_IN_USE` | `currentBalance` | La cuenta tiene saldo distinto de cero |

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
| `icon` | Nombre de un ícono. Nunca es `null`. Las categorías por defecto usan nombres del set [Lucide](https://lucide.dev/icons) (`shopping-cart`, `heart-pulse`, `wallet`); el API no los valida |
| `color` | `#RRGGBB`. Nunca es `null` |
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
| `icon` | string | sí | Hasta 40 caracteres, no en blanco. Se recorta |
| `color` | string | sí | `#RRGGBB`, no en blanco. Se guarda en mayúsculas |

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

### `PATCH /api/categories/{id}`

Modifica una categoría del usuario del token. **Bearer.** Se envían solo los campos que cambian.
Las categorías de la semilla se modifican igual que las propias, y siguen con `isSystem` en `true`.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `name` | string | Hasta 60 caracteres, no en blanco. Se recorta. Único entre las categorías vivas del usuario sin distinguir mayúsculas |
| `appliesTo` | string | `EXPENSE`, `INCOME` o `BOTH`, en cualquier caja |
| `icon` | string | Hasta 40 caracteres, no en blanco |
| `color` | string | `#RRGGBB`, no en blanco. Se guarda en mayúsculas |

- **Ausente o `null` es "no cambia".** Ningún campo se puede vaciar: un `icon` o un `color` en
  blanco son 400, igual que en el alta.
- Un parche sin ningún campo (`{}`, o todo en `null`) es 400 en `body`.
- Cambiar solo las mayúsculas del nombre de la propia categoría no es un choque.
- Pasar `appliesTo` a `EXPENSE` cuando la categoría tiene ingresos, o a `INCOME` cuando tiene gastos,
  es 409 y no cambia nada del parche. Pasar a `BOTH` siempre se puede.
- La categoría conserva su lugar en la lista.

```json
{
  "name": "Huerta",
  "icon": "leaf",
  "color": "#558B2F"
}
```

**200 OK** — la categoría completa como quedó, con la misma forma que un elemento de
`GET /categories`.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 400 | `VALIDATION_ERROR` | `body` | El parche no trae ningún campo |
| 400 | `VALIDATION_ERROR` | el campo | Formato inválido o campo en blanco, todos en la misma respuesta |
| 404 | `NOT_FOUND` | `id` | La categoría no existe, está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `DUPLICATE_RESOURCE` | `name` | Otra categoría viva del usuario ya usa ese nombre |
| 409 | `RESOURCE_IN_USE` | `appliesTo` | La categoría tiene movimientos del tipo que el alcance nuevo dejaría fuera |

### `DELETE /api/categories/{id}`

Borra una categoría del usuario del token. **Bearer.** El borrado es lógico: la categoría deja de
aparecer en `GET /categories` y en el catálogo, pero no desaparece de la historia. No hay forma de
recuperarla desde el API.

**204 No Content**, sin cuerpo.

- Se puede borrar aunque tenga movimientos, y también una de la semilla.
- Sus movimientos no cambian: conservan su `categoryId`, y el reporte de movimientos los sigue
  mostrando con el `categoryName` que tenía.
- Un PATCH de uno de esos movimientos que no cambie su categoría ni su tipo se acepta. Ningún
  movimiento nuevo, ni un PATCH que la elija, puede usarla: es el mismo 400 de una categoría que no
  existe.
- Su nombre queda libre para crear o renombrar otra.

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La categoría no existe, ya está borrada o es de otro usuario: la respuesta es la misma |

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
acepta `currencyCode` en `POST /api/accounts` y en el alta de movimientos.

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

### `GET /api/exchange-rates`

La tasa de cambio de un par de monedas en una fecha: cuántas unidades de `to` vale una de `from`.
**Bearer.** Sirve para mostrar conversiones, y es la misma tasa con la que el alta de movimientos
convierte lo que llega en otra moneda (FA-51).

| Parámetro | Obligatorio | Formato |
|---|---|---|
| `from` | sí | Código de una moneda activa (`GET /api/catalogs/currencies`) |
| `to` | sí | Código de una moneda activa |
| `date` | sí | `YYYY-MM-DD` |

**200 OK:**

```json
{ "from": "EUR", "to": "COP", "date": "2026-10-10", "rate": 5125.0000000000, "rateDate": "2026-10-10" }
```

- `rate` va con 10 decimales. El mismo par vale 1.
- Toda tasa se calcula contra el dólar: `from→to = (USD→to) / (USD→from)`. Cada lado sale de la tasa
  guardada más reciente con fecha menor o igual a `date`, por vieja que sea. Si no hay ninguna (una
  fecha anterior a la primera tasa guardada), se usa la más antigua (FA-122).
- `rateDate` es la fecha del dato más viejo que se usó. Si es anterior a `date`, la tasa no está al
  día; si es posterior, `date` es anterior a todas las tasas guardadas. Conviene mostrar los dos casos.
- Para hoy o una fecha futura, si falta la tasa del día, el API la pide a ExchangeRate-API y la
  guarda. Si el proveedor no responde, se usa la anterior.

**Atribución obligatoria.** Las tasas vienen del plan gratuito de ExchangeRate-API, cuyos términos
exigen mostrar `Rates By Exchange Rate API` con un enlace a `https://www.exchangerate-api.com`
donde se muestren las tasas.

| Status | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `from`, `to` o `date` | Falta el parámetro, la moneda no está activa o la fecha no es un día válido |
| 404 | `NOT_FOUND` | `date` | Una de las monedas no tiene ninguna tasa guardada, ni el proveedor la dio |

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
| `accountId` | string (UUID) | sí | Cuenta del usuario y activa, en cualquier moneda. En una transferencia, la de origen |
| `destinationAccountId` | string (UUID) | solo `TRANSFER` | Cuenta del usuario, activa y distinta del origen. Prohibido en los otros tipos |
| `categoryId` | string (UUID) | `EXPENSE` / `INCOME` | Categoría activa del usuario y compatible con el tipo. **Prohibido en `TRANSFER`** |
| `amount` | number | sí | Mayor que cero, hasta 4 decimales. En la moneda de `currencyCode` |
| `currencyCode` | string | no | Moneda en que viene `amount`: código de 3 letras activo en el catálogo, sin importar mayúsculas. Ausente, la de la cuenta |
| `description` | string | sí | Hasta 255 caracteres. Se recortan los espacios de los extremos |
| `notes` | string | no | Hasta 1000 caracteres |
| `occurredAt` | string | no | ISO-8601 con offset. Sin fecha (ausente, `null` o vacío), el instante en que el servidor atiende la petición, el mismo para todo el lote |
| `destinationAmount` | number | no | Solo en una `TRANSFER` entre cuentas de monedas distintas: lo que entra al destino, en su moneda. Mayor que cero, hasta 4 decimales |

Una cuenta que no existe y una de otro usuario responden lo mismo ("La cuenta no existe"). El API
no confirma que un id exista fuera de tus datos.

**Monedas** (FA-51). El movimiento se registra siempre en la moneda de su cuenta (`currencyCode` de
la respuesta), que es lo que cobra el banco.

- Si `currencyCode` es otra moneda, `amount` se convierte a la de la cuenta con la tasa de
  [`GET /api/exchange-rates`](#get-apiexchange-rates) de la fecha del movimiento, redondeado a los
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
    "originalCurrencyCode": null
  }
]
```

`status` es `CONFIRMED` en todo lo que entra por aquí, salvo lo que se convierte de otra moneda; si el
cuerpo trae un `status`, se ignora. Lo demás que entra `PENDING` lo pone el asistente (ver
[Movimientos pendientes](#movimientos-pendientes)).

`destinationAmount`, `originalAmount` y `originalCurrencyCode` van en toda respuesta de un movimiento,
en `null` cuando no aplican: el primero solo en una transferencia entre monedas distintas, y los otros
dos solo si el movimiento se convirtió.

`origin` dice por dónde entró el movimiento, y no cambia después:

| Valor | Quién lo pone |
|---|---|
| `WEB` | Este endpoint, las ocurrencias de una [serie](#series-recurrentes) y las cuotas de una [compra en cuotas](#compras-en-cuotas) |
| `TELEGRAM` | El [asistente](#post-apiassistantmessages). Entra `PENDING` |
| `IMPORT` | Reservado para una importación de archivos. Hoy ningún endpoint lo produce |

Va en las mismas respuestas que `status`. Lo pone el servidor: si el cuerpo trae un `origin`, se
ignora.

`scheduled` es `true` si `occurredAt` es posterior al momento en que el servidor responde: el
movimiento está **programado** (ver [Movimientos programados](#movimientos-programados)). Lo calcula
el servidor; si el cuerpo trae un `scheduled`, se ignora.

`recurrenceId` es el id de la serie de la que el movimiento es ocurrencia (ver
[Series recurrentes](#series-recurrentes)), o `null` si no es de ninguna. Va en toda respuesta de un
movimiento: alta, modificación, pendientes y aprobación. Lo pone el servidor: si el cuerpo trae un
`recurrenceId`, se ignora, y un movimiento no se puede agregar a una serie ni sacar de ella.

`installment` dice si el movimiento es una cuota de una [compra en cuotas](#compras-en-cuotas):
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

### `DELETE /api/transactions/{id}`

Borra un movimiento del usuario del token. **Bearer.** El borrado es físico: no hay papelera.

**204 No Content**, sin cuerpo. La base revierte su efecto en los saldos.

| HTTP | `field` | Cuándo |
|---|---|---|
| 400 | `id` | El id de la ruta no es un UUID |
| 404 | `id` | El movimiento no existe, ya se borró, o es de otro usuario |

### Movimientos pendientes

Lo que registre el asistente de IA entra **pendiente** (`"status": "PENDING"`) hasta que el usuario
lo aprueba. Mientras tanto no mueve saldos ni cuenta en `monthly-spending`, `reports/transactions`
ni `reports/balance`. Los crea [`POST /api/assistant/messages`](#post-apiassistantmessages), con
`"origin": "TELEGRAM"`. También entra pendiente lo que [`POST /api/transactions`](#post-apitransactions)
convierte de otra moneda (FA-51), con su origen de siempre.

Los tres endpoints son **Bearer**.

**`GET /api/transactions/pending`** — **200 OK** con los pendientes del usuario, del más reciente al
más antiguo por `occurredAt`, con la misma forma que un elemento de la respuesta del alta. Sin
pendientes, `[]`.

**`POST /api/transactions/{id}/approve`** — sin cuerpo. **200 OK** con el movimiento completo y
`"status": "CONFIRMED"`. Desde ese momento mueve los saldos como en el alta y cuenta en los reportes.

**`POST /api/transactions/{id}/reject`** — sin cuerpo. **204 No Content.** El pendiente se borra y no
deja rastro.

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | No existe, ya se rechazó, o es de otro usuario: la respuesta es la misma |
| 409 | `INVALID_STATE` | `status` | Ya está confirmado. Para borrar un confirmado está `DELETE /api/transactions/{id}` |
| 409 | `INVALID_STATE` | `accountId` / `destinationAccountId` | Solo al aprobar: la cuenta de ese campo está desactivada, un error por cuenta. Sigue pendiente y ningún saldo cambia; se aprueba después de reactivarla |

Un pendiente también se corrige con `PATCH` y se borra con `DELETE`, igual que uno confirmado.

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

`scheduled` no es un estado que se guarde ni que el cliente envíe: el servidor lo calcula en cada
respuesta comparando `occurredAt` con su reloj. Una misma respuesta puede marcarlo `true` y, unos
segundos después, otra marcarlo `false`.

### Series recurrentes

Un gasto o ingreso que se repite cada cierto número de semanas o de meses: una suscripción, una
cuota de gimnasio, el salario. Sus **ocurrencias** son movimientos normales, con el `recurrenceId` de
la serie, y quedan [programadas](#movimientos-programados) hasta su fecha. **Bearer** en las cuatro
rutas.

**Fechas.** Cada ocurrencia cae a las 00:00 de su día en la zona del usuario (`05:00:00Z` en Bogotá),
así que la de hoy ya ocurrió y cuenta. La primera es el primer día pedido desde `startDate`, incluido;
las siguientes caen cada `interval` semanas o meses. En una mensual el día se mantiene: con
`dayOfMonth` 31, un mes más corto la pone en su último día (31-ene, 28-feb, 31-mar, 30-abr).

**Con fin y sin fin.** Con `endDate` u `occurrences`, el alta crea todas las ocurrencias de una vez.
Sin fin, crea las que van hasta hoy y la siguiente. Las demás aparecen solas: al consultar
`GET /accounts`, `reports/balance`, `reports/transactions`, `monthly-spending` o la lista de series, el
servidor crea antes las que falten, aunque haya estado apagado.

**Ocurrencias sueltas.** Una ocurrencia se corrige o se borra con `PATCH` o `DELETE` sobre
`/api/transactions/{id}`, y sigue siendo de su serie. Una borrada a mano no vuelve a aparecer.

#### `POST /api/recurrences`

```json
{
  "type": "EXPENSE",
  "accountId": "0199a1b3-...",
  "categoryId": "0199a1c0-...",
  "amount": 44900,
  "description": "Netflix",
  "frequency": "MONTHLY",
  "interval": 1,
  "dayOfMonth": 15,
  "startDate": "2026-10-15",
  "occurrences": 12
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `type` | Sí | `EXPENSE` o `INCOME`. No hay transferencias recurrentes |
| `accountId` | Sí | Cuenta propia, activa y en COP: por ahora las series solo admiten cuentas en COP |
| `categoryId` | Sí | Categoría propia que aplique al tipo |
| `amount` | Sí | Mayor que cero, hasta 4 decimales |
| `description` | Sí | Hasta 255 caracteres; se recorta |
| `frequency` | Sí | `WEEKLY` o `MONTHLY`. Anual es `MONTHLY` con `interval` 12 |
| `interval` | No | Cada cuántas semanas (1 a 52) o meses (1 a 12). Por defecto 1 |
| `dayOfWeek` | En `WEEKLY` | `MONDAY` a `SUNDAY`. No va en una mensual |
| `dayOfMonth` | En `MONTHLY` | 1 a 31. No va en una semanal |
| `startDate` | Sí | `YYYY-MM-DD`. Puede ser pasada: las ocurrencias hasta hoy cuentan de una vez |
| `endDate` | No | `YYYY-MM-DD`, incluida, no anterior a `startDate` |
| `occurrences` | No | Total de ocurrencias, de 1 a 500. No va junto con `endDate` |

Una serie no crea más de 500 ocurrencias de una vez: con fin, el error va en `endDate`; sin fin y con
un inicio muy atrás, en `startDate`. Una serie que no tendría ninguna ocurrencia antes de su
`endDate` también es error en `endDate`.

**201 Created** — la serie:

```json
{
  "id": "019a2f10-...",
  "type": "EXPENSE",
  "accountId": "0199a1b3-...",
  "categoryId": "0199a1c0-...",
  "amount": 44900.0000,
  "currencyCode": "COP",
  "description": "Netflix",
  "frequency": "MONTHLY",
  "interval": 1,
  "dayOfWeek": null,
  "dayOfMonth": 15,
  "startDate": "2026-10-15",
  "endDate": null,
  "occurrences": 12,
  "nextOccurrenceAt": "2026-10-15T05:00:00Z"
}
```

`nextOccurrenceAt` es la ocurrencia más próxima con fecha posterior al momento actual, en UTC. Es
`null` en una serie con fin a la que ya no le queda ninguna. `startDate` es el inicio de la regla
actual: cambiar la periodicidad lo mueve (ver abajo).

#### `GET /api/recurrences`

**200 OK** — arreglo de series con la forma del alta, de la próxima ocurrencia a la más lejana. Solo
las **activas**: no canceladas y con ocurrencias por venir. Una sin fin siempre lo está; una con fin
deja de estarlo cuando pasa su última ocurrencia. Sin series activas, `[]`.

#### `PATCH /api/recurrences/{id}`

```json
{ "scope": "FUTURE", "amount": 49900 }
```

`scope` es obligatorio: `FUTURE` cambia solo las ocurrencias con fecha posterior al momento actual;
`ALL`, también las pasadas, y los saldos se recalculan. Lo demás es opcional, y ausente o `null` es
"no cambia": `amount`, `description`, `categoryId`, `accountId`, `frequency`, `interval`, `dayOfWeek`,
`dayOfMonth`. Al menos uno tiene que venir.

- Las ocurrencias del alcance reciben **solo los campos que trae el parche**, también las que se
  editaron a mano. Lo que el parche no trae, lo conservan.
- **Cambiar la periodicidad rehace las futuras, con cualquier `scope`**: se borran y se crean de nuevo
  con la regla nueva, que empieza mañana en la zona del usuario. Las pasadas no cambian de fecha. Una
  serie con `occurrences` conserva el total: las ya ocurridas se descuentan. Una con `endDate` llega
  hasta esa fecha; una sin fin queda con la siguiente.
- La regla se valida sobre la serie resultante: pasar a `MONTHLY` exige `dayOfMonth` en el parche, y
  un `dayOfWeek` en una serie que queda mensual es error.

**200 OK** — la serie como quedó.

#### `DELETE /api/recurrences/{id}`

**204 No Content.** Borra las ocurrencias futuras, conserva las pasadas con su `recurrenceId` y saca la
serie de la lista. No lleva `scope`.

**Errores de las cuatro rutas**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | El del campo | Cualquier regla de las tablas de arriba |
| 400 | `VALIDATION_ERROR` | `scope` | `PATCH` sin `scope`, o con un valor distinto de `FUTURE` y `ALL` |
| 400 | `VALIDATION_ERROR` | `body` | `PATCH` sin ningún campo que modificar, además de `scope` |
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La serie no existe, es de otro usuario o ya está cancelada |

### Compras en cuotas

Una compra con tarjeta de crédito diferida a cuotas. Cada **cuota** es un gasto normal de la tarjeta,
con su `installment` (ver [`POST /api/transactions`](#post-apitransactions)), y queda
[programada](#movimientos-programados) hasta su fecha. **Bearer** en las cinco rutas.

**Solo en una tarjeta** `CREDIT` con `statementDay` (corte) y `paymentDueDay` (pago).

**Fechas.** El primer corte es el día de corte del mes de la compra si la compra cae ese día o antes
—**una compra del mismo día del corte entra en ese corte**—, y si no, el del mes siguiente. La primera
cuota vence el día de pago del mes del corte si el pago es posterior al corte, y del mes siguiente si
no; las demás, el día de pago de cada mes. Un día que el mes no tiene cae en su último día (31-ene,
28-feb, 31-mar). Cada cuota vence a las 00:00 de su día en la zona del usuario (`05:00:00Z` en Bogotá).
Con corte 20 y pago 5, una compra del 9 o del 20 de octubre vence el 5 de noviembre; una del 21, el 5
de diciembre.

**Capital e interés.** El capital de cada cuota es el total entre las cuotas, en pesos enteros y
redondeado hacia abajo; la última se lleva el resto, de modo que los capitales suman el total exacto.
El interés de cada cuota es la tasa mensual de la tarjeta sobre el capital que falta antes de esa cuota,
en pesos enteros, la mitad hacia arriba. Con 1 cuota, o con tasa 0 o sin tasa, no hay interés. La tasa
se copia en la compra: cambiar la de la tarjeta después no mueve las cuotas.

| Cuota (1.200.000 a 3, tasa 2) | Capital | Interés | Total |
|---|---|---|---|
| 1 | 400.000 | 24.000 (2 % de 1.200.000) | 424.000 |
| 2 | 400.000 | 16.000 (2 % de 800.000) | 416.000 |
| 3 | 400.000 | 8.000 (2 % de 400.000) | 408.000 |

**El cupo.** Desde la compra, `availableCredit` de la tarjeta descuenta el capital de las cuotas que
todavía no llegan, aunque esas cuotas no cuenten en `currentBalance`. Al llegar una cuota, su capital
deja de estar comprometido y la cuota entera entra al saldo: el interés consume cupo cuando se cobra.
En el ejemplo, una tarjeta en cero con cupo 5.000.000 queda en 3.800.000 al comprar, y en 3.776.000
cuando vence la primera cuota.

**Cuotas sueltas.** Una cuota se corrige o se borra con `PATCH` o `DELETE` sobre
`/api/transactions/{id}` y sigue siendo de su compra, con su número. Un **pago adelantado** se
registra así: adelantando la fecha de la cuota o borrándola. Una cuota borrada cuenta como pagada.

#### `POST /api/installment-purchases/preview` y `POST /api/installment-purchases`

Los dos reciben el mismo cuerpo y aplican las mismas reglas:

```json
{
  "accountId": "0199a1b2-...",
  "categoryId": "0199a1c0-...",
  "amount": 1200000,
  "description": "Televisor",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `accountId` | Sí | Tarjeta `CREDIT` propia, activa y en COP (por ahora las cuotas solo se registran en tarjetas en COP), con día de corte y día de pago |
| `categoryId` | Sí | Categoría propia que aplique a gastos |
| `amount` | Sí | Total de la compra, sin interés. Mayor que cero, hasta 4 decimales, y no menor que `installmentCount`: cada cuota lleva al menos 1 peso de capital |
| `description` | Sí | Hasta 255 caracteres; se recorta. Es la de cada cuota |
| `purchaseDate` | Sí | `YYYY-MM-DD`, hoy o antes en la zona del usuario. Si es pasada, las cuotas vencidas cuentan de una vez |
| `installmentCount` | Sí | De 1 a 48 |

`preview` responde **200 OK** sin guardar nada:

```json
{
  "accountId": "0199a1b2-...",
  "amount": 1200000,
  "currencyCode": "COP",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3,
  "monthlyInterestRate": 2.0000,
  "totalInterest": 48000,
  "totalAmount": 1248000,
  "installments": [
    { "number": 1, "dueAt": "2026-11-05T05:00:00Z", "principal": 400000, "interest": 24000, "amount": 424000 },
    { "number": 2, "dueAt": "2026-12-05T05:00:00Z", "principal": 400000, "interest": 16000, "amount": 416000 },
    { "number": 3, "dueAt": "2027-01-05T05:00:00Z", "principal": 400000, "interest": 8000, "amount": 408000 }
  ]
}
```

El alta responde **201 Created** con la compra y su plan, cada cuota con el `transactionId` de su
movimiento:

```json
{
  "id": "019a3c20-...",
  "accountId": "0199a1b2-...",
  "categoryId": "0199a1c0-...",
  "amount": 1200000.0000,
  "currencyCode": "COP",
  "description": "Televisor",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3,
  "monthlyInterestRate": 2.0000,
  "paidCount": 0,
  "remainingPrincipal": 1200000,
  "remainingAmount": 1248000,
  "nextInstallment": {
    "number": 1, "transactionId": "019a3c20-...", "dueAt": "2026-11-05T05:00:00Z", "amount": 424000
  },
  "installments": [
    { "number": 1, "transactionId": "019a3c20-...", "dueAt": "2026-11-05T05:00:00Z",
      "principal": 400000, "interest": 24000, "amount": 424000 }
  ]
}
```

| Campo | Significado |
|---|---|
| `monthlyInterestRate` | La tasa de la tarjeta al registrar la compra; `0` si no tenía |
| `paidCount` | `installmentCount` menos las cuotas que existen con fecha posterior al momento actual. Una cuota borrada a mano cuenta como pagada |
| `remainingPrincipal` | Capital de esas cuotas: lo que la compra tiene comprometido del cupo |
| `remainingAmount` | Lo que falta por pagar: el monto actual de esas cuotas, también si se editó a mano |
| `nextInstallment` | La cuota más próxima con fecha posterior al momento actual, o `null` si no queda ninguna |
| `installments` | Solo en el alta: el plan completo |

#### `GET /api/installment-purchases`

**200 OK** — arreglo de compras con la forma del alta, **sin** `installments`, de la próxima cuota a la
más lejana. Solo las **activas**: no canceladas y con alguna cuota por venir. Una compra cuyas cuotas
ya pasaron todas deja de aparecer. Sin compras activas, `[]`. Cada cuota está en
`reports/transactions` con su `installment`.

#### `PATCH /api/installment-purchases/{id}`

```json
{ "scope": "FUTURE", "description": "Televisor sala" }
```

`scope` es obligatorio: `FUTURE` cambia solo las cuotas con fecha posterior al momento actual; `ALL`,
también las pasadas. Se puede cambiar `description` y `categoryId`; ausente o `null` es "no cambia", y
al menos uno tiene que venir. Las cuotas del alcance reciben los cambios también si se editaron a mano.
Ningún saldo cambia. El monto, las cuotas, la tarjeta y la fecha no se editan: se cancela y se registra
de nuevo.

**200 OK** — la compra como quedó, sin `installments`.

#### `DELETE /api/installment-purchases/{id}`

**204 No Content.** Borra las cuotas futuras, conserva las pasadas con su `installment` y saca la compra
de la lista. El capital de las cuotas borradas vuelve al cupo. No lleva `scope`.

**Errores de las cinco rutas**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | El del campo | Cualquier regla de la tabla del alta |
| 400 | `VALIDATION_ERROR` | `accountId` | La cuenta no es una tarjeta, o la tarjeta no tiene día de corte o de pago |
| 400 | `VALIDATION_ERROR` | `purchaseDate` | La fecha es posterior a hoy en la zona del usuario |
| 400 | `VALIDATION_ERROR` | `scope` | `PATCH` sin `scope`, o con un valor distinto de `FUTURE` y `ALL` |
| 400 | `VALIDATION_ERROR` | `body` | `PATCH` sin `description` ni `categoryId` |
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La compra no existe, es de otro usuario o ya está cancelada |

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
| `totalSpent` | Suma de los gastos (`EXPENSE`) confirmados del mes que ya ocurrieron, en la moneda base del usuario con la misma regla que `amountBase` del reporte de movimientos. Ingresos, transferencias, pendientes y programados no cuentan |
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

Movimientos confirmados del usuario del token en un rango de días, con sus totales por tipo y por
categoría. Los pendientes no salen hasta aprobarse. Los programados salen marcados con
`"scheduled": true` y no suman en los totales. **Bearer.**

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
      "occurredAt": "2026-10-10T20:00:00Z",
      "scheduled": false,
      "recurrenceId": null,
      "installment": null
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
      "occurredAt": "2026-10-02T15:00:00Z",
      "scheduled": false,
      "recurrenceId": null,
      "installment": null
    }
  ],
  "totalsByType": [
    { "type": "EXPENSE", "total": 85000.0000, "count": 1 },
    { "type": "INCOME", "total": 0, "count": 0 },
    { "type": "TRANSFER", "total": 410000.0000, "count": 1 }
  ],
  "totalsByCategory": [
    { "categoryId": "0192a3b4-...", "categoryName": "Mercado", "total": 85000.0000, "count": 1 }
  ],
  "net": -85000.0000
}
```

| Campo | Significado |
|---|---|
| `currencyCode` | La moneda base del usuario: la de `amountBase` y la de todos los totales |
| `amount` / `currencyCode` del movimiento | El monto en la moneda de la cuenta origen |
| `amountBase` | El mismo monto en la moneda base. Si el movimiento ya está en esa moneda, es su `amount` tal cual; si no, se convierte con la tasa de su fecha, no con la de hoy. Es lo que suman los totales |
| `scheduled` | `true` si el movimiento está programado: sale en la lista, pero no en los totales ni en `net` |
| `recurrenceId` | La serie de la que el movimiento es ocurrencia, o `null`. Sirve para mostrar juntas las ocurrencias de una serie |
| `installment` | `{purchaseId, number, count}` si el movimiento es una cuota de una [compra en cuotas](#compras-en-cuotas) ("3 de 12"), o `null` |
| `totalsByType` | Una entrada por tipo consultado (los tres sin filtro de tipo), aunque sea en cero. Sin los programados |
| `totalsByCategory` | Solo las categorías con movimientos ya ocurridos, de mayor a menor total |
| `net` | Total de `INCOME` menos total de `EXPENSE`. Puede ser negativo |

**A tener en cuenta**

- **Un rango sin movimientos no es 404**: responde 200 con `transactions` vacía, los tipos en cero
  y `totalsByCategory` vacía.
- **Las transferencias no tienen categoría.** Con filtro de categoría quedan fuera, y nunca entran
  en `totalsByCategory`.
- Un `categoryId` bien formado que no es del usuario no da error: el reporte sale vacío.
- Los movimientos de una categoría borrada siguen saliendo, con su nombre, en `transactions` y en
  `totalsByCategory`.
- **`accountId` toma la cuenta como origen o como destino.** El filtro de una cuenta trae también
  las transferencias que le llegan, y una transferencia entre dos cuentas filtradas sale una sola
  vez. Un `accountId` bien formado que no es del usuario, igual que una categoría ajena, deja el
  reporte vacío.
- **Los totales no llevan signo.** Con filtro de cuenta, `TRANSFER` suma lo que entró y lo que
  salió por ella: mide cuánto se movió, no su saldo neto.
- El día de cada movimiento se decide con la **zona horaria del usuario**, igual que el mes en
  `monthly-spending`: un gasto del 31 a las 21:30 en Bogotá es del 31 aunque en UTC ya sea el 1.
- No hay paginación: el rango entero viene en una respuesta.
- **`net` sigue a los filtros.** Se calcula sobre la lista devuelta: un tipo que el filtro deja
  fuera cuenta como cero, así que con `type=EXPENSE` el neto es el gasto con signo negativo, y con
  `type=TRANSFER` es cero.

### `GET /api/reports/balance`

Cuánto entró menos cuánto salió, en un rango y desde siempre, junto con las cuentas activas del
usuario del token. **Bearer.**

**Query params**

| Param | Formato | Obligatorio |
|---|---|---|
| `from` | `YYYY-MM-DD` | Junto con `to` |
| `to` | `YYYY-MM-DD` | Junto con `from` |

Van los dos o ninguno. Sin ninguno, el rango es el mes en curso. Errores 400 `VALIDATION_ERROR`, en
el campo del parámetro:

- Viene uno solo: el error va en el que falta.
- Un día que no viene como `YYYY-MM-DD` o que no existe.
- `from` posterior a `to`, en el campo `from`.

**200 OK**

```json
{
  "currencyCode": "COP",
  "period": {
    "from": "2026-10-01",
    "to": "2026-10-31",
    "income": 5300000.0000,
    "expense": 1905500.0000,
    "net": 3394500.0000
  },
  "allTime": {
    "income": 14300000.0000,
    "expense": 5170500.0000,
    "net": 9129500.0000
  },
  "accounts": [
    {
      "id": "0192a3b4-...",
      "name": "Bancolombia",
      "type": "DEBIT",
      "currencyCode": "COP",
      "currentBalance": 12375000.0000,
      "creditLimit": null,
      "availableCredit": null
    },
    {
      "id": "0192a3b4-...",
      "name": "Visa",
      "type": "CREDIT",
      "currencyCode": "COP",
      "currentBalance": -658000.0000,
      "creditLimit": 5000000.0000,
      "availableCredit": 4342000.0000
    }
  ]
}
```

| Campo | Significado |
|---|---|
| `currencyCode` | La moneda base del usuario: la de `period` y `allTime` |
| `period` | Ingresos, gastos y neto del rango, con las fechas que se usaron |
| `allTime` | Lo mismo con todos los movimientos del usuario, sin importar el rango |
| `net` | `income` menos `expense`. Puede ser negativo |
| `accounts` | Las cuentas activas por nombre, cada una con su saldo en **su** moneda. Las desactivadas no salen |
| `availableCredit` | En una tarjeta, lo que queda por gastar: igual que en `GET /accounts`, `creditLimit` + `currentBalance` menos el capital de las cuotas de [compras en cuotas](#compras-en-cuotas) que todavía no llegan |

**A tener en cuenta**

- **Las transferencias no cuentan** como ingreso ni como gasto: mover plata entre cuentas propias,
  incluido pagar la tarjeta, no cambia el neto. Los gastos pagados con tarjeta sí cuentan.
- **Los pendientes no cuentan** hasta aprobarse, ni en `period` ni en `allTime`.
- **Los programados no cuentan** hasta su fecha, ni en `period`, ni en `allTime`, ni en el
  `currentBalance` de las cuentas: un rango futuro con solo programados sale en cero.
- **Las cuentas no se suman.** Pueden estar en monedas distintas, así que la respuesta no trae un
  saldo total.
- Una tarjeta sin cupo cargado trae `creditLimit` y `availableCredit` en `null`. Se completa con
  `PATCH /api/accounts/{id}`.
- El día de cada movimiento se decide con la zona horaria del usuario, como en
  `reports/transactions`. El mes por defecto sale del reloj del servidor, que hoy está en la misma
  zona.

### `POST /api/assistant/messages`

Recibe un mensaje en lenguaje natural ("gasté 20 mil en almuerzo con la Nequi", "¿cuánto gasté este
mes?") y lo resuelve con un modelo de IA (FA-77). **Bearer.** Registra un movimiento, consulta los
movimientos o consulta el saldo, siempre del usuario del token.

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `data` | string | sí | El mensaje. No en blanco, hasta 1000 caracteres |

```json
{ "data": "gaste 20000 en almuerzo con la Nequi" }
```

**200 OK** — también cuando el asistente no pudo hacer lo pedido: `intent` dice qué pasó.

```json
{
  "intent": "CREATE_TRANSACTION",
  "message": "Registre un gasto de 20.000 COP en Nequi (Restaurantes). Queda pendiente de tu aprobacion.",
  "transaction": {
    "id": "0199a1d0-...",
    "type": "EXPENSE",
    "accountId": "0199a1b3-...",
    "destinationAccountId": null,
    "categoryId": "0199a1c2-...",
    "amount": 20000,
    "currencyCode": "COP",
    "description": "Almuerzo",
    "notes": null,
    "occurredAt": "2026-10-10T17:30:12.345Z",
    "status": "PENDING",
    "origin": "TELEGRAM",
    "scheduled": false,
    "recurrenceId": null,
    "installment": null
  },
  "report": null,
  "balance": null
}
```

| `intent` | Qué hizo | Objeto que trae |
|---|---|---|
| `CREATE_TRANSACTION` | Registró un gasto, ingreso o transferencia **pendiente** de aprobación | `transaction`, con la forma de un elemento del alta de [`POST /api/transactions`](#post-apitransactions) |
| `LIST_TRANSACTIONS` | Consultó los movimientos | `report`, con la forma de [`GET /api/reports/transactions`](#get-apireportstransactions) |
| `GET_BALANCE` | Consultó ingresos, gastos y cuentas | `balance`, con la forma de [`GET /api/reports/balance`](#get-apireportsbalance) |
| `NEEDS_CLARIFICATION` | Nada: le falta o no reconoce un dato, y `message` dice cuál | Ninguno |
| `UNSUPPORTED` | Nada: lo pedido no es ninguna de las tres acciones | Ninguno |

Solo viene el objeto del `intent`; los otros dos salen en `null`. `message` es un texto para
mostrar al usuario, escrito siempre por el servidor y nunca por el modelo. Como toda `description`,
no se compara: para decidir se usa `intent`.

**A tener en cuenta**

- **Lo que registra queda pendiente.** Entra con `"status": "PENDING"` y `"origin": "TELEGRAM"`, y no
  mueve saldos hasta que el usuario lo aprueba (ver [Movimientos pendientes](#movimientos-pendientes)).
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
| 400 | `VALIDATION_ERROR` | `data` | Falta, está en blanco o pasa de 1000 caracteres |
| 401 | `UNAUTHENTICATED` | `authorization` | Sin token, token inválido o la credencial Basic |
| 502 | `EXTERNAL_SERVICE_ERROR` | `server` | El modelo falló o no respondió a tiempo. No se registró nada: se puede reintentar |

## CORS

El servicio acepta los orígenes que declare su configuración (`CORS_ALLOWED_ORIGINS`). Mientras no
exista frontend está en `*`, una decisión provisional. Cuando haya frontend, se cambia por su URL.
Permite los métodos `GET`, `POST`, `PUT`, `PATCH` y `DELETE`, cualquier cabecera y credenciales. Para
desplegar un frontend en un dominio nuevo, hay que pedir que se agregue a la lista.

## Lo que el API todavía no tiene

Para que el frontend no lo busque:

- Recuperar o listar las cuentas borradas.
- Recuperar o listar las categorías borradas.
- Consultar un movimiento por su id. Para listarlos está `GET /api/reports/transactions`.
- Crear movimientos pendientes por otra vía que el asistente.
- Aprobar o rechazar pendientes en lote.
- Crear metas de gasto.
- Refresh token o logout. El token simplemente vence, también después de cambiar la contraseña.
- Cambiar la moneda base del usuario (FA-91): el `PATCH /api/users/me` la omite.
- Recuperar la contraseña olvidada (FA-90).
- Series y compras en cuotas en monedas distintas de COP.
- Transferencias recurrentes, y cambiar el fin (`endDate`, `occurrences`) o el tipo de una serie.
- Listar las ocurrencias de una serie: se filtran por `recurrenceId` en `reports/transactions`.
- Cambiar el monto, el número de cuotas, la tarjeta o la fecha de una compra en cuotas, o recalcular
  sus cuotas si cambian los datos de la tarjeta. Tampoco valida que la compra quepa en el cupo.
- Listar las cuotas de una compra: se filtran por `installment.purchaseId` en `reports/transactions`.
