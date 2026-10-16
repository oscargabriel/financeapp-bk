# Proposal

Origen: FIN-101, del epic FIN-11 (Asistente y Telegram). Desbloquea FIN-102 (Frontend), que muestra
bajo la descripción de un pendiente «Desde Telegram» o «Desde correo».

## Why

La pantalla de pendientes del front va a decir por qué canal entró cada movimiento, y el correo es
uno de ellos aunque su ingesta (FIN-12) no exista todavía. Hoy `origin` solo admite `WEB`,
`TELEGRAM` e `IMPORT`, así que no hay forma de guardar ni mostrar un pendiente de correo. Además, la
demo solo trae pendientes de Telegram y compartidos: el gasto en dólares y los importados están
confirmados, y el front no ve cómo se muestran esos casos.

Decisiones del epic que aplica:

- «El origen `EMAIL` existe desde ya, antes de la ingesta de FIN-12, para que la pantalla y la demo
  lo muestren. La demo trae pendientes de cada origen» (15-10-2026).
- «Un movimiento queda pendiente porque no se ha confirmado, no por su origen» (13-10-2026): `EMAIL`
  no decide el estado, igual que los otros orígenes.

## What Changes

- `TransactionOrigin` agrega `EMAIL`. El check `ck_transactions_origin` lo admite en `schema.sql` y
  en un update nuevo de `docs/database/update/`. Ningún endpoint lo produce: el alta sigue poniendo
  `WEB` y el asistente `TELEGRAM`, y un `origin` en el cuerpo se sigue ignorando.
- Un movimiento guardado con `EMAIL` sale con `"origin": "EMAIL"` en la lista de pendientes y en la
  aprobación, uno a uno o por grupo, y aprobarlo lo conserva. El rechazo responde 204 sin cuerpo,
  así que no hay origen que devolver: basta con que acepte el movimiento como cualquier otro.
- `docs/api/transacciones.md` agrega `EMAIL` a la tabla de `origin`: reservado para la ingesta desde
  correo (FIN-12); hoy solo lo traen los datos de demo y de prueba.
- `demo-data.sql` deja a `demo@` y a `dev@` pendientes de los orígenes que no tenían, en el mes en
  curso y el anterior:
  - `EMAIL`, en grupos, con descripciones de notificación bancaria (compras con tarjeta, débitos
    automáticos).
  - `WEB`, un gasto que llegó en USD a la Mastercard en COP, con `original_amount`.
  - `IMPORT`, un grupo de varios movimientos.
- `test-data.sql` trae un pendiente `EMAIL` para `pendientes@`, y `bruno/pending/` comprueba que se
  lista y se aprueba con ese origen.

Los datos de demo no son comportamiento: no van a la spec (sus montos los decide el front, ver
`AGENTS.md`).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: el requisito *Origen de un movimiento* admite `EMAIL`, dice que ningún endpoint lo
  produce y agrega el escenario de un pendiente de correo que se lista y se aprueba conservando su
  origen.

## Fuera de alcance

- La ingesta desde correo: es FIN-12.
- Mostrar el origen en la pantalla de pendientes: es FIN-102, del front.
- Un endpoint que produzca `IMPORT`: sigue reservado.

## Impact

- Código: `TransactionOrigin` y tests de las capas que leen y devuelven el origen. El adapter R2DBC
  ya lo lee con `TransactionOrigin.valueOf`, y la respuesta con `name()`: no cambian.
- Base: `schema.sql` y `docs/database/update/20261015_02_origen_email.sql`. Solo amplía el check: no
  reinicia los datos de la base local. En Neon va antes de recargar la demo, que ya trae filas
  `EMAIL`; la app vieja no sabe leerlas, así que la demo se recarga después de desplegar.
- Datos: `demo-data.sql` y `test-data.sql`.
- Bruno: `bruno/pending/` (las cuentas de pendientes y un request nuevo de aprobación). No cambia
  ningún endpoint, así que no hay nada que replicar en `bruno-personal/`.
- Contrato: `docs/api/transacciones.md`. No cambia la forma de ninguna respuesta; el front lo trae
  con `npm run sincronizar-contrato`.
