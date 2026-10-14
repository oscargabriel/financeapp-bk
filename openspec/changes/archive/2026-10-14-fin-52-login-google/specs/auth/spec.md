## ADDED Requirements

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
