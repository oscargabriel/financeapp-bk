# Proposal

FIN-95, epic FIN-74 (Calidad y mantenimiento). Refinada desde la raíz el 15-10-2026, junto con
FIN-30, FIN-96 y FIN-97.

## Why

Los únicos pendientes del usuario de la demo son los que le dejan sus personas afiliadas (FIN-30):
todo lo suyo entra confirmado. Los pendientes del asistente (FIN-58) solo existen en `test-data.sql`,
para `pendientes@` y `bruno/pending/`. La pantalla de pendientes del front y su filtro por persona
(FIN-71, FIN-73) necesitan, además de los compartidos, pendientes propios del usuario agrupados como
los deja el asistente, para mostrar la confirmación por grupo y que el filtro deje fuera los propios.

Decisión del epic que aplica:

- La demo (`demo-data.sql`) cubre personas afiliadas y lo compartido, pendientes del asistente,
  movimientos en USD y 12 meses con unos 30 movimientos por mes (15-10-2026). Este change es la
  parte de los pendientes del asistente; USD y los 12 meses son FIN-96 y FIN-97.

## What Changes

- `demo-data.sql` le crea al usuario de la demo (`demo@`, o `dev@` con `u = 1`) dos grupos de
  pendientes con origen `TELEGRAM`, con las mismas columnas que deja el alta en lote del asistente
  (`PendienteDelAsistenteAdapter`): cuenta y categoría resueltas, `status` `PENDING`, un `group_id`
  por mensaje y sin `shared_by_user_id`.
  - Un grupo de hace un día con tres movimientos: dos gastos en la tarjeta (uno con la fecha del
    mensaje y otro con una fecha explícita de dos días antes, a las 12:00, como fecha el asistente
    un día nombrado) y una transferencia de la nómina a Nequi.
  - Un grupo de hace unas horas con un solo gasto en efectivo.
- Los pendientes no mueven saldos ni cuentan en reportes ni en metas: lo garantiza el sistema por el
  `status`, y el change lo comprueba contra el API y con `check-saldos.sql`.
- La recarga los deja otra vez sin confirmar: caen con el `DELETE` del usuario, como el resto.
- El encabezado de `demo-data.sql` dice qué pendientes trae y para qué pantalla, el comentario de
  *Cuentas* deja de decir que Nequi no tiene movimientos, y el resumen final del script cuenta los
  pendientes propios por grupo.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: cambia un script de datos, no el comportamiento del sistema (`skip_specs: true`). Las filas
siguen lo que ya describen `asistente` (*Crear un movimiento desde el asistente*, *Varios movimientos
en un mensaje*) y `transacciones` (*Grupo de un pendiente*, *Un pendiente no mueve saldos*).

## Fuera de alcance

- Pendientes del asistente en la cuenta en dólares o convertidos de otra moneda: FIN-96.
- Más meses o más volumen de movimientos: FIN-97.
- Pendientes del asistente para las personas afiliadas: la tarea los pide solo para el usuario.
- Cambios en `test-data.sql`, en `bruno/` o en el API.

## Impact

- `docs/database/demo-data.sql`
- Sin cambios en `src/`, `bruno/`, el esquema ni el contrato del API. En Neon, la próxima recarga de
  la demo agrega los pendientes.
