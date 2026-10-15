# Contrato del API — auth

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

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
| `phone` | string | no | Formato internacional E.164: `+`, un indicativo que no empieza por 0 y de 7 a 15 dígitos en total, sin espacios ni separadores (`+573001234567`). Se recorta |

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
   `baseCurrencyCode`. Un `phone` sin `+` o con separadores (`3001234567`, `+57 300 123 4567`) sale
   en `phone` con la descripción `El celular va en formato internacional: + e indicativo, de 7 a 15
   digitos en total, sin espacios ni separadores`, la misma en el alta y en el perfil (FIN-59).
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

Cambia correo y contraseña por una sesión: un access y un refresh. **Basic** (la credencial
compartida va en la cabecera y la del usuario en el cuerpo).

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
  "expiresIn": 3600,
  "refreshToken": "q3Jf8m0W1x...",
  "refreshExpiresIn": 86400
}
```

| Campo | Qué es |
|---|---|
| `accessToken` | El JWT para `Authorization: Bearer` |
| `expiresIn` | Segundos que le quedan al access |
| `refreshToken` | Valor opaco de 43 caracteres para [`POST /auth/refresh`](#post-apiauthrefresh) y [`POST /auth/logout`](#post-apiauthlogout). No es un JWT: no se decodifica. Es una credencial, y no debe ir a logs ni a la URL |
| `refreshExpiresIn` | Segundos que le quedan a la sesión. Recién hecho el login, 86400 |

Cada login abre una sesión nueva e independiente: las de otros dispositivos siguen abiertas.

**Errores**

- 400 `VALIDATION_ERROR` si falta `email` o `password`.
- 401 `INVALID_CREDENTIALS`, `field: "credentials"`, "Correo o contrasena incorrectos". Sale igual
  si el correo no existe, si la contraseña está mal o si el usuario está desactivado: así el login
  no sirve para averiguar qué correos están registrados. El cliente debe mostrar un único mensaje
  genérico.

El login **no consulta la lista de admitidos** del registro. Si un correo sale de la lista, el usuario
que ya se registró con él sigue entrando; para cortarle el acceso se desactiva el usuario.

Un usuario registrado con Google que no ha definido contraseña recibe el mismo 401
`INVALID_CREDENTIALS` con cualquier contraseña: entra con [`POST /auth/google/login`](#post-apiauthgooglelogin),
y desde ahí puede definir una con [`POST /users/me/password`](usuarios.md#post-apiusersmepassword).

### Google Identity Services

El registro y el login con Google reciben el **ID token** que entrega Google Identity Services en el
navegador (`credential` en la respuesta del botón «Continuar con Google» o de One Tap), configurado
con el **client ID** del servicio. Es un JWT que el API verifica con las llaves públicas de Google:

- firma de Google y emisor `accounts.google.com`;
- `aud` igual al client ID del servicio: un token pedido para otra aplicación no sirve;
- sin vencer (Google los emite por una hora);
- `email_verified` verdadero.

Si falla cualquiera, la respuesta es 401 `INVALID_CREDENTIALS` en `idToken`, sin decir cuál, y no se
crea ni se vincula nada. El cliente pide otro token a Google y vuelve a intentar.

El API identifica la cuenta de Google por su identificador estable (`sub`), no por el correo: el
correo del usuario puede cambiar después sin que el login con Google deje de funcionar.

### `POST /api/auth/google/register`

Crea un usuario **sin contraseña** con su cuenta de Google, le copia las categorías por defecto y le
abre la sesión. **Basic.** Pasa por la misma lista de admitidos que
[`POST /auth/register`](#post-apiauthregister).

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `idToken` | string | sí | El ID token de Google Identity Services |
| `timezone` | string | no | Zona IANA. Por defecto `America/Bogota` |

```json
{ "idToken": "eyJhbGciOiJSUzI1NiIs...", "timezone": "America/Bogota" }
```

**201 Created**, con la misma forma que [`POST /auth/login`](#post-apiauthlogin): `accessToken`,
`tokenType`, `expiresIn`, `refreshToken` y `refreshExpiresIn`. La sesión es como la del login: se
refresca, se cierra y vence igual.

El usuario queda con el correo de Google en minúsculas, el nombre de pila y el apellido de Google
(sin nombre de pila, el nombre completo; sin ninguno, la parte del correo antes de la `@`), la
zona enviada y la moneda `COP`. Sin celular. Todo se cambia luego con
[`PATCH /users/me`](usuarios.md#patch-apiusersme), salvo el correo mientras no tenga contraseña (ver allí).

**Errores**, en el orden en que se resuelven:

1. 400 `VALIDATION_ERROR` en `idToken` si falta, o en `timezone` si no es una zona IANA.
2. 401 `INVALID_CREDENTIALS` en `idToken` si el token no sirve.
3. 502 `EXTERNAL_SERVICE_ERROR` en `idToken` si no se pudieron obtener las llaves de Google. Se puede
   reintentar.
4. 403 `REGISTRATION_NOT_ALLOWED` en `email` si el correo de Google no está admitido. Sale aunque ya
   tenga cuenta, como en el alta.
5. 409 `DUPLICATE_RESOURCE` en `email` si el correo ya es de un usuario, con contraseña o con Google,
   o si esa cuenta de Google ya tiene usuario. **No vincula**: el cliente ofrece el login con Google,
   que es el que vincula.

### `POST /api/auth/google/login`

Cambia el ID token de Google por una sesión. **Basic.** Como el login con contraseña, **no consulta
la lista de admitidos**.

**Cuerpo**

| Campo | Tipo | Obligatorio |
|---|---|---|
| `idToken` | string | sí |

```json
{ "idToken": "eyJhbGciOiJSUzI1NiIs..." }
```

**200 OK**, con la misma forma que [`POST /auth/login`](#post-apiauthlogin).

Busca al usuario por su cuenta de Google. Si no la encuentra pero hay un usuario activo con el
correo del token **sin cuenta de Google**, se la vincula y entra: desde ahí ese usuario entra con su
contraseña y con Google.

**Errores**

- 400 `VALIDATION_ERROR` en `idToken` si falta.
- 401 `INVALID_CREDENTIALS` en `idToken` si el token no sirve.
- 502 `EXTERNAL_SERVICE_ERROR` en `idToken` si no se pudieron obtener las llaves de Google.
- 404 `ACCOUNT_NOT_REGISTERED` en `idToken` si no hay usuario para esa cuenta de Google ni para su
  correo. Un usuario desactivado o borrado cuenta como inexistente. El cliente ofrece el registro.
  Decirlo no revela qué correos están registrados: solo lo recibe quien presenta un ID token válido
  de esa cuenta.
- 409 `DUPLICATE_RESOURCE` en `email` si el usuario de ese correo ya está vinculado a **otra** cuenta
  de Google. No se vincula nada.

### `POST /api/auth/refresh`

Cambia un refresh vigente por un access y un refresh nuevos (FIN-47). **Basic**, como el login: el
access puede estar vencido justo cuando hace falta refrescar.

**Cuerpo**

| Campo | Tipo | Obligatorio |
|---|---|---|
| `refreshToken` | string | sí |

```json
{ "refreshToken": "q3Jf8m0W1x..." }
```

**200 OK** — la misma forma que el login. `refreshExpiresIn` es lo que le queda a la sesión, que no
se alarga: el refresh nuevo vence cuando vencía el anterior, a las 24 horas del login.

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "refreshToken": "Zt0b2Lr9pE...",
  "refreshExpiresIn": 72000
}
```

