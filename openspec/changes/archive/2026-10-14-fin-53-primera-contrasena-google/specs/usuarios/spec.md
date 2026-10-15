## MODIFIED Requirements

### Requirement: Forma del perfil
`GET /api/users/me` y `PATCH /api/users/me` SHALL presentar el perfil como `{id, email, firstName,
lastName, phone, baseCurrencyCode, timezone, hasPassword, googleLinked}`. `hasPassword` SHALL ser
verdadero si el usuario tiene contraseña, y `googleLinked` si tiene una cuenta Google vinculada. La
respuesta MUST NOT incluir la contraseña, su hash ni el identificador de Google.

#### Scenario: Leer el perfil
- **WHEN** un usuario registrado con `phone` `3001234567` y sin apellido pide `GET /api/users/me` con su token
- **THEN** la respuesta es 200 con su `id`, `email`, `firstName`, `phone` `3001234567`, `lastName` en `null`, `baseCurrencyCode` y `timezone`, y sin `password` ni `passwordHash`

#### Scenario: Usuario registrado con contraseña
- **WHEN** un usuario registrado con correo y contraseña, que nunca entró con Google, pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `true` y `googleLinked` `false`

#### Scenario: Usuario registrado con Google
- **WHEN** un usuario registrado con Google pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `false` y `googleLinked` `true`, y no trae el identificador de Google

#### Scenario: Usuario con los dos métodos
- **WHEN** un usuario con contraseña cuya cuenta se vinculó a Google en su primer login con Google pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `true` y `googleLinked` `true`

#### Scenario: El PATCH trae los dos campos
- **WHEN** un usuario registrado con Google modifica su nombre con `PATCH /api/users/me`
- **THEN** la respuesta es 200 con `hasPassword` `false` y `googleLinked` `true`

### Requirement: El perfil es el del usuario del token
Las rutas de `/api/users/me` SHALL operar sobre el usuario del `sub` del token y SHALL exigir Bearer.
Sin credencial o con el Basic compartido SHALL responder 401 `UNAUTHENTICATED` en `authorization`.
Un token válido de un usuario desactivado o borrado SHALL responder 401 `UNAUTHENTICATED` en
`authorization`.

#### Scenario: Sin credenciales
- **WHEN** se pide `GET /api/users/me`, `PATCH /api/users/me`, `PUT /api/users/me/password` o `POST /api/users/me/password` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Con el Basic compartido
- **WHEN** se pide cualquiera de esas rutas con el Basic de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la cabecera `WWW-Authenticate: Bearer`

#### Scenario: Token de un usuario desactivado
- **WHEN** un usuario con `is_active` en falso, o con `deleted_at`, pide `GET /api/users/me` con un token aún vigente
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Un usuario sin contraseña no puede probar la actual
Para un usuario sin contraseña, `PUT /api/users/me/password` y un `PATCH /api/users/me` que cambie el
correo SHALL responder 409 `INVALID_STATE` en `currentPassword`, haya enviado o no `currentPassword`,
sin cambiar nada y sin cerrar sesiones. Es un error distinto del de contraseña equivocada: el usuario
define primero su contraseña con `POST /api/users/me/password`.

#### Scenario: Cambio de contraseña sin tener una
- **WHEN** un usuario registrado con Google pide `PUT /api/users/me/password` con cualquier `currentPassword` y una `newPassword` válida
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `currentPassword` y el access con el que se pidió sigue sirviendo

#### Scenario: Cambio de correo sin tener contraseña
- **WHEN** un usuario registrado con Google pide `PATCH /api/users/me` con un `email` distinto y cualquier `currentPassword`
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `currentPassword` y el correo no cambia

#### Scenario: Cambio de correo sin tener contraseña y sin enviarla
- **WHEN** un usuario registrado con Google pide `PATCH /api/users/me` con un `email` distinto y sin `currentPassword`
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `currentPassword`, no un `VALIDATION_ERROR`, y el correo no cambia

#### Scenario: Otros cambios del perfil sin contraseña
- **WHEN** un usuario registrado con Google pide `PATCH /api/users/me` con su mismo correo y un nombre nuevo
- **THEN** la respuesta es 200 con el nombre nuevo

## ADDED Requirements

