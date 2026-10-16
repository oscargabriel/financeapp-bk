# Proposal

FIN-30, epic FIN-74 (Calidad y mantenimiento). Migrada de Notion (FA-96) y refinada desde la raíz el
15-10-2026.

## Why

`demo-data.sql` crea un solo usuario (`demo@`, o `dev@` con `u = 1`), y ni la demo ni
`test-data.sql` llenan `affiliations` ni `expense_shares`: Bruno las crea en la corrida con usuarios
`@bruno.local` y las deja en una base que se recarga. Las pantallas de afiliados y compartidos del
front (FIN-69 a FIN-73, epic FIN-9) no tienen nada que mostrar, ni en local ni en Neon.

Decisiones del epic que aplica:

- La demo cubre personas afiliadas y lo compartido (15-10-2026). Los pendientes del asistente, los
  movimientos en USD y los 12 meses son FIN-95, FIN-96 y FIN-97.
- Las personas afiliadas se crean en local y en Neon junto con `demo@`, con su misma contraseña: el
  riesgo aceptado en FIN-43 para `demo@` (15-10-2026).

## What Changes

- `demo-data.sql` crea, junto al usuario de la demo, tres personas que inician sesión con
  `claveDePrueba123`, cada una con una cuenta en COP y la copia de las categorías por defecto:
  - Carlos, afiliado: el usuario de la demo le envió la solicitud y él la aceptó.
  - Valentina, afiliada: ella le envió la solicitud al usuario de la demo y él la aceptó.
  - Andrés, que le envió una solicitud que sigue sin responder.
  - Además, el usuario de la demo tiene una solicitud abierta a un correo sin cuenta.
- Gastos compartidos en los dos sentidos, con partes `PENDING`, `PAID` y `REJECTED` en cada uno, y
  todas las filas que el API habría dejado: el gasto de quien pagó, el pendiente de quien debe, el
  gasto confirmado de quien pagó su parte, el ingreso que eso le crea a quien pagó (uno aprobado y
  otro todavía pendiente), y la parte rechazada sin pendiente.
- Una transferencia compartida hecha por el usuario de la demo, con el ingreso de la persona
  pendiente, y una recibida por él, también pendiente.
- Los correos de las personas van en `<nombre>@<local de la demo>.financeapp.local`
  (`carlos@demo.financeapp.local`, `carlos@dev.financeapp.local`): `dev@` tiene sus propias personas,
  ningún correo puede ser de alguien real ni registrarse por el alta, y la comprobación de Neon de
  `docs/despliegue.md` no las confunde con datos de prueba (ver `design.md`).
- La recarga borra y recrea también a las personas, por id y por correo: lo que se haya aprobado o
  rechazado desde el front desaparece con ellas.
- El encabezado de `demo-data.sql`, la tabla de usuarios de `AGENTS.md`, el encabezado de
  `cargar-datos-local.sql` y *La demo en Neon* de `docs/despliegue.md` dicen quiénes son las
  personas y que también se cargan en Neon.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: cambia un script de datos, no el comportamiento del sistema (`skip_specs: true`). Las
filas siguen lo que ya describen `afiliaciones` y `compartidos`.

## Fuera de alcance

- Pendientes del asistente (FIN-95), movimientos de la cuenta en USD (FIN-96) y los 12 meses con más
  volumen (FIN-97). Por eso las partes de la demo van todas en COP.
- Afiliaciones desvinculadas y partes anuladas (`VOIDED`): la tarea no las pide.
- Personas afiliadas a `dev@` con las de `demo@`, o entre `demo@` y `dev@`: cada uno tiene las suyas.
- Cambios en `test-data.sql`, en `bruno/` o en el API.

## Impact

- `docs/database/demo-data.sql`
- `docs/database/cargar-datos-local.sql` (encabezado)
- `docs/despliegue.md` (*La demo en Neon*)
- `AGENTS.md` (tabla de usuarios de *Base de datos*)
- Sin cambios en `src/`, `bruno/`, el esquema ni el contrato del API. En Neon, la próxima recarga de
  la demo agrega las personas.
