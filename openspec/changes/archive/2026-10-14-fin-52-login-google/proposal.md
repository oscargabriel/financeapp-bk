# Proposal

Issue **FIN-52** del tablero, epic **FIN-8** (Login con Google). Depende de FIN-47, ya entregada: los
tokens de Google salen por la misma emisión de access y refresh con versión de sesión. Lo consumen
FIN-53 (definir una contraseña confirmando con Google) y FIN-54 (los botones del front).

## Why

Hoy solo se entra con correo y contraseña, y `finance.users.password_hash` es `NOT NULL`: un usuario
que solo tenga Google no cabe en la base. El epic pide Google como segundo método, con los mismos
tokens y el mismo refresco.

Este change aplica estas decisiones del epic FIN-8, todas del 12-10-2026:

- El registro con Google pasa por la lista de correos admitidos y deja la sesión iniciada de una vez.
- Para iniciar sesión con Google hay que estar registrado.
- Si ya existe una cuenta con contraseña y el mismo correo, la primera entrada con Google la vincula
  automáticamente.
- El front usa Google Identity Services, que entrega un ID token firmado, y el back lo verifica. Solo
  hace falta el client ID, que es público.
- El OAuth Client ID lo crea el usuario en Google Cloud Console, fuera de los repos.

## What Changes

- **`POST /api/auth/google/register`** (Basic, como el alta) recibe `{idToken, timezone?}`. Verifica
  el token, pasa el correo por la lista de admitidos y crea el usuario **sin contraseña**. El usuario
  queda con el nombre y el apellido de Google, la zona enviada (`America/Bogota` si falta) y `COP`, y
  con las categorías por defecto. Responde **201** con la misma forma del login: `{accessToken,
  tokenType, expiresIn, refreshToken, refreshExpiresIn}`.
- **`POST /api/auth/google/login`** (Basic) recibe `{idToken}` y responde 200 con la forma del login.
  Busca al usuario por su identificador de Google (`sub`). Si no lo encuentra, busca un usuario
  activo con el correo del token y sin cuenta Google, y lo vincula.
- **El ID token se verifica** con las llaves públicas de Google: firma, emisor, `aud` igual al client
  ID configurado, vencimiento y `email_verified`. Si algo falla, responde 401 `INVALID_CREDENTIALS`
  en `idToken` y no crea ni vincula nada.
- Errores nuevos para el front:
  - Login de un Google sin cuenta: **404 `ACCOUNT_NOT_REGISTERED`** en `idToken`, código nuevo en
    `ErrorCodes`. El front ofrece el registro.
  - Registro con un correo o un Google que ya tienen cuenta: 409 `DUPLICATE_RESOURCE` en `email`,
    como el alta normal. El front ofrece el login.
  - Correo no admitido en el registro: 403 `REGISTRATION_NOT_ALLOWED` en `email`.
  - Login con un correo que ya está vinculado a otra cuenta Google: 409 `DUPLICATE_RESOURCE` en
    `email`, sin vincular nada.
  - Si no se pueden obtener las llaves de Google: 502 `EXTERNAL_SERVICE_ERROR`, como las tasas de
    cambio.
- **El login con contraseña de un usuario sin contraseña** responde 401 `INVALID_CREDENTIALS`, igual
  que cualquier credencial incorrecta.
- **Mientras llega FIN-53**, `PUT /api/users/me/password` y el cambio de correo de `PATCH
  /api/users/me` responden a un usuario sin contraseña lo mismo que a una contraseña actual
  equivocada: 400 `INVALID_CREDENTIALS` en `currentPassword`. Hoy esos dos caminos fallarían con un
  500 al leer un hash nulo. FIN-53 los reemplaza por su propio error y por la vía de definir la
  primera contraseña.
- **Esquema**: `password_hash` admite nulo, columna nueva `google_sub` con único parcial entre los no
  borrados, y un `CHECK` que exige al menos uno de los dos métodos. Van en `schema.sql` y en el update
  `docs/database/update/20261014_01_login_google.sql`.
- **Configuración**: `google.client-id` (`GOOGLE_CLIENT_ID`, **sin default**, como los secretos) y
  `google.proveedor` (`GOOGLE_PROVEEDOR`, `google` por defecto). Con `stub`, fuera de `prod`, un
  verificador de mentira deja a `bruno/` probar el flujo sin Google. `verificar-bruno.ps1` lo
  enciende como los otros dos stubs.
- **BREAKING para el despliegue**: Cloud Run necesita `GOOGLE_CLIENT_ID` antes de llevar esto a
  `main`, o la app no arranca. Hace falta crear antes el OAuth Client ID, que es el paso manual del
  epic.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `auth`: registro e inicio de sesión con Google, verificación del ID token, vinculación con una
  cuenta existente y el login con contraseña de un usuario que no la tiene.
- `usuarios`: qué responden el cambio de contraseña y el cambio de correo a un usuario sin
  contraseña, mientras FIN-53 no lo cambie.

## Fuera de alcance

- **Definir la primera contraseña, y mostrar en el perfil si el usuario tiene contraseña y Google
  vinculado.** Es FIN-53.
- **Los botones del front y la carga de Google Identity Services.** Es FIN-54.
- **Desvincular una cuenta Google, o vincularla a mano desde el perfil.** El epic no lo pide.
- **Actualizar el nombre o el correo del usuario cuando cambien en Google.** El registro los copia
  una vez. Después, el correo de la cuenta y el de Google pueden diferir: el login busca por `sub`.
- **Exigir que el ID token sea reciente**, más allá de su vencimiento. Lo necesita FIN-53, no el
  login.
- **Celular y moneda en el registro con Google.** El criterio fija `COP`, y los dos se cambian luego
  desde el perfil.

## Impact

- Esquema: el update va **antes** del despliegue, porque la app nueva lee `google_sub`, y modifica
  tablas, así que en local se recarga con `cargar-datos-local.sql`. Después se corre la comparación
  de esquemas de `docs/database/modelo-datos.md`.
- `compartido`: las dos rutas nuevas entran en la cadena Basic de `SecurityConfig`. `ErrorCodes`
  suma `ACCOUNT_NOT_REGISTERED`.
- `core`:
  - Puerto de salida y modelo para la identidad de Google, con su adapter real y su stub.
  - Dos puertos de entrada con sus casos de uso, y los endpoints en `AuthController` con sus DTOs.
  - Consultas nuevas en `UserRepositoryPort` y `UserR2dbcAdapter`.
  - `User` y `UserCredentials` llevan `googleSub`.
  - La apertura de sesión de `LoginUseCase` pasa a un componente compartido por los tres logins.
- Dependencias: ninguna nueva. El verificador usa el decoder de `spring-security-oauth2-jose`, que ya
  está.
- Configuración: el bloque `google` en los dos `application.yaml`, en `CloudRunConfigTest` y en los
  dos yaml locales sin versionar (`application-local.yaml` y `application-prod.yaml`).
  `docs/despliegue.md` registra la variable nueva del servicio.
- Documentación: `docs/api/contrato-api.md` (autenticación, los dos endpoints, login con contraseña,
  errores), `docs/database/modelo-datos.md` y `AGENTS.md`. `AGENTS.md` cambia en tres lugares: las
  rutas de la cadena Basic, las variables sin default y las claves del perfil local.
- Bruno: carpeta nueva `bruno/auth-google/` con el stub, y sus requests replicados en
  `bruno-personal/` con un ID token real que el usuario pega a mano.
- Front: FIN-54 ya está bloqueada por FIN-52 y es la que consume este contrato.
