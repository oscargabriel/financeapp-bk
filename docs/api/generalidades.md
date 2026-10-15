# Contrato del API — generalidades

Parte del [contrato del API](contrato-api.md): lo que vale para todos los endpoints.

## Generalidades

### URL base

Todas las rutas cuelgan de `/api`:

| Ambiente | URL base |
|---|---|
| Local | `http://localhost:8080/api` |
| Producción | `https://<servicio>.run.app/api` (la URL de Cloud Run se entrega por aparte) |

Una ruta sin el prefijo `/api` no existe: responde 404.

### Formato

- Cuerpos en JSON, con `Content-Type: application/json`. Los campos van en `camelCase`. La única
  excepción es [`POST /api/assistant/messages`](asistente.md#post-apiassistantmessages) con un audio o una
  imagen, que va en `multipart/form-data`.
- Los identificadores son UUID en texto (versión 7, ordenables por fecha de creación).
- Los campos sin valor salen como `null`. No se omiten, con una excepción: `installments` de una
  [compra en cuotas](cuotas.md#compras-en-cuotas) solo sale en el alta, y en las demás respuestas no viene.
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
  y queda pendiente: ver [`POST /api/transactions`](transacciones.md#post-apitransactions). Las series y las compras
  en cuotas también van en la moneda de su cuenta, sin conversión (FA-123).

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

### Basic — `register`, `login`, `google/register`, `google/login`, `refresh`, `logout`, `status`

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
- **Se renueva con el refresh token** (FIN-47). El login abre una sesión que dura **24 horas**, y
  `refreshExpiresIn` dice cuántos segundos le quedan. Antes de que venza el access, el cliente lo
  cambia en [`POST /auth/refresh`](auth.md#post-apiauthrefresh) por un access y un refresh nuevos. Los
  refrescos no alargan la sesión: a las 24 horas del login hay que volver a entrar.
- **Cada refresh sirve una sola vez.** Presentar uno ya usado cierra la sesión entera, incluido el
  refresh que lo reemplazó. Dos refrescos simultáneos con el mismo refresh cuentan como reuso: el
  cliente tiene que hacerlos de uno en uno.
- **El access deja de servir en cuanto se cierra su sesión de usuario**: si el usuario cambia la
  contraseña o lo desactivan, cualquier access emitido antes responde 401 desde ese momento, no al
  vencer. El API lo comprueba en cada request.
- Un access emitido antes de FIN-47 no trae la marca de sesión y responde 401: al desplegar
  FIN-47, cada usuario vuelve a hacer login una vez.
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
intentarlo: ante un 401 en una ruta Bearer, prueba una vez con
[`POST /auth/refresh`](auth.md#post-apiauthrefresh), y si el refresco también da 401, vuelve al login.

Dos excepciones:

- El login con credenciales del usuario incorrectas tiene su propio código (ver
  [`POST /auth/login`](auth.md#post-apiauthlogin)), y también un ID token de Google que no sirve (ver
  [`POST /auth/google/login`](auth.md#post-apiauthgooglelogin)).
- El refresh que no sirve da el mismo `UNAUTHENTICATED`, pero con `field: "refreshToken"` y sin
  `WWW-Authenticate`: la credencial Basic de la cabecera era buena, lo que falló es el cuerpo.

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
| 401 | `UNAUTHENTICATED` | Falta la credencial o no es válida para esa ruta, o el refresh de `POST /auth/refresh` no sirve |
| 401 | `INVALID_CREDENTIALS` | Login con correo o contraseña incorrectos, o un ID token de Google que no sirve en el registro o el login con Google (`field`: `idToken`) |
| 403 | `REGISTRATION_NOT_ALLOWED` | Alta, o registro con Google, con un correo que no está en la lista de admitidos (`field`: `email`) |
| 404 | `NOT_FOUND` | La ruta no existe (con token válido), o el recurso de la ruta no existe o es de otro usuario (`field`: `id`) |
| 404 | `ACCOUNT_NOT_REGISTERED` | Login con Google de una cuenta Google sin usuario (`field`: `idToken`). El cliente ofrece el registro |
| 405 | `VALIDATION_ERROR` | Método no soportado en esa ruta (`description`: "La peticion no pudo ser procesada") |
| 409 | `DUPLICATE_RESOURCE` | Ya existe: correo o cuenta de Google registrados, nombre de cuenta o de categoría repetido, una solicitud de afiliación abierta o una afiliación con esa persona, o un correo ya vinculado a otra cuenta de Google (`field`: `email`) |
| 409 | `RESOURCE_IN_USE` | El cambio dejaría inconsistentes otros datos que usan el recurso: el alcance de una categoría con movimientos que no admitiría (`field`: `appliesTo`), la moneda de una cuenta con movimientos (`field`: `currencyCode`), o borrar una cuenta con saldo (`field`: `currentBalance`) |
| 409 | `INVALID_STATE` | El recurso no está en el estado que la acción exige: aprobar o rechazar un movimiento o un grupo ya confirmado (`field`: `status`), o aprobar un pendiente con una cuenta desactivada o una categoría borrada (`field`: `accountId`, `destinationAccountId` o `categoryId`, con `[i].` delante si es un grupo), o una acción sobre una afiliación que no corresponde a su estado o al papel del usuario (`field`: `status`), o cambiar la contraseña o el correo sin tener contraseña (`field`: `currentPassword`), o definir la primera contraseña teniendo una (`field`: `newPassword`) |
| 409 | `INSUFFICIENT_FUNDS` | La petición dejaría por debajo de cero el saldo de una cuenta que no es de crédito. Ver [Saldo no negativo](#saldo-no-negativo) |
| 413 | `PAYLOAD_TOO_LARGE` | El cuerpo pasa de 1 MB. No aplica al archivo del asistente, que tiene su propio tope de 5 MB y responde 400 (ver [`POST /api/assistant/messages`](asistente.md#post-apiassistantmessages)) |
| 500 | `INTERNAL_SERVER_ERROR` | Error inesperado. Nunca trae detalle técnico |
| 502 | `EXTERNAL_SERVICE_ERROR` | El modelo del asistente falló o no respondió a tiempo (`field`: `server`). Ver [`POST /api/assistant/messages`](asistente.md#post-apiassistantmessages). También al registrar o modificar un movimiento, o al cambiar la moneda base en `PATCH /api/users/me`, cuando la moneda no tiene ninguna tasa de cambio guardada y el proveedor no respondió: no se guardó nada y se puede reintentar. Y en el registro y el login con Google cuando no se pudieron obtener las llaves de Google (`field`: `idToken`) |
| 503 | — | Solo en `/status`, cuando la base no responde (ver su sección) |

### Saldo no negativo

Una cuenta `CASH`, `DEBIT`, `SAVINGS`, `INVESTMENT` u `OTHER` no puede quedar por debajo de cero
(FA-133). Una tarjeta `CREDIT` sí: ahí el negativo es la deuda.

- Se mide sobre el `currentBalance`, el saldo vigente. Lo programado y lo pendiente no cuentan hasta
  que lo mueven.
- Solo se rechaza una petición que **baja** el saldo de la cuenta. Una cuenta que ya está en negativo
  —por datos anteriores a esta regla o por un programado que llegó a su fecha— sigue recibiendo
  dinero aunque no salga de cero, y no acepta salidas.
- El rechazo es 409 `INSUFFICIENT_FUNDS`, uno por cuenta afectada, y no cambia nada. La `description`
  nombra la cuenta y el saldo en que quedaría, y se puede mostrar tal cual:
  `"Saldo insuficiente en Nómina: quedaría en -50000 COP"`.

| Ruta | Se rechaza cuando… | `field` |
|---|---|---|
| `POST /api/transactions` | El efecto neto del lote deja la cuenta en negativo | `[i].amount` |
| `PATCH /api/transactions/{id}` | El movimiento resultante baja la cuenta por debajo de cero | `amount` |
| `DELETE /api/transactions/{id}` | Borrar un ingreso o una transferencia entrante deja la cuenta en negativo | `id` |
| `POST /api/transactions/{id}/approve` | Aprobar el pendiente deja la cuenta en negativo | `amount` |
| `POST /api/transactions/groups/{groupId}/approve` | Confirmar el grupo entero deja la cuenta en negativo | `[i].amount` |
| `PATCH /api/accounts/{id}` | Bajar `initialBalance` deja el saldo en negativo | `initialBalance` |

**No se valida** en lo que llega solo: un programado que alcanza su fecha, ni las ocurrencias que las
series crean al consultar. Esas pueden dejar una cuenta en negativo. Tampoco en el alta ni la
edición de una serie (ver [Series recurrentes](recurrentes.md#series-recurrentes)).

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
- Aprobar en una sola petición pendientes elegidos de grupos distintos: hoy se confirma un grupo
  entero, o un pendiente a la vez. Rechazarlos sí se puede (`POST /api/transactions/pending/reject`).
- Crear metas de gasto.
- Listar las sesiones abiertas, o cerrarlas todas sin cambiar la contraseña.
- Revocar el access en el logout: sigue sirviendo hasta vencer, como mucho una hora.
- Recuperar la contraseña olvidada (FA-90).
- Transferencias recurrentes, y cambiar el fin (`endDate`, `occurrences`) o el tipo de una serie.
- Listar las ocurrencias de una serie: se filtran por `recurrenceId` en `reports/transactions`.
- Cambiar el monto, el número de cuotas, la tarjeta o la fecha de una compra en cuotas, o recalcular
  sus cuotas si cambian los datos de la tarjeta. Tampoco valida que la compra quepa en el cupo.
- Listar las cuotas de una compra: se filtran por `installment.purchaseId` en `reports/transactions`.
- Listar las solicitudes de afiliación rechazadas, canceladas o desvinculadas.
- Cambiar las partes de un gasto compartido después del alta.
- Retirar una transferencia compartida: borrarla en los libros de quien la hizo no quita el ingreso
  pendiente de quien la recibe, que lo rechaza.
- Compartir una serie recurrente o una compra en cuotas.
