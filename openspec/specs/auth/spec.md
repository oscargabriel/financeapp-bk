# auth Specification

## Purpose
Cómo se abre, se renueva y se cierra una sesión del API, y cuándo deja de servir cada token: el access
que autentica las rutas Bearer y el refresh que lo renueva.

## Requirements

### Requirement: El login entrega un access y un refresh
`POST /api/auth/login` con credenciales correctas SHALL responder 200 con `accessToken`, `tokenType`
`Bearer`, `expiresIn`, `refreshToken` y `refreshExpiresIn`. `expiresIn` SHALL valer 3600 y
`refreshExpiresIn` 86400 con la configuración por defecto, los dos en segundos. El refresh SHALL ser
un valor opaco, distinto en cada login.

#### Scenario: Login correcto
- **WHEN** el usuario del escenario hace login con su correo y su contraseña
- **THEN** la respuesta es 200 con `tokenType` `Bearer`, `expiresIn` 3600, `refreshExpiresIn` 86400, un `accessToken` y un `refreshToken` no vacíos

#### Scenario: Dos logins dan refresh distintos
- **WHEN** el mismo usuario hace login dos veces seguidas
- **THEN** los dos `refreshToken` son distintos y los dos sirven para refrescar

### Requirement: Refrescar la sesión
`POST /api/auth/refresh` SHALL recibir `{refreshToken}` y, si el refresh está vigente, responder 200
con la misma forma del login: un access nuevo y un refresh nuevo. El refresh nuevo SHALL vencer en el
mismo instante que el que reemplaza, así que `refreshExpiresIn` nunca crece al refrescar.

#### Scenario: Refresco correcto
- **WHEN** se envía el `refreshToken` recién recibido del login
- **THEN** la respuesta es 200 con `tokenType` `Bearer`, `expiresIn` 3600, un `accessToken` y un `refreshToken` distintos de los anteriores, y `refreshExpiresIn` mayor que 0 y no mayor que el del login

#### Scenario: El access nuevo sirve
- **WHEN** después del refresco se pide `GET /api/users/me` con el `accessToken` nuevo
- **THEN** la respuesta es 200 con el perfil del mismo usuario

#### Scenario: El refresh nuevo sirve para el siguiente refresco
- **WHEN** se refresca con el `refreshToken` que devolvió el refresco anterior
- **THEN** la respuesta es 200 con otro par nuevo y el mismo vencimiento de la sesión

### Requirement: Un refresh que no está vigente se rechaza
`POST /api/auth/refresh` SHALL responder 401 `UNAUTHENTICATED` en el campo `refreshToken`, sin
emitir tokens, si el refresh no existe, ya venció, ya se usó, fue revocado, es de antes del último
cambio de contraseña del usuario o es de un usuario desactivado o borrado. La respuesta MUST NOT
decir cuál de esos casos fue.

#### Scenario: Refresh inexistente
- **WHEN** se envía un `refreshToken` que el API nunca emitió
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

#### Scenario: Refresh vencido
- **WHEN** se envía un refresh cuya sesión empezó hace más de 24 horas
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

#### Scenario: Refresh de un usuario desactivado
- **WHEN** se envía un refresh vigente de un usuario con `is_active` en falso o con `deleted_at`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

### Requirement: Reusar un refresh cierra la sesión
Un refresh SHALL servir una sola vez. Si se presenta uno que ya se usó, la respuesta SHALL ser 401
`UNAUTHENTICATED` en `refreshToken` y el sistema SHALL revocar también el refresh que lo reemplazó y
cualquier otro vigente de la misma sesión.

#### Scenario: Refresh ya rotado
- **WHEN** se refresca con un refresh y después se vuelve a enviar ese mismo refresh
- **THEN** el segundo envío responde 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

#### Scenario: El reemplazo queda revocado
- **WHEN** después de ese reuso se envía el refresh que devolvió el primer refresco
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

### Requirement: Cerrar sesión
`POST /api/auth/logout` SHALL recibir `{refreshToken}`, revocar ese refresh y todos los de su misma
sesión, y responder 204 sin cuerpo. SHALL responder 204 también si el refresh no existe, ya venció o
ya estaba revocado. El access de esa sesión no se revoca: sigue sirviendo hasta vencer.

#### Scenario: Logout correcto
- **WHEN** se envía el `refreshToken` vigente de una sesión
- **THEN** la respuesta es 204 sin cuerpo

