# Proposal

Origen: FIN-59, del epic FIN-11 (Asistente y Telegram).

## Why

Hoy `phone` acepta un `+` opcional y de 7 a 15 dígitos, así que `3001234567` y `+573001234567` son
válidos y distintos. Telegram entrega el número del contacto con indicativo, y FIN-60 va a vincular
el chat comparando ese número con `users.phone`: si el celular se guarda sin indicativo, la
comparación nunca coincide. El alta y el perfil tienen que exigir el formato internacional desde
ahora, antes de que el bot dependa de él.

Decisiones del epic que aplica:

- El chat se vincula compartiendo el contacto propio, y el teléfono tiene que ser el `phone` de un
  usuario registrado, en formato internacional (13-10-2026).
- Mientras la app esté en prueba, cambiar el formato del teléfono no necesita migración
  (13-10-2026).

## What Changes

- **BREAKING** `phone` en `POST /api/auth/register` y `PATCH /api/users/me` exige el formato E.164:
  `+`, un primer dígito de 1 a 9 (el indicativo no empieza por 0) y de 7 a 15 dígitos en total, sin
  espacios ni separadores. Los espacios del borde se siguen tolerando y recortando. Un número sin `+`
  o con separadores es 400 `VALIDATION_ERROR` en `phone`, con un mensaje nuevo que dice el formato.
- En el perfil, `phone` en blanco lo sigue borrando.
- Los datos de prueba (`test-data.sql`) y de demo (`demo-data.sql`) pasan sus celulares a `+57…`.
  `seed.sql` no tiene celulares.
- La colección `bruno/users/` usa el formato nuevo y prueba que un celular sin `+` se rechace.
- El contrato (`docs/api/auth.md`, `docs/api/usuarios.md`) describe el formato y el mensaje, que el
  front necesita para validar igual en el registro y el perfil (FIN-62).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `usuarios`: el formato de `phone` en el alta y en el parche del perfil, y los ejemplos del perfil
  que mostraban un celular sin indicativo.

## Fuera de alcance

- Migrar los celulares ya guardados: el epic decide que no hace falta mientras la app esté en
  prueba. En Neon el usuario de demo conserva `3109876543` hasta que se recargue `demo-data.sql`.
- Un `CHECK` sobre `users.phone` en la base (ver `design.md`).
- Validar el número por país (longitud o prefijos válidos de cada indicativo).
- Los formularios del front: FIN-62.
- La vinculación del chat por teléfono y la lista de admitidos con teléfonos: FIN-60.

## Impact

- `Formatos.CELULAR` y `Formatos.MENSAJE_CELULAR`, usados por `RegisterUserRequest` y
  `UpdateUserProfileRequest`, y sus tests (`RegisterUserRequestTest`, `UpdateUserProfileRequestTest`,
  `AuthControllerTest`, `UserControllerTest`).
- `docs/database/test-data.sql` y `docs/database/demo-data.sql`. No hay cambio de esquema, así que
  no hay `update/` ni se reinician los datos de la base local.
- `bruno/users/` y su réplica en `bruno-personal/`.
- `docs/api/auth.md` y `docs/api/usuarios.md`. Clientes que manden el celular sin `+` empiezan a
  recibir 400: el front lo adapta en FIN-62.
