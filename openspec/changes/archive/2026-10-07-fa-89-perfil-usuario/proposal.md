# Proposal

Origen: **FA-89** — Endpoint de perfil del usuario: leer y editar sus datos. La pidió el front el
07-10-2026: la bloquean FA-88 (menú y edición del perfil) y, por el celular, FA-87 (registro).

## Why

Los mocks del front tienen un menú de perfil (iniciales, nombre, correo) y una pantalla «Editar
perfil» con nombre, correo, celular y contraseña nueva. El API solo tiene `register` y `login`: no hay
forma de leer ni modificar al usuario logueado.

## What Changes

- `GET /api/users/me`: el perfil del usuario del token, sin contraseña ni hash.
- `PATCH /api/users/me`: modificación parcial de `firstName`, `lastName`, `email`, `phone` y
  `timezone`. Cambiar el correo exige `currentPassword`.
- `PUT /api/users/me/password`: cambio de contraseña con `currentPassword` y `newPassword`, 204.
- `POST /api/auth/register` acepta `phone` opcional y lo devuelve. La columna `finance.users.phone`
  ya existe: **no hay cambio de esquema** (la tarea decía lo contrario, es un error de la tarea).
- `contrato-api.md` y la colección de Bruno con los casos.

Decisiones del usuario del 07-10-2026, tomadas al empezar la tarea:

- El celular se expone en el perfil **y** en el registro, opcional en los dos.
- Cambiar el correo exige la contraseña actual: con un JWT robado se podría cambiar el correo y,
  cuando exista FA-90, recuperar la contraseña. Eso sería tomar la cuenta.
- La contraseña se cambia en una ruta aparte, no dentro del `PATCH`.
- **La moneda base no se cambia aquí.** Si el `PATCH` trae `baseCurrencyCode`, se omite: ni error
  ni cambio. El cambio y la pregunta de qué pasa con los reportes históricos son de FA-91 (flujo de
  monedas y conversiones).

## Fuera de alcance

- Cambiar `baseCurrencyCode`: FA-91.
- Invalidar los tokens vigentes al cambiar la contraseña o el correo. Hoy el JWT no se puede
  revocar y simplemente vence; es FA-17 (estrategia de refresh token y expiración).
- Limitar los intentos de `currentPassword` desde un token válido. No hay limitación de intentos en
  ningún endpoint, tampoco en el login; si hace falta, es una tarea nueva.
- `birth_date` y `telegram_chat_id`, que también están en la tabla: el mock no los pide.
- Recuperar la contraseña olvidada: FA-90.
- Desactivar o borrar el propio usuario.

## Capabilities

### New Capabilities
- `usuarios`: los datos del propio usuario. Qué acepta el alta, cómo se leen y modifican desde el
  perfil y cómo se cambia la contraseña.

### Modified Capabilities

Ninguna. El registro no tenía spec: lo que este change le agrega (el celular) entra en `usuarios`.

## Impact

- API: tres rutas nuevas bajo `/api/users/me`, en la cadena del JWT. `SecurityConfig` no cambia,
  porque esa cadena ya recoge toda ruta que no sea `/auth/*` ni `/status`. El registro suma un
  campo opcional, compatible hacia atrás.
- Código:
  - Puertos de entrada y casos de uso nuevos para leer el perfil, modificarlo y cambiar la
    contraseña.
  - `UserRepositoryPort` y `UserR2dbcAdapter` suman la lectura y la actualización del usuario.
  - `User` gana `phone`.
- Base: sin cambios de esquema. Los requests de Bruno usan un usuario propio `@bruno.local`, que
  `test-data.sql` ya limpia.
- Front: desbloquea FA-88 y el celular de FA-87.
