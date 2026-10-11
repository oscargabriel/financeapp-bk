# Proposal

Tarea **FA-76** del tablero. Salió el 06-10-2026 al dividir la etapa 14: lo que registre el
asistente de IA tiene que quedar pendiente de aprobación, y `transactions` no tiene estado. El
usuario decidió que el estado entra ya, antes del endpoint con IA (FA-77), y no se aplaza a la etapa
de Telegram. Depende de FA-75, que ya está en `Por revisar`: la consulta de saldo existe y aquí se
fija que los pendientes no cuentan en ella.

## Why

FA-77 va a crear movimientos a partir de texto libre interpretado por un modelo. Un error de
interpretación no puede mover saldos ni aparecer en los reportes hasta que el usuario lo vea. Hace
falta que un movimiento pueda existir sin efecto, y una forma de revisarlo y decidir sobre él.

## What Changes

- `transactions` gana la columna `status`, con los valores `PENDING` y `CONFIRMED`. El default es
  `CONFIRMED`: todo lo que ya existe y todo lo que entra por `POST /api/transactions` sigue igual.
  Ningún endpoint de este change crea pendientes; eso lo hará FA-77.
- Un pendiente no mueve `current_balance`: el trigger de saldos lo ignora, y al aprobarlo aplica
  su efecto.
- Un pendiente no cuenta en `GET /api/reports/transactions`, ni en `GET /api/reports/balance`, ni
  en el gasto mensual (`GET /api/monthly-spending` y las vistas `v_monthly_spending*`).
- Endpoints nuevos:
  - `GET /api/transactions/pending` lista los pendientes del usuario.
  - `POST /api/transactions/{id}/approve` lo confirma, responde 200 con el movimiento y mueve el
    saldo.
  - `POST /api/transactions/{id}/reject` lo borra y responde 204, sin dejar rastro en saldos ni en
    reportes.
- Aprobar o rechazar un movimiento ajeno o inexistente da 404 `NOT_FOUND`. Si el movimiento ya
  está confirmado, da 409 con un código nuevo, `INVALID_STATE`, en el campo `status`.
- La respuesta de un movimiento (alta, modificación, aprobación y lista de pendientes) gana el
  campo `status`. Es un campo nuevo y no cambia los que ya había.
- `PATCH` y `DELETE` sobre un pendiente siguen funcionando: se puede corregir antes de aprobar, y
  la modificación lo deja pendiente.
- Update `docs/database/update/20261008_01_transacciones_estado_aprobacion.sql`. Se aplica **antes**
  de desplegar la app, y en local **reinicia los datos**: después de aplicarlo se corre
  `cargar-datos-local.sql`, y el PR lo avisa para el front.
- `test-data.sql` siembra un usuario propio para los pendientes, `pendientes@financeapp.local`.
  Lo usa una carpeta nueva de `bruno/` y no toca los totales que verifican las demás carpetas.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: el estado de aprobación, los tres endpoints de pendientes, el saldo que no
  se mueve hasta aprobar, y `PATCH` y `DELETE` sobre un pendiente.
- `reportes`: los pendientes quedan fuera del reporte de movimientos, de la consulta de saldo y
  del gasto mensual.

## Fuera de alcance

- **Crear pendientes desde el API.** Lo hará el asistente en FA-77. Aquí solo se siembran con
  `test-data.sql`.
- **Guardar los rechazos.** Rechazar borra la fila, igual que `DELETE`, porque los movimientos no
  tienen borrado lógico. Si FA-77 necesita aprender de lo rechazado, se agrega un estado entonces.
- **Borrar una cuenta que tiene pendientes.** Hoy se puede borrar una cuenta en cero aunque tenga
  pendientes, porque estos no mueven su saldo. Al aprobar uno después, la cuenta borrada quedaría
  con saldo. Va como tarea nueva, FA-97.
- **Pendientes en la demo del front.** `demo-data.sql` no los siembra: el front todavía no tiene
  esa pantalla.
- **Aprobar o rechazar en lote.** Uno por uno, hasta que el front o el asistente lo pidan.

## Impact

- Esquema: la columna `status` con su CHECK, un índice parcial para los pendientes, la función
  `sync_account_balances` y las dos vistas de gasto mensual. Va en `schema.sql` y en el update, y
  se corre la comparación de esquemas de `docs/database/modelo-datos.md`.
- Dominio: un enum `TransactionStatus` y el campo `status` en `Transaction`. Se crean tres puertos
  de entrada con sus casos de uso, y `TransactionRepositoryPort` gana tres operaciones.
- Web: tres endpoints en `TransactionController`, `status` en `TransactionResponse` y
  `INVALID_STATE` en `ErrorCodes`.
- Persistencia: `TransactionR2dbcAdapter`, más el filtro de estado en `TransactionReportR2dbcAdapter`
  y `BalanceR2dbcAdapter`.
- Datos y documentación:
  - `test-data.sql` agrega el usuario de pendientes;
  - `AGENTS.md` lo suma a la tabla de usuarios del back;
  - `docs/api/contrato-api.md` documenta las rutas nuevas y el campo `status`;
  - `docs/database/modelo-datos.md` documenta la columna.
- Bruno: carpeta nueva `bruno/pending/`, replicada en `bruno-personal/`. Aprueba y rechaza filas
  sembradas, así que **entre dos corridas hay que recargar `test-data.sql`**. Es lo que ya hace
  `verificar-bruno.ps1 -RecargarDatos`, y el `docs` de la carpeta lo dice.
