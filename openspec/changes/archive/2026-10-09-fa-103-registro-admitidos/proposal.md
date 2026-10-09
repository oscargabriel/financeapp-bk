# Proposal

Tarea **FA-103** del tablero, prioridad `Alta`. Se pidió desde la raíz de coordinación el
09-10-2026: el usuario quiere publicar la app en producción con el registro desde el login (FA-87
en el front) para que la prueben algunos amigos, pero sin abrirla a cualquiera. Los roles o planes
de pago que mencionó van en FA-104; aquí solo se cuida no estorbarlos.

## Why

Hoy `POST /api/auth/register` registra a quien tenga la credencial Basic compartida, y esa
credencial es visible en el front (FA-43). Publicar el front sería abrir el registro a cualquiera.
Hace falta cerrar el alta a un grupo de correos que el dueño controla, sin pantalla ni endpoint para
administrarlo.

## What Changes

- Tabla nueva `finance.registration_allowlist`. Cada fila admite un correo exacto
  (`ana@correo.com`) o un dominio completo (`@bruno.local`). Se guarda en minúsculas y la
  comparación usa el correo ya normalizado del alta, así que `Ana@Correo.com` cuenta como
  `ana@correo.com`.
- Con la restricción encendida, el alta de un correo que no esté en la lista responde **403
  `REGISTRATION_NOT_ALLOWED` en `email`**. No crea usuario ni categorías. `REGISTRATION_NOT_ALLOWED`
  es un código nuevo de `ErrorCodes`.
- Orden de las validaciones del alta: primero los 400 (formato del cuerpo y moneda inexistente),
  luego el 403 de no admitido y al final el 409 de correo repetido. A un correo fuera de la lista no
  se le dice si ya existe una cuenta con él.
- Un correo admitido se registra igual que hoy, y el 409 `DUPLICATE_RESOURCE` no cambia.
- La restricción se controla con la propiedad `registro.admitidos.enabled` (variable
  `REGISTRO_ADMITIDOS_ENABLED`), que vale `true` por defecto en `application.yaml`. Cloud Run queda
  encendido sin tocar el servicio. Con `false`, el alta queda abierta como hoy y la tabla no se
  consulta.
- El login no consulta la lista. Quitar un correo no afecta al usuario que ya se registró con él;
  para cortarle el acceso está `is_active`.
- Update `docs/database/update/20261009_01_registro_lista_admitidos.sql`. Se aplica **antes** de
  desplegar la app. Crea la tabla vacía: en producción el registro queda cerrado para todos hasta
  que el dueño agregue los correos. No modifica tablas existentes, así que no reinicia los datos
  locales.
- Los datos locales admiten por dominio a quienes ya registran desde ahí: `test-data.sql` agrega
  `@bruno.local` y `demo-data.sql` agrega `@front.local`. `test-data.sql` agrega además el correo
  exacto `invitado@financeapp.local`, para probar la coincidencia exacta desde Bruno.
- `docs/despliegue.md` documenta las sentencias SQL para agregar y quitar un correo en Neon.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `usuarios`: el alta solo acepta correos admitidos cuando la restricción está encendida, con su
  rechazo, el orden de las validaciones y el login que no consulta la lista.

## Fuera de alcance

- **Administrar la lista desde el API o el front.** Se mantiene con SQL a mano, como pide la tarea.
- **Roles o planes por invitado.** Van en FA-104. La tabla es propia y está separada de `users`,
  así que podrá llevar más adelante el plan con el que entra cada invitado sin tocar el alta.
- **Cortar el acceso de un usuario ya registrado al quitarlo de la lista.** Para eso está
  `is_active`. No hay endpoint para desactivar usuarios, y no se pidió.
- **Ocultar si un correo está en la lista.** El 403 dice que el correo no está admitido, y eso
  permite probar correos uno por uno. Para hacerlo hace falta la credencial Basic, y lo único que se
  averigua es quién está invitado. Se acepta mientras la app esté en pruebas.

## Impact

- Esquema: tabla `registration_allowlist` en `schema.sql` y en el update. Después se corre la
  comparación de esquemas de `docs/database/modelo-datos.md`.
- Dominio: puerto de salida nuevo `RegistrationAllowlistPort` y el código `REGISTRATION_NOT_ALLOWED`.
- Aplicación: `RegisterUserUseCase` consulta el puerto si la propiedad está encendida. Pasa a tener
  un constructor explícito, porque recibe un `@Value`.
- Persistencia: `RegistrationAllowlistR2dbcAdapter`, que verifica Bruno, como los demás adapters.
- Configuración: la propiedad en `application.yaml` y en el `application.yaml` de test, y su
  comprobación en `CloudRunConfigTest`.
- Datos: `test-data.sql` y `demo-data.sql`. El usuario `invitado@financeapp.local` se suma a la
  tabla de usuarios del back en `AGENTS.md`.
- Documentación:
  - `docs/api/contrato-api.md`: el registro, el login y la tabla de errores;
  - `docs/database/modelo-datos.md`: la tabla;
  - `docs/despliegue.md`: las sentencias de la lista y la variable.
- Bruno: dos requests nuevos en `bruno/auth/`, uno admitido por correo exacto y en mayúsculas y otro
  no admitido. El primero no es idempotente: **entre dos corridas hay que recargar
  `test-data.sql`**, como ya pasa con `bruno/pending/`. `verificar-bruno.ps1 -RecargarDatos` lo
  hace. No cambia la ruta, el cuerpo ni la autenticación de ningún endpoint, así que
  `bruno-personal/` no necesita requests nuevos.