El refresh enviado queda usado: el siguiente refresco va con el nuevo.

**Errores**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken` | Falta o viene en blanco |
| 401 | `UNAUTHENTICATED` | `refreshToken` | El refresh no existe, venció, ya se usó, fue revocado, es de antes del último cambio de contraseña o su usuario está desactivado. La respuesta no dice cuál: el cliente vuelve al login |

**Reuso.** Si el refresh ya se había usado, además del 401 se cierra la sesión entera: el refresh
que lo reemplazó también deja de servir. Es la señal de que alguien más lo tiene, y así el legítimo
y el que lo copió vuelven los dos al login. Por lo mismo, **el cliente no debe mandar dos refrescos
a la vez** con el mismo refresh: el segundo cuenta como reuso.

### `POST /api/auth/logout`

Cierra la sesión del refresh (FIN-47). **Basic.**

**Cuerpo**

| Campo | Tipo | Obligatorio |
|---|---|---|
| `refreshToken` | string | sí |

**204 No Content** — sin cuerpo. El refresh y la sesión a la que pertenece quedan revocados. Responde
204 también si el refresh no existe, ya venció o ya estaba revocado: no hay nada que el cliente
pueda corregir. El access de esa sesión **no** se revoca y sigue sirviendo hasta vencer, como mucho
una hora; el cliente lo descarta. Las sesiones de otros dispositivos siguen abiertas.

**Errores**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `refreshToken` | Falta o viene en blanco |

### `GET /api/status`

Estado del servicio y de sus dependencias. **Basic.** Sirve para monitoreo y para despertar el
servicio: el front lo llama al abrir el login para que la instancia de Cloud Run y la base arranquen
en frío antes de que el usuario envíe sus credenciales. Ese uso ignora la respuesta, sea 200 o 503.

**200 OK** — todo arriba:

```json
{ "status": "UP", "services": { "postgres": "UP" } }
```

**503 Service Unavailable** — mismo cuerpo con `"status": "DOWN"` y el servicio caído en `DOWN`.
Cada dependencia tiene 2 segundos para responder antes de contarse como caída.
