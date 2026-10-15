# Proposal

FIN-53, del epic FIN-8 «Login con Google». Aplica la decisión del epic «Un usuario sin contraseña
puede definir una después, confirmando su identidad con Google» (12-10-2026), y también las de
«El front usa Google Identity Services, que entrega un ID token firmado; el back lo verifica»
(12-10-2026) y la vinculación automática por correo (12-10-2026), que fijan qué cuenta Google es la
del usuario.

## Why

Desde FIN-52 hay usuarios sin contraseña. Esos usuarios no pueden definir una, porque
`PUT /api/users/me/password` exige la actual, y tampoco cambiar el correo. Mientras tanto reciben un
`INVALID_CREDENTIALS` provisional que no los distingue de quien se equivocó de contraseña. El front
(FIN-55) necesita saber desde el perfil qué métodos tiene el usuario y tener una vía para la primera
contraseña.

## What Changes

- **El perfil dice qué métodos tiene el usuario.** `GET` y `PATCH /api/users/me` agregan dos campos:
  `hasPassword` (tiene contraseña) y `googleLinked` (tiene una cuenta Google vinculada).
- **Ruta nueva `POST /api/users/me/password`, con Bearer.** Recibe `{idToken, newPassword}` y define
  la primera contraseña de un usuario que no tiene ninguna. Exige un ID token de Google:
  - válido, con la misma verificación que FIN-52;
  - emitido hace 5 minutos o menos;
  - de la cuenta Google vinculada al usuario.

  Responde 204 y, como el cambio de contraseña, cierra todas las sesiones del usuario. Desde ahí el
  usuario entra también con correo y contraseña.
- **Esa ruta rechaza a quien ya tiene contraseña:** 409 `INVALID_STATE`. Ese usuario usa
  `PUT /api/users/me/password` con la actual.
- **Fin del provisional de FIN-52.** Para un usuario sin contraseña, `PUT /api/users/me/password` y un
  `PATCH /api/users/me` que cambie el correo responden 409 `INVALID_STATE` en `currentPassword`, ya no
  400 `INVALID_CREDENTIALS`. Es un error propio: ese usuario define primero su contraseña.
- **Los errores de Google en esta ruta van con 400, no con 401:** un token inválido, viejo o de otra
  cuenta da 400 `INVALID_CREDENTIALS` en `idToken`. Va en la cadena Bearer, y un 401 le diría al
  cliente que su sesión venció. Es el mismo criterio de la contraseña actual equivocada.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `usuarios`:
  - la forma del perfil, con los dos campos nuevos;
  - las rutas que exigen Bearer;
  - la regla del usuario sin contraseña, que cambia de error;
  - la definición de la primera contraseña, que es nueva.

## Fuera de alcance

- **Desvincular la cuenta Google o vincular una a mano desde el perfil.** El epic no lo pide; la
  vinculación sigue siendo la automática del primer login con Google.
- **Exigir un ID token reciente en el registro o el login con Google.** Siguen como los dejó FIN-52.
- **Quitar la contraseña a un usuario que ya la tiene.**
- **Cambiar el correo con la confirmación de Google en vez de la contraseña.** El usuario sin
  contraseña define primero una; si hace falta otra vía, es una tarea nueva.

## Impact

- **API:**
  - el perfil (`GET` y `PATCH /api/users/me`) suma dos campos, sin quitar ninguno: el cambio es
    compatible;
  - ruta nueva `POST /api/users/me/password`;
  - para un usuario sin contraseña, el error de `PUT /api/users/me/password` y del `PATCH` del correo
    pasa de 400 `INVALID_CREDENTIALS` a 409 `INVALID_STATE`.
- **Código (`core`):**
  - el perfil del dominio y su respuesta;
  - el puerto de usuarios: lee las credenciales por id y escribe la primera contraseña con una
    condición;
  - la identidad de Google: agrega cuándo se emitió el token, y la sacan el verificador y el stub;
  - un caso de uso, un puerto de entrada y un DTO nuevos;
  - `ChangePasswordUseCase` y `UpdateUserProfileUseCase`.
- **Sin cambios de esquema:** `password_hash` nullable y `google_sub` ya existen desde FIN-52.
- **Documentación y colecciones:** `docs/api/contrato-api.md`, `bruno/auth-google/` y
  `bruno-personal/`.
- **Front:** FIN-55 consume los dos campos del perfil y la ruta nueva, y ya está bloqueada por esta
  tarea.