#### Scenario: El refresh no sirve después del logout
- **WHEN** después del logout se refresca con ese mismo `refreshToken`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

#### Scenario: Logout de un refresh desconocido
- **WHEN** se envía un `refreshToken` que el API nunca emitió
- **THEN** la respuesta es 204 sin cuerpo

### Requirement: Cuerpo del refresco y del logout
`POST /api/auth/refresh` y `POST /api/auth/logout` SHALL exigir `refreshToken` no vacío. Sin él
SHALL responder 400 `VALIDATION_ERROR` en el campo `refreshToken`, sin tocar ninguna sesión.

#### Scenario: Refresco sin refreshToken
- **WHEN** se pide `POST /api/auth/refresh` con `{}` o con `"refreshToken": "  "`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `refreshToken`

#### Scenario: Logout sin refreshToken
- **WHEN** se pide `POST /api/auth/logout` con `{}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `refreshToken`

### Requirement: Refresco y logout usan la credencial Basic
`POST /api/auth/refresh` y `POST /api/auth/logout` SHALL exigir la credencial Basic compartida, como
el login. Sin ella, o con un Bearer, SHALL responder 401 `UNAUTHENTICATED` en `authorization` con la
cabecera `WWW-Authenticate: Basic`, sin tocar ninguna sesión.

#### Scenario: Sin credencial
- **WHEN** se pide `POST /api/auth/refresh` o `POST /api/auth/logout` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la cabecera `WWW-Authenticate: Basic`

#### Scenario: Con un Bearer válido
- **WHEN** se pide `POST /api/auth/refresh` con un refresh vigente y un `Authorization: Bearer` válido en vez del Basic
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y el refresh sigue sirviendo

### Requirement: El refresh no se guarda en claro
El sistema MUST NOT guardar el valor del refresh en la base de datos: SHALL guardar solo un hash que
no permita reconstruirlo.

#### Scenario: La base no tiene el refresh
- **WHEN** después de un login se buscan en `finance.refresh_tokens` las filas del usuario
- **THEN** ninguna columna contiene el `refreshToken` que recibió el cliente

### Requirement: Un access solo sirve mientras su sesión siga abierta
Toda ruta Bearer SHALL responder 401 `UNAUTHENTICATED` en `authorization` a un access con firma,
emisor y vencimiento válidos si su usuario está desactivado o borrado, si el usuario cambió la
contraseña después de emitirlo, o si el token no trae la marca de sesión que emite el login.

#### Scenario: Access de antes del cambio de contraseña
- **WHEN** un usuario cambia su contraseña y después pide `GET /api/accounts` con el access que tenía antes del cambio
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Access de un usuario desactivado en cualquier ruta
- **WHEN** un usuario con `is_active` en falso pide `GET /api/accounts` con un access aún vigente
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Access sin marca de sesión
- **WHEN** se pide una ruta Bearer con un token firmado con la clave y el emisor del API, sin vencer, pero sin la marca de sesión
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: El cambio de contraseña cierra las sesiones
Tras un `PUT /api/users/me/password` correcto, todo refresh emitido antes del cambio SHALL responder
401 `UNAUTHENTICATED` en `refreshToken` desde ese momento, y un login nuevo con la contraseña nueva
SHALL abrir una sesión que funciona.

#### Scenario: Refresh de antes del cambio
- **WHEN** un usuario cambia su contraseña y después refresca con el refresh que tenía antes del cambio
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

#### Scenario: Sesión nueva tras el cambio
- **WHEN** después del cambio el usuario hace login con la contraseña nueva y refresca con el refresh recibido
- **THEN** las dos respuestas son 200

### Requirement: Registro con Google
`POST /api/auth/google/register` SHALL recibir `{idToken, timezone}`, con `timezone` opcional, y con
un ID token válido de un correo admitido y sin cuenta SHALL crear un usuario sin contraseña asociado
al identificador de Google del token. SHALL responder 201 con la misma forma que el login: un access
y un refresh de una sesión nueva.

#### Scenario: Registro correcto
- **WHEN** se registra un ID token válido de un correo `@bruno.local` sin cuenta, con `timezone` `Europe/Madrid`
- **THEN** la respuesta es 201 con `tokenType` `Bearer`, `expiresIn` 3600, `refreshExpiresIn` 86400, un `accessToken` y un `refreshToken` no vacíos

#### Scenario: El usuario nuevo tiene los datos de Google
- **WHEN** después del registro se pide `GET /api/users/me` con el `accessToken` recibido
- **THEN** la respuesta es 200 con el correo del token en minúsculas, el nombre y el apellido de Google, `timezone` `Europe/Madrid` y `baseCurrencyCode` `COP`

#### Scenario: El usuario nuevo tiene las categorías por defecto
- **WHEN** después del registro se pide `GET /api/categories` con el `accessToken` recibido
- **THEN** la respuesta trae las categorías por defecto

#### Scenario: Registro sin zona
- **WHEN** se registra un ID token válido sin `timezone`
- **THEN** el usuario queda con `timezone` `America/Bogota`

#### Scenario: Google sin nombre de pila
- **WHEN** el ID token no trae nombre de pila ni nombre completo
- **THEN** el usuario queda con la parte del correo anterior a la `@` como nombre

### Requirement: El registro con Google pasa por la lista de admitidos
El registro con Google SHALL aplicar la misma lista de correos admitidos que el alta normal, sobre el
correo del ID token. Un correo no admitido SHALL recibir 403 `REGISTRATION_NOT_ALLOWED` en `email`,
aunque ya tenga cuenta, sin crear nada.

#### Scenario: Correo no admitido
- **WHEN** se registra un ID token válido de un correo que no está en la lista
- **THEN** la respuesta es 403 con un error `REGISTRATION_NOT_ALLOWED` en el campo `email`

### Requirement: El registro con Google no duplica cuentas
Si el correo del ID token ya es de un usuario no borrado, o su identificador de Google ya está
asociado a uno, el registro SHALL responder 409 `DUPLICATE_RESOURCE` en `email`, sin crear ni
vincular nada.

#### Scenario: Correo ya registrado
- **WHEN** se registra un ID token válido cuyo correo ya tiene cuenta con contraseña
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `email`

#### Scenario: Google ya registrado
- **WHEN** se registra por segunda vez el mismo ID token que ya se registró
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `email`

### Requirement: Login con Google
`POST /api/auth/google/login` SHALL recibir `{idToken}` y, si el identificador de Google del token es
de un usuario activo, responder 200 con la misma forma del login. El usuario SHALL buscarse por ese
identificador y no por el correo. La lista de admitidos no se consulta.

#### Scenario: Login correcto
- **WHEN** un usuario registrado con Google hace login con un ID token de la misma cuenta Google
- **THEN** la respuesta es 200 con `tokenType` `Bearer`, `expiresIn` 3600, `refreshExpiresIn` 86400, un `accessToken` y un `refreshToken` no vacíos

#### Scenario: El correo cambió después del registro
- **WHEN** un usuario con contraseña y cuenta Google vinculada cambia su correo en el perfil y después hace login con un ID token de la misma cuenta Google, que trae el correo anterior
- **THEN** la respuesta es 200 y el `accessToken` recibido abre el perfil de ese usuario

### Requirement: Login con Google sin cuenta
Si ningún usuario activo tiene el identificador de Google del token, ni hay un usuario activo con
ese correo que se pueda vincular, el login con Google SHALL responder 404 `ACCOUNT_NOT_REGISTERED`
en `idToken`, sin crear nada. Un usuario desactivado o borrado cuenta como inexistente.

#### Scenario: Google sin cuenta
- **WHEN** se hace login con un ID token válido de un correo sin cuenta
- **THEN** la respuesta es 404 con un error `ACCOUNT_NOT_REGISTERED` en el campo `idToken`

#### Scenario: Usuario desactivado
- **WHEN** se hace login con un ID token válido cuyo correo es el de un usuario con `is_active` en falso
- **THEN** la respuesta es 404 con un error `ACCOUNT_NOT_REGISTERED` en el campo `idToken`

### Requirement: La primera entrada con Google vincula la cuenta del mismo correo
Si no hay usuario con el identificador de Google del token pero sí un usuario activo con ese correo
y sin cuenta Google, el login con Google SHALL asociarle ese identificador y responder 200. Desde
ahí el usuario SHALL entrar con los dos métodos. Si ese usuario ya tiene otra cuenta Google, SHALL
responder 409 `DUPLICATE_RESOURCE` en `email` sin cambiar nada.

#### Scenario: Vinculación automática
- **WHEN** un usuario registrado con contraseña hace login con un ID token válido de su mismo correo
- **THEN** la respuesta es 200 con la forma del login

#### Scenario: La contraseña sigue sirviendo tras vincular
- **WHEN** después de la vinculación ese usuario hace login con su correo y su contraseña
- **THEN** la respuesta es 200

#### Scenario: Correo vinculado a otra cuenta Google
- **WHEN** se hace login con un ID token válido de otra cuenta Google cuyo correo es el de un usuario ya vinculado
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `email`

### Requirement: El ID token de Google se verifica
El registro y el login con Google SHALL aceptar solo un ID token con firma de una llave pública de
Google, emisor de Google, `aud` igual al client ID configurado, sin vencer y con `email_verified`
verdadero. Cualquier otro SHALL recibir 401 `INVALID_CREDENTIALS` en `idToken`, sin crear ni
vincular nada.

#### Scenario: Token que no es de Google
- **WHEN** se registra o se hace login con un `idToken` que no es un ID token de Google válido
- **THEN** la respuesta es 401 con un error `INVALID_CREDENTIALS` en el campo `idToken`

#### Scenario: Token para otro client ID
- **WHEN** el ID token está firmado por Google pero su `aud` es otro client ID
- **THEN** la respuesta es 401 con un error `INVALID_CREDENTIALS` en el campo `idToken`

#### Scenario: Token vencido
- **WHEN** el ID token está firmado por Google para el client ID configurado, pero ya venció
- **THEN** la respuesta es 401 con un error `INVALID_CREDENTIALS` en el campo `idToken`

#### Scenario: Correo sin verificar
- **WHEN** el ID token es válido pero trae `email_verified` en falso
- **THEN** la respuesta es 401 con un error `INVALID_CREDENTIALS` en el campo `idToken`

### Requirement: Google no disponible
Si no se pueden obtener las llaves públicas de Google para verificar el ID token, el registro y el
login con Google SHALL responder 502 `EXTERNAL_SERVICE_ERROR` en `idToken`, sin crear ni vincular
nada.

#### Scenario: Llaves inalcanzables
- **WHEN** se hace login con Google y el servicio de llaves de Google no responde
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `idToken`

### Requirement: Cuerpo del registro y del login con Google
El registro y el login con Google SHALL exigir `idToken` no vacío. Sin él SHALL responder 400
`VALIDATION_ERROR` en `idToken`. En el registro, un `timezone` que no sea una zona IANA SHALL
responder 400 `VALIDATION_ERROR` en `timezone`.

#### Scenario: Sin idToken
- **WHEN** se pide `POST /api/auth/google/login` o `POST /api/auth/google/register` con `{}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `idToken`

