# Design

## Context

El login (`LoginUseCase`) busca credenciales por correo, compara el hash con BCrypt y abre una sesión
con la mecánica de FIN-47: una fila en `refresh_tokens` y un access con la versión de sesión. El
alta (`RegisterUserUseCase`) valida la moneda, la lista de admitidos y que el correo esté libre, en
ese orden, e inserta el usuario con sus categorías en una transacción. `finance.users.password_hash`
es `NOT NULL`, y `findActivePasswordHash` mapea la columna sin contemplar un nulo.

El ID token de Google Identity Services es un JWT RS256. Lo firma una de las llaves que Google
publica en `https://www.googleapis.com/oauth2/v3/certs` y que rota cada pocos días. Su `iss` vale
`accounts.google.com` o `https://accounts.google.com`, su `aud` es el client ID y su `sub` es el
identificador estable de la cuenta, de hasta 255 caracteres ASCII.

## Goals / Non-Goals

**Goals:**
- Verificar el ID token sin dependencias nuevas y sin una llamada a Google por cada login.
- Que `bru run` ejerza el flujo completo sin Google ni tokens reales.
- Una sola forma de abrir sesión para los tres logins.

**Non-Goals:**
- Definir la primera contraseña o exponer el método en el perfil. Es FIN-53, que también reemplaza
  el comportamiento provisional de la decisión 7.

## Decisions

### 1. Verificar con el decoder de Spring Security contra el JWKS de Google

Un `NimbusReactiveJwtDecoder.withJwkSetUri(jwksUri)` lleva un `WebClient` con timeout y estos
validadores:

- el de vencimiento (`JwtTimestampValidator`), con la tolerancia por defecto de 60 s;
- uno de emisor que acepta los dos valores de Google;
- uno de `aud` que exige el client ID configurado.

`email_verified`, que puede llegar como booleano o como texto, lo comprueba el adapter después de
decodificar. El decoder guarda las llaves en caché y solo vuelve a pedirlas cuando aparece un `kid`
desconocido.

Descartadas:

- **`google-api-client` (`GoogleIdTokenVerifier`)**: una dependencia más, fuera del BOM de Spring, y
  bloqueante, así que habría que envolverla en `boundedElastic`. El decoder ya está en el classpath
  por `spring-security-oauth2-jose`.
- **El endpoint `tokeninfo` de Google**: una llamada de red en cada login, y Google lo desaconseja en
  producción.

La URL del JWKS es configuración fija (`google.jwks-uri`), nunca dato de la petición: no abre una
vía de SSRF.

### 2. Un puerto que devuelve la identidad o vacío

`GoogleIdTokenVerifierPort.verificar(idToken)` emite un `GoogleIdentity(sub, email, firstName,
lastName)` si el token es válido, vacío si no lo es, y un error 502 `EXTERNAL_SERVICE_ERROR` si no
pudo obtener las llaves. El adapter normaliza el correo en minúsculas y resuelve el nombre en este
orden: `given_name`, luego `name` y luego la parte del correo antes de la `@`, recortado a 100
caracteres. El apellido sale de `family_name`, recortado a 100, o nulo.

Para separar los dos fallos, el adapter mira la cadena de causas de la excepción del decoder. Un
`WebClientException`, un `IOException` o un timeout significan «Google no respondió»; cualquier otra
`JwtException` es un token inválido. Lo prueba un test del adapter contra un servidor HTTP propio que
sirve un JWKS generado en el test, como `GeminiAssistantAdapterTest`.

### 3. Stub fuera de `prod` para `bruno/`

`StubGoogleIdTokenVerifierAdapter` tiene `@Profile("!prod")` y se activa con
`google.proveedor=stub`, igual que los stubs del asistente y de las tasas. Acepta tokens de la forma
`stub|<sub>|<email>|<nombre>|<apellido>`, y `<apellido>` puede ir vacío. Cualquier otro texto es un
token inválido. Así `bruno/` crea cuentas repetibles con un `Date.now()` en el `sub` y en el correo,
y prueba el 401 con un token cualquiera. Con `prod` el stub no existe aunque se pida, y la app no
arranca sin verificador.

Lo que el stub no puede probar son la firma, `aud`, el vencimiento, `email_verified` y la caída de
Google. Eso lo cubre el test del adapter real (decisión 2).

### 4. Códigos de error

| Caso | Respuesta | Por qué |
|---|---|---|
| Token inválido | 401 `INVALID_CREDENTIALS` en `idToken` | Es una credencial del usuario que no sirve, como la contraseña equivocada. `UNAUTHENTICATED` queda para la credencial de la app y para la sesión |
| Login sin cuenta | 404 `ACCOUNT_NOT_REGISTERED` en `idToken` (código nuevo) | El front tiene que distinguirlo para ofrecer el registro |
| Registro de una cuenta existente | 409 `DUPLICATE_RESOURCE` en `email` | El mismo código y el mismo campo del alta normal, así el front reutiliza el manejo |
| Correo vinculado a otro Google | 409 `DUPLICATE_RESOURCE` en `email` | — |
| Correo no admitido | 403 `REGISTRATION_NOT_ALLOWED` en `email` | — |

