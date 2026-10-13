# Design

## Context

El login emite un JWT HS256 de 1 hora (`JwtTokenIssuerAdapter`) y la cadena Bearer lo acepta con solo
mirar firma, emisor y vencimiento (`JwtConfig.jwtDecoder`): el API no consulta la base para
autenticar, así que no tiene cómo dejar de aceptar un token antes de que venza. `finance.users` no
guarda nada de sesiones. La cadena Basic (`SecurityConfig`, `@Order(0)`) cubre `POST /auth/register`,
`POST /auth/login` y `GET /status`, y el proxy del front pone la Basic en todo `/auth/*`.

`compartido` no importa nada de `core` (AGENTS.md, *Módulos*), y el decoder vive en `compartido`.

## Goals / Non-Goals

**Goals:**
- Que un access deje de servir en el momento en que su usuario cambia la contraseña o se desactiva.
- Que un refresh robado y usado por el atacante se detecte en el siguiente uso del legítimo.

**Non-Goals:**
- Sesiones por dispositivo con nombre, ni listado de sesiones.
- Revocar un access concreto (el logout no lo hace: ver proposal, *Fuera de alcance*).

## Decisions

### 1. Una versión de sesión por usuario, comprobada en cada request Bearer

`finance.users` gana `token_version INTEGER NOT NULL DEFAULT 0`. El access lleva un claim `sv` con la
versión vigente al emitirlo. El decoder, después de validar firma, emisor y vencimiento, consulta
que el usuario del `sub` siga activo, sin borrar y con `token_version = sv`. Si no, el token se
rechaza con `BadJwtException`, que el resource server convierte en el 401 de
`UnauthenticatedEntryPoint`. El cambio de contraseña sube la versión en el mismo `UPDATE` que guarda
el hash.

Descartadas:
- **`password_changed_at` contra `iat`.** `iat` va en segundos: un token emitido en el mismo segundo
  del cambio sobreviviría, o el login inmediatamente posterior quedaría rechazado según cómo se
  redondee. Un contador no depende del reloj.
- **Lista de `jti` revocados.** También exige una consulta por request, y además guardar un registro
  por cada access emitido o revocado.
- **Caché de la versión.** Rompe el «en ese momento» del criterio: el token seguiría sirviendo hasta
  que expire la entrada.
- **Access más corto sin consulta.** Tampoco cumple el criterio, solo acota la ventana.

El precio es una consulta por clave primaria en cada request Bearer, sobre el pool de 10 conexiones
contra Neon. Para el volumen de la app es aceptable; si un día no lo es, la salida es una caché con
invalidación explícita, no volver a no consultar.

Efecto lateral buscado: un usuario desactivado o borrado recibe 401 en cualquier ruta Bearer, no solo
en `/users/me` como hasta ahora.

### 2. El puerto de la sesión vive en `compartido` y lo implementa `core`

`compartido/domain/port/out/SesionDelTokenPort` con `Mono<Boolean> abierta(UUID userId, long
version)`. Lo implementa `SesionDelTokenR2dbcAdapter` en `core/infrastructure/adapter/out/persistence`,
porque la tabla `users` es de `core`. `core` ya importa `compartido`, así que no se rompe la regla de
módulos, y `compartido` sigue sin importar a nadie.

Descartado: que `compartido` consulte `finance.users` directamente. Funcionaría, pero dejaría SQL de
una tabla de `core` en el módulo que no es dueño de ella, y la regla de «cuenta activa» se copiaría en
dos sitios.

Los slices `@WebFluxTest` que importan `JwtConfig` necesitan un bean del puerto para construir el
decoder: importan una configuración de test que lo da siempre abierto. Los `*IT` que firman tokens
con `TokenMother` lo declaran `@MockitoBean`. `TokenMother` agrega el claim `sv`.

### 3. Refresh opaco, aleatorio, guardado como SHA-256

El refresh es un valor aleatorio de 32 bytes de `SecureRandom` en base64url sin relleno. La base
guarda `sha256` en hexadecimal (`CHAR(64)`, único), y se busca por ese hash.

Descartadas:
- **Un JWT como refresh.** La rotación y la revocación necesitan estado en la base de todas formas, y
  un JWT expone sus claims a quien lo lea.
- **BCrypt para el hash.** Es para secretos de baja entropía; con 256 bits aleatorios no hay
  diccionario que probar, y BCrypt no permite buscar por hash (lleva sal), así que obligaría a
  identificar el refresh por otro campo en claro.