#### Scenario: Zona inválida
- **WHEN** se registra con Google con `timezone` `Marte/Olympus`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `timezone`

### Requirement: El registro y el login con Google usan la credencial Basic
`POST /api/auth/google/register` y `POST /api/auth/google/login` SHALL exigir la credencial Basic
compartida. Sin ella, o con un Bearer, SHALL responder 401 `UNAUTHENTICATED` en `authorization` con
la cabecera `WWW-Authenticate: Basic`.

#### Scenario: Sin credencial
- **WHEN** se pide `POST /api/auth/google/login` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la cabecera `WWW-Authenticate: Basic`

#### Scenario: Con un Bearer válido
- **WHEN** se pide `POST /api/auth/google/register` con un `Authorization: Bearer` válido en vez del Basic
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Las sesiones de Google son como las del login
Los tokens del registro y del login con Google SHALL refrescarse, cerrarse y vencer igual que los del
login con contraseña.

#### Scenario: Refrescar una sesión de Google
- **WHEN** se refresca con el `refreshToken` recibido del login con Google
- **THEN** la respuesta es 200 con un par nuevo

#### Scenario: Cerrar una sesión de Google
- **WHEN** se cierra sesión con el `refreshToken` de un login con Google y después se refresca con él
- **THEN** el logout responde 204 y el refresco 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

### Requirement: Login con contraseña de un usuario sin contraseña
`POST /api/auth/login` con el correo de un usuario que no tiene contraseña SHALL responder 401
`INVALID_CREDENTIALS` en `credentials`, igual que una contraseña incorrecta.

#### Scenario: Usuario registrado solo con Google
- **WHEN** un usuario registrado con Google hace login con su correo y cualquier contraseña
- **THEN** la respuesta es 401 con un error `INVALID_CREDENTIALS` en el campo `credentials`
