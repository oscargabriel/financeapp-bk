# Contrato del API — usuarios

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `GET /api/users/me`

El perfil del usuario del token. **Bearer.**

**200 OK**

```json
{
  "id": "0199a1b2-3c4d-7e5f-8a9b-0c1d2e3f4a5b",
  "email": "ana@correo.com",
  "firstName": "Ana",
  "lastName": "Pérez",
  "phone": "+573001234567",
  "baseCurrencyCode": "COP",
  "timezone": "America/Bogota",
  "hasPassword": true,
  "googleLinked": false
}
```

`lastName` y `phone` pueden venir en `null`. La respuesta nunca trae la contraseña, su hash ni el
identificador de la cuenta Google.

Los dos últimos campos dicen con qué métodos entra el usuario (FIN-53):

| `hasPassword` | `googleLinked` | El usuario | Qué ofrece el cliente |
|---|---|---|---|
| `true` | `false` | Se registró con correo y contraseña y nunca entró con Google | Cambiar la contraseña |
| `false` | `true` | Se registró con Google | Definir la primera contraseña con [`POST /users/me/password`](#post-apiusersmepassword) |
| `true` | `true` | Tiene los dos: la primera entrada con Google vinculó su cuenta, o definió una contraseña | Cambiar la contraseña |

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
| `phone` | string | Las reglas del alta: `+` e indicativo (`+573001234567`). **En blanco lo borra** |
| `timezone` | string | Zona IANA, no en blanco |
| `baseCurrencyCode` | string | Código de 3 letras de una moneda **activa** del catálogo, no en blanco. Sin distinguir mayúsculas; se guarda en mayúsculas |
| `currentPassword` | string | La contraseña actual. Solo se mira si el correo cambia |

- **Ausente o `null` es "no cambia".** Para vaciar el apellido o el celular se manda el texto en
  blanco (`""`).
- Un parche sin ningún campo modificable es 400 en `body`. `currentPassword` no cuenta;
  `baseCurrencyCode` sí: `{"baseCurrencyCode": "USD"}` solo es un parche válido.
- El correo cuenta como cambiado si difiere del guardado después de recortarlo y pasarlo a
  minúsculas. Mandar el mismo en otra caja no exige la contraseña.
- Cambiar el correo no invalida el token: sigue sirviendo hasta que vence.

**Cambiar la moneda base** (FIN-29). Es la moneda en que la persona ve todos sus totales: los
reportes, el saldo, el gasto mensual con sus metas y el asistente pasan a darla desde la respuesta
siguiente. No reescribe nada: cada movimiento conserva su monto, y los totales se reconvierten con
la tasa de la fecha de cada movimiento. Los saldos de las cuentas siguen en la moneda de cada cuenta.

- Mandar la moneda que ya tiene es 200 sin efecto: no consulta el catálogo ni las tasas.
- Antes de guardar una moneda distinta de la actual y de `USD`, el API asegura que exista una tasa
  `USD→<moneda>`, con las mismas reglas que `GET /api/exchange-rates` para hoy. Si no hay ninguna
  guardada y el proveedor no responde, es 502 y no se aplica nada del parche.

**Orden de los errores.** Sale solo el primer paso que falle, y ningún error aplica nada:

1. Formato de todos los campos, a la vez (400 en cada campo).
2. Parche vacío (400 en `body`).
3. Moneda inexistente o inactiva (400 en `baseCurrencyCode`).
4. Si el correo cambia: que el usuario tenga contraseña (409 en `currentPassword`), que la haya
   mandado y sea la correcta (400 en `currentPassword`) y que el correo esté libre (409 en `email`).
5. Tasa de la moneda nueva (502 en `server`).

```json
{
  "firstName": "Ana María",
  "phone": "+573109876543",
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
| 400 | `VALIDATION_ERROR` | `baseCurrencyCode` | La moneda no existe en el catálogo o está inactiva |
| 400 | `VALIDATION_ERROR` | `currentPassword` | El correo cambia y no vino la contraseña actual |
| 400 | `INVALID_CREDENTIALS` | `currentPassword` | El correo cambia y la contraseña actual no es correcta |
| 409 | `INVALID_STATE` | `currentPassword` | El correo cambia y el usuario no tiene contraseña (`hasPassword: false`), la mande o no |
| 409 | `DUPLICATE_RESOURCE` | `email` | Otro usuario ya usa ese correo, sin distinguir mayúsculas |
| 502 | `EXTERNAL_SERVICE_ERROR` | `server` | La moneda nueva no tiene ninguna tasa guardada y el proveedor no respondió. Se puede reintentar |

**La contraseña equivocada es 400, no 401.** El token es válido: un 401 haría que el cliente cerrara
la sesión de alguien que solo se equivocó al escribir. Se verifica antes que el correo, así que con
la contraseña mal nunca se sabe si el correo nuevo estaba libre.

**Un usuario sin contraseña no puede cambiar el correo** (FIN-53): recibe 409 `INVALID_STATE` en
`currentPassword`, mande o no `currentPassword`, distinto del 400 de la contraseña equivocada. Primero
define una con [`POST /users/me/password`](#post-apiusersmepassword). El resto del perfil lo modifica
sin contraseña.

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
anterior, y **todas las sesiones del usuario quedan cerradas** (FIN-47): todo access y todo refresh
emitido antes del cambio, incluido el access con el que se pidió, responde 401. El 204 no trae
tokens nuevos: el cliente vuelve al login. Un cambio rechazado no cierra ninguna sesión.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | el campo | Falta una de las dos, o la nueva no cumple las reglas |
| 400 | `INVALID_CREDENTIALS` | `currentPassword` | La contraseña actual no es correcta. 400 y no 401, por lo mismo que en el `PATCH` |
| 409 | `INVALID_STATE` | `currentPassword` | El usuario no tiene contraseña (`hasPassword: false`): la define con [`POST /users/me/password`](#post-apiusersmepassword). No cierra sesiones |

### `POST /api/users/me/password`

Define la **primera** contraseña de un usuario que no tiene ninguna, el que se registró con Google
(FIN-53). **Bearer.** Para que una sesión ajena abierta (un equipo prestado, un token robado) no
baste para quedarse con acceso permanente, exige además confirmar la identidad con Google **en ese
momento**.

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `idToken` | string | sí | Un ID token de Google Identity Services de la cuenta vinculada al usuario, recién emitido (ver abajo) |
| `newPassword` | string | sí | Las reglas del alta: al menos 8 caracteres y no más de 72 bytes en UTF-8 |

```json
{ "idToken": "eyJhbGciOiJSUzI1NiIs...", "newPassword": "unaClaveLarga" }
```

El ID token se obtiene como en el login con Google (ver
[Google Identity Services](auth.md#google-identity-services)) y se verifica igual. Además:

- su emisión (`iat`) tiene que estar a **5 minutos o menos** de la hora del servidor, antes o
  después. El cliente le pide el token a Google **justo al enviar** el formulario, no al abrirlo:
  el token con el que el usuario inició sesión ya no sirve;
- tiene que ser de la cuenta Google vinculada al usuario de la sesión. Cuenta el identificador de
  Google, no el correo.

**204 No Content**, sin cuerpo. Desde ese momento el usuario entra también con su correo y esa
contraseña, y sigue entrando con Google. Como el cambio de contraseña, **cierra todas las sesiones
del usuario**, incluida la del access con el que se pidió: el cliente vuelve al login, con Google o
con la contraseña nueva. Un rechazo no cierra ninguna.

**Errores**, en el orden en que se resuelven:

1. 400 `VALIDATION_ERROR` en `idToken` si falta, y en `newPassword` si falta o no cumple las reglas.
2. 409 `INVALID_STATE` en `newPassword` si el usuario ya tiene contraseña: la cambia con
   [`PUT /users/me/password`](#put-apiusersmepassword) y la actual. Sale sin consultar a Google.
3. 502 `EXTERNAL_SERVICE_ERROR` en `idToken` si no se pudieron obtener las llaves de Google. Se puede
   reintentar.
4. 400 `INVALID_CREDENTIALS` en `idToken` si el token no es válido, se emitió fuera de los 5 minutos
   o es de otra cuenta Google, sin decir cuál. **400 y no 401**: la sesión sigue siendo válida, y un
   401 haría que el cliente la cerrara.
