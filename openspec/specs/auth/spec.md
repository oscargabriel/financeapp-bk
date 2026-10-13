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