### 4. Familias, uso único y detección de reuso

`finance.refresh_tokens(id, user_id, family_id, token_hash, token_version, expires_at, created_at,
used_at, revoked_at)`. El login abre una familia nueva (`family_id`), con `expires_at = ahora + 24h`
y la `token_version` del usuario. El refresco **rota en una sola sentencia**: un `UPDATE ... SET
used_at` condicionado a que el refresh no esté usado, revocado ni vencido, y a que su usuario siga
activo con la misma `token_version`, encadenado en un CTE con el `INSERT` del refresh nuevo, que
copia la familia, el vencimiento y la versión. Si el `UPDATE` no toca filas, no se inserta nada, el
caso de uso revoca la familia del hash presentado y responde 401.

Así el uso único lo garantiza la base aunque lleguen dos refrescos a la vez, y no hay estado
intermedio en el que el viejo quedó usado y el nuevo no existe.

Revocar la familia en cualquier fallo, y no solo en el reuso, simplifica el caso de uso sin cambiar
nada: una familia vencida o de un usuario inactivo ya no tiene refresh que sirva.

Descartado: **una ventana de gracia** para el reuso, que toleraría dos pestañas refrescando a la vez.
Debilita justo la detección que se busca; el front serializa los refrescos (FIN-48).

### 5. El tope de 24 horas es de la sesión, no de cada refresh

El refresh nuevo copia `expires_at` del que reemplaza. `refreshExpiresIn` se calcula en el caso de uso
con el `Clock` como `expires_at - ahora`, en segundos. La duración sale de
`spring.security.jwt.refresh-expiration` (24h por defecto), que lee `LoginUseCase` con `@Value`.

### 6. El cambio de contraseña no escribe en `refresh_tokens`

El refresh guarda la `token_version` con la que se abrió su familia, y la rotación exige que coincida
con la del usuario. Subir la versión en el `UPDATE` de la contraseña basta para que todos los refresh
anteriores dejen de servir. `ChangePasswordUseCase` no cambia: cambia la sentencia `ACTUALIZAR_CLAVE`
de `UserR2dbcAdapter`.

Descartado: marcar `revoked_at` en todas las filas del usuario en la misma transacción. Son dos
sentencias para el mismo efecto y una sola fuente de verdad (la versión) es más difícil de
desincronizar.

### 7. Rutas bajo `/auth/` con la Basic y el refresh en el cuerpo

`POST /auth/refresh` y `POST /auth/logout` entran en `RUTAS_BASIC`. El proxy del front ya pone la
Basic en todo `/auth/*`, y el refresh se presenta en el cuerpo, igual que la contraseña en el login.

Descartado: el refresh como `Authorization: Bearer` contra la cadena JWT. El refresh no es un JWT, el
decoder lo rechazaría, y el proxy no pone el Bearer en `/auth/*`.

### 8. Errores sin código nuevo

Un refresh que no sirve da 401 `UNAUTHENTICATED` en `refreshToken`, con la misma descripción que el
resto de los 401. El front solo necesita saber «vuelve al login», y el campo ya separa este 401 del
de la cabecera. El logout responde 204 aunque el refresh no exista: un cliente que reintenta un
logout no tiene nada que corregir, y un 401 ahí solo informaría si un valor fue emitido.

Descartado: `INVALID_REFRESH_TOKEN` en `ErrorCodes`. Duplicaría la categoría de `UNAUTHENTICATED`.

## Risks / Trade-offs

- [Al desplegar, todos los access abiertos dan 401 porque no traen `sv`] → Cada usuario vuelve a
  hacer login una vez. Es una app personal con pocos usuarios; se avisa en el PR.
- [Una consulta más por request Bearer] → Por clave primaria; ver decisión 1.
- [Dos refrescos simultáneos cierran la sesión] → El front los serializa (FIN-48).
- [Las filas de `refresh_tokens` crecen sin límite] → Issue nuevo para limpiarlas; ver proposal.
- [El access sigue sirviendo hasta 1 hora después del logout] → Aceptado; el front lo descarta.

## Migration Plan

1. Aplicar `20261012_01_refresh_tokens.sql` en Neon **antes** de desplegar: la app nueva lee
   `token_version`. La app vieja con la base nueva sigue funcionando (no nombra la columna ni la
   tabla).
2. Desplegar. Las sesiones abiertas caen una vez.
3. Reversión: revertir el commit en `main` (la app vieja ignora la columna y la tabla) y, si se
   quiere, el bloque de reversión comentado del update.
