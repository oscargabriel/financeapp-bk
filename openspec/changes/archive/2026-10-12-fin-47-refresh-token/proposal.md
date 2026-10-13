# Proposal

Issue **FIN-47** del tablero, epic FIN-6 (Etapa 5 — Identidad y seguridad). Se pidió desde la raíz
de coordinación el 12-10-2026 como la decisión de FIN-14: access de 1 hora, refresh de 24 horas con
tope fijo desde el login, logout que revoca el refresh y cambio de contraseña que invalida todo lo
emitido antes. Su contraparte en el front es FIN-48, que consume este contrato.

## Why

Hoy el login entrega solo un access de 1 hora: al vencer, el front no tiene más salida que mandar al
usuario al login, aunque esté usando la app. Y un token robado o el de una sesión olvidada sigue
sirviendo hasta vencer aunque el usuario cambie la contraseña, porque `finance.users` no tiene con
qué revocar nada.

## What Changes

- **El login devuelve además un refresh token.** La respuesta pasa a ser `{accessToken, tokenType,
  expiresIn, refreshToken, refreshExpiresIn}`. `expiresIn` sigue en 3600; `refreshExpiresIn` son los
  segundos que le quedan al refresh, 86400 recién hecho el login. Los campos actuales no cambian.
- **`POST /api/auth/refresh`** (Basic, como el login) recibe `{refreshToken}` y responde 200 con la
  misma forma del login: un access nuevo de 1 hora y un refresh nuevo. El refresh nuevo vence cuando
  vencía el que reemplaza: la sesión dura como mucho 24 horas desde el login, se refresque o no.
- **Rotación con detección de reuso.** Cada refresh sirve una sola vez. Presentar uno ya usado
  invalida también el que lo reemplazó, y la sesión entera queda cerrada.
- **`POST /api/auth/logout`** (Basic) recibe `{refreshToken}` y responde 204: el refresh y la sesión
  a la que pertenece quedan revocados. Responde 204 también si el refresh no existe o ya no servía.
- Un refresh vencido, inexistente, ya usado, revocado, de una sesión cerrada por cambio de contraseña
  o de un usuario desactivado o borrado responde **401 `UNAUTHENTICATED` en `refreshToken`**. Sin
  `refreshToken` en el cuerpo, 400 `VALIDATION_ERROR` en `refreshToken`. No hay código de error nuevo.
- **El cambio de contraseña cierra todas las sesiones del usuario.** Tras `PUT
  /api/users/me/password`, todo access y todo refresh emitido antes responde 401 desde ese momento.
  El 204 del cambio no trae tokens nuevos: el front vuelve al login (FIN-48).
- **Cada request con Bearer comprueba en la base que el usuario siga activo y que su token no sea de
  antes del último cambio de contraseña.** Un token de un usuario desactivado o borrado da 401 en
  cualquier ruta, no solo en `/users/me`.
- **BREAKING para sesiones abiertas al desplegar:** los access emitidos por la versión anterior no
  traen la marca de sesión y responden 401. Cada usuario vuelve a hacer login una vez.
- El refresh no se guarda en claro: la base guarda su hash SHA-256.
- Tabla nueva `finance.refresh_tokens` y columna `finance.users.token_version`, en `schema.sql` y en
  el update `docs/database/update/20261012_01_refresh_tokens.sql`.
- Propiedad nueva `spring.security.jwt.refresh-expiration` (`JWT_REFRESH_EXPIRATION`, 24h por
  defecto en `application.yaml`).

El criterio del issue que pide dejar «la decisión, con su porqué, en la memoria del proyecto» se
cumple con `design.md` de este change y la spec `auth`: en este repo el porqué de una decisión vive en
el change que la tomó, y la memoria de Claude queda solo para decisiones sin spec
(`.claude/rules/claude-code.md`, *Reparto con la memoria persistente*).

## Capabilities

### New Capabilities

- `auth`: login, refresco y cierre de sesión, y la validez de los tokens: qué entrega cada uno, cuánto
  dura, cuándo deja de servir y con qué error se rechaza.

### Modified Capabilities

- `usuarios`: el cambio de contraseña invalida los tokens emitidos antes, y el escenario del token de
  un usuario desactivado se generaliza a cualquier ruta Bearer en `auth`.

## Fuera de alcance

- **Limpiar los refresh vencidos.** Las filas se acumulan: una por login y una por cada refresco.
  Para el volumen de la app no estorba durante meses. Va a un issue nuevo.
- **Cerrar todas las sesiones a petición, o listar las sesiones abiertas.** No se pidió; el cambio
  de contraseña ya las cierra todas.
- **Revocar el access en el logout.** El logout revoca el refresh; el access sigue sirviendo hasta
  vencer (como mucho 1 hora) y el front lo descarta. Revocarlo exigiría tocar la versión de sesión,
  que cerraría también las de los otros dispositivos.
- **Tolerar dos refrescos simultáneos con el mismo refresh.** El segundo se trata como reuso y cierra
  la sesión. El front serializa los refrescos (FIN-48).
- **Mover el refresh a una cookie `HttpOnly`.** Lo maneja el proxy del front; el API lo recibe en el
  cuerpo.

## Impact

- Esquema: columna `users.token_version` y tabla `refresh_tokens`, en `schema.sql` y en el update.
  El update va **antes** del despliegue y modifica tablas, así que en local se recarga con
  `cargar-datos-local.sql`. Después se corre la comparación de esquemas de
  `docs/database/modelo-datos.md`.
- `compartido`: puerto de salida nuevo para comprobar la sesión de un token, que usa el decoder de
  `JwtConfig`; las rutas `/auth/refresh` y `/auth/logout` entran en la cadena Basic de
  `SecurityConfig`.
- `core`: el login, el refresco y el logout (puertos de entrada, casos de uso, controlador, DTOs), el
  emisor de tokens con la versión de sesión, un generador de refresh y su adapter R2DBC, y el
  adapter que implementa el puerto de `compartido`. `ChangePasswordUseCase` no cambia: la versión la
  sube el `UPDATE` de la contraseña.
- Tests: los `*IT` que firman tokens con `TokenMother` pasan a declarar el puerto de sesión como
  mock, porque el decoder lo consulta.
- Configuración: la propiedad nueva en los dos `application.yaml` y en `CloudRunConfigTest`. Cloud
  Run no necesita variable nueva.
- Datos: `test-data.sql` siembra refresh de prueba (vencido, de un usuario inactivo) con hash
  conocido.
- Documentación: `docs/api/contrato-api.md` (autenticación, login, refresh, logout, cambio de
  contraseña, errores), `docs/database/modelo-datos.md` y `AGENTS.md` (rutas de la cadena Basic y
  la consulta del decoder).
- Bruno: requests nuevos en `bruno/auth/` y `bruno/users/`, y el login guarda el refresh. Se replican
  en `bruno-personal/`.