Que el login con Google diga «no tienes cuenta» no reabre el oráculo de correos que el login con
contraseña evita (OWASP A07). Para recibir esa respuesta hay que presentar un ID token válido de esa
cuenta de Google, y quien lo tiene ya es el dueño del correo.

Se descartó un 401 genérico para todo: el front no podría ofrecer el registro, que es lo que pide la
tarea.

### 5. Orden del login y la vinculación

1. Verificar el token.
2. Buscar un usuario activo por `google_sub`. Si existe, abrir la sesión.
3. Si no, buscar un usuario activo por correo:
   - Sin `google_sub`: se vincula con un `UPDATE … SET google_sub = :sub WHERE id = :id AND
     google_sub IS NULL AND activo`. Si no actualiza ninguna fila (otra petición vinculó entre medio)
     o choca con el único, responde 409.
   - Con otro `google_sub`: 409.
   - Ninguno: 404.

El registro sigue el orden del alta normal: token, lista de admitidos, correo libre y luego `sub`
libre. Así un correo no admitido no se entera de si ya tiene cuenta. Un choque con un único en el
`INSERT` (carrera) sale como el mismo 409, como ya hace `comoConflicto`.

### 6. Esquema

- `password_hash` pasa a admitir nulo.
- Columna `google_sub VARCHAR(255)`.
- `ux_users_google_sub` sobre `google_sub`, único parcial `WHERE google_sub IS NOT NULL AND deleted_at
  IS NULL`, con el mismo criterio que `ux_users_email`: un usuario borrado libera su cuenta Google
  para volver a registrarse.
- `ck_users_metodo CHECK (password_hash IS NOT NULL OR google_sub IS NOT NULL)`: la base no admite
  un usuario sin ninguna forma de entrar.

Se descartó una tabla aparte de identidades (`user_identities`): con un solo proveedor externo es
una tabla y un join más sin un caso que los pida. Si llega un segundo proveedor, se migra.

### 7. Un hash nulo mientras llega FIN-53

- `LoginUseCase` descarta las credenciales con hash nulo antes de llamar al hasher, y responde el
  401 de siempre.
- `findActivePasswordHash` lee `coalesce(password_hash, '')`. Con eso el cambio de contraseña y el
  cambio de correo de un usuario sin contraseña comparan contra un hash vacío, que BCrypt rechaza, y
  responden el 400 de contraseña actual equivocada en vez de un 500 por un nulo en el mapeo.

FIN-53 cambia esto por su propio error, y el requisito de `usuarios` que agrega este change dice
cuál es el comportamiento mientras tanto.

### 8. Abrir la sesión en un solo lugar

El `abrirSesion` de `LoginUseCase` (fila de refresh + access) pasa a un `@Component` de
`application/usecase`, `AperturaDeSesion`, que reciben los tres casos de uso. Se descartó copiarlo:
la duración de la sesión y la forma de la fila tienen que ser las mismas para los tres, y eso es lo
que pide el criterio de «los tokens se refrescan, se revocan y vencen igual».

### 9. `GOOGLE_CLIENT_ID` sin default

Se lee como los secretos, aunque no lo sea, porque un client ID por defecto haría que la app aceptara
tokens de un cliente que no es el suyo, o ninguno. La app no arranca sin él, y `CloudRunConfigTest`
lo comprueba. El precio es que el servicio necesita la variable antes de que esto llegue a `main`.

### 10. El registro con Google responde 201

Crea un recurso, como el alta, aunque su cuerpo sea el del login. Se descartó 200: el front ya
distingue el alta por su 201.

## Risks / Trade-offs

- **Google rota las llaves** → el decoder vuelve a pedirlas al ver un `kid` desconocido; no hay nada
  que configurar.
- **La caché de llaves vive en memoria y Cloud Run escala a 0** → el primer login tras un arranque en
  frío hace una petición a Google. Es aceptable: el arranque en frío ya cuesta más que eso.
- **El correo de Google cambia después de vincular** → el login sigue entrando por `sub`, y el correo
  de la cuenta no se toca (fuera de alcance).
- **Un correo vinculado a otro Google bloquea la entrada con el nuevo Google** → es el comportamiento
  que pide el criterio. Desvincular queda fuera de alcance.
- **El stub en un entorno real** → solo existe sin el perfil `prod`, como los otros dos.

## Migration Plan

1. Crear el OAuth Client ID de tipo Web en Google Cloud Console (paso manual del epic) y anotar el
   client ID.
2. Aplicar `20261014_01_login_google.sql` en Neon **antes** del despliegue: la app vieja no nombra
   `google_sub`, y con `password_hash` nulable sigue igual porque nadie inserta nulos.
3. Agregar `GOOGLE_CLIENT_ID` al servicio de Cloud Run y anotarlo en `docs/despliegue.md`.
4. Llevar el código a `main`.

Reversión: primero la app. El bloque comentado del update borra la columna, el índice y el `CHECK`.
Volver a poner `NOT NULL` en `password_hash` falla si ya hay usuarios solo-Google, y así lo dice el
bloque.