### Requirement: Definir la primera contraseña
`POST /api/users/me/password` SHALL recibir `{idToken, newPassword}`, los dos obligatorios, y con un
ID token de Google aceptado SHALL guardar `newPassword` como la contraseña de un usuario que no tiene
ninguna y responder 204 sin cuerpo. `newPassword` sigue las reglas del alta: al menos 8 caracteres y
no más de 72 bytes en UTF-8. Desde ahí el usuario SHALL entrar también con correo y contraseña.

#### Scenario: Definición correcta
- **WHEN** un usuario registrado con Google envía un ID token recién emitido de su cuenta Google y una `newPassword` de 12 caracteres
- **THEN** la respuesta es 204, y el login con su correo y esa contraseña da 200

#### Scenario: El perfil refleja la contraseña
- **WHEN** después de definirla el usuario hace login y pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `true` y `googleLinked` `true`

#### Scenario: Sigue entrando con Google
- **WHEN** después de definirla el usuario hace login con Google
- **THEN** la respuesta es 200

#### Scenario: Contraseña nueva que no cumple las reglas
- **WHEN** `newPassword` tiene 7 caracteres o pasa de 72 bytes
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `newPassword`

#### Scenario: Faltan campos
- **WHEN** el cuerpo no trae `idToken` o `newPassword`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en cada campo que falta

### Requirement: La primera contraseña exige confirmar la identidad con Google
El ID token SHALL pasar la verificación de Google del login con Google, su emisión SHALL estar a 5
minutos o menos de la hora actual, antes o después, y su identificador SHALL ser el de la cuenta
Google vinculada al usuario del token de sesión. Si no, SHALL responder 400 `INVALID_CREDENTIALS` en `idToken`, sin decir cuál falló, sin
guardar nada y sin cerrar sesiones. Es 400 y no 401: el access con el que se pidió sigue siendo válido.

#### Scenario: Token que no es de Google
- **WHEN** el `idToken` no es un ID token de Google válido
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `idToken`, y el access con el que se pidió sigue sirviendo

#### Scenario: Token emitido hace más de 5 minutos
- **WHEN** el `idToken` es válido y de la cuenta vinculada pero se emitió hace 10 minutos
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `idToken` y la contraseña no se define

#### Scenario: Token con la emisión una hora adelante
- **WHEN** el `idToken` es válido y de la cuenta vinculada pero su emisión está una hora después de la hora actual
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `idToken` y la contraseña no se define

#### Scenario: Token de otra cuenta Google
- **WHEN** el `idToken` es válido y reciente pero de una cuenta Google distinta de la vinculada al usuario
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `idToken` y la contraseña no se define

### Requirement: Solo un usuario sin contraseña define la primera
Si el usuario ya tiene contraseña, `POST /api/users/me/password` SHALL responder 409 `INVALID_STATE`
en `newPassword`, sin verificar el ID token, sin cambiar la contraseña y sin cerrar sesiones. Ese
usuario cambia la suya con `PUT /api/users/me/password`.

#### Scenario: Usuario con contraseña
- **WHEN** un usuario registrado con correo y contraseña pide `POST /api/users/me/password` con un cuerpo válido
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `newPassword`, y su contraseña sigue siendo la misma

#### Scenario: Segunda definición
- **WHEN** un usuario que ya definió su primera contraseña vuelve a pedir `POST /api/users/me/password`
- **THEN** la respuesta es 409 con un error `INVALID_STATE` en el campo `newPassword`

### Requirement: Definir la primera contraseña cierra las sesiones
Una definición correcta SHALL cerrar todas las sesiones del usuario, como el cambio de contraseña: el
access con el que se pidió y todo access y refresh emitidos antes dejan de servir.

#### Scenario: El token de la definición deja de servir
- **WHEN** después de una definición correcta el usuario pide `GET /api/users/me` con el mismo access con el que la definió
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: El refresh anterior deja de servir
- **WHEN** después de una definición correcta se pide `POST /api/auth/refresh` con un refresh emitido antes
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `refreshToken`

### Requirement: Google no disponible al definir la primera contraseña
Si no se pueden obtener las llaves públicas de Google para verificar el ID token,
`POST /api/users/me/password` SHALL responder 502 `EXTERNAL_SERVICE_ERROR` en `idToken`, sin guardar
nada.

#### Scenario: Llaves inalcanzables
- **WHEN** las llaves de Google no responden al verificar el `idToken`
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `idToken`
