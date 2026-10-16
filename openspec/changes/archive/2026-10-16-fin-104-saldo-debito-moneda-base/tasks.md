# Tasks

## 1. Escenario y requests de Bruno, fallando

Skills: `bruno-cli`.

- [x] 1.1 En `docs/database/test-data.sql`, el usuario `saldo@financeapp.local` (COP, `America/Bogota`, contraseña `claveDePrueba123`) con las cuentas y los movimientos de la tabla de `design.md`, `created_at` de «Débito dólares» hace 50 días, y su correo en el `DELETE` del inicio. Se verifica cargándolo con `psql -v ON_ERROR_STOP=1` y con `check-saldos.sql` sin cuentas descuadradas.
- [x] 1.2 Carpeta `bruno/reports/` con login de `saldo@`, consulta de saldo que espera `debitBalance` 1551000 y «Débito dólares» con `currentBalance` 190 en USD, `PATCH /api/users/me` a USD, consulta que espera `currencyCode` USD y `debitBalance` 382.6829, y `PATCH` de vuelta a COP. Se verifica con `bru run reports -r --env local` fallando porque `debitBalance` no existe.
- [x] 1.3 En `bruno/reports/saldo-mes.yml`, `debitBalance` en las claves del contrato e igual al `currentBalance` de Bancolombia; en `bruno/reports/usd-saldo.yml`, `debitBalance` 0. Se verifican en la misma corrida, fallando.

## 2. La vista

- [x] 2.1 `finance.v_account_balance_in_base` en `schema.sql`, con su `COMMENT ON VIEW` y el bloque de reglas como el de las vistas de gasto mensual, y su `DROP VIEW` en `drop.sql`. Se verifica reconstruyendo la base local con `drop.sql` + `schema.sql` + `seed.sql` + `cargar-datos-local.sql` sin errores.
- [x] 2.2 Update `docs/database/update/20261016_01_saldo_en_moneda_base.sql`, con el encabezado que dice que va antes del despliegue y que no reinicia datos. Se verifica con la comparación de esquemas de `modelo-datos.md` («Comprobar que `schema.sql` y `update/` no han divergido»).
- [x] 2.3 `modelo-datos.md`: sección de la vista bajo *Vistas* (columnas, consulta de ejemplo, cómo convierte el saldo inicial y lo que entra por una transferencia) y una línea en *Multi-moneda* que la nombre. Se verifica leyendo que las reglas coincidan con el DDL.

## 3. El total en la consulta de saldo

Skills: `java-architect`.

- [x] 3.1 `GetBalanceUseCaseTest`: el `debitBalance` de `Balance.Sums` llega a `Balance`. Rojo, después `Balance.Sums` y `Balance` con `debitBalance`.
- [x] 3.2 `TransactionReportControllerTest` y `TransactionReportIT`: la respuesta de `/api/reports/balance` trae `debitBalance` de primer nivel. Rojo, después `BalanceResponse`. El IT no cambia: sin base no tiene un 200 de esta ruta, y el campo con datos reales lo comprueba `bruno/reports/`.
- [x] 3.3 `BalanceR2dbcAdapter` suma `v_account_balance_in_base` con `account_type = 'DEBIT' AND is_active` como subconsulta escalar con `COALESCE` a 0, y su comentario dice por qué. Se verifica con los requests del grupo 1 en verde.
- [x] 3.4 `docs/api/reportes.md`: `debitBalance` en el ejemplo y en la tabla de campos, y «Las cuentas no se suman» reescrito para decir que la única suma es `debitBalance` y cómo convierte. La descripción de su fila en `docs/api/contrato-api.md`. Se verifica comparando el ejemplo con la respuesta real de `saldo-mes.yml`.
- [x] 3.5 `bruno-personal/`: el request de la consulta de saldo no cambia de ruta, cuerpo ni autenticación; se confirma leyéndolo, sin ejecutarlo.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo de requests y `check-saldos.sql` sin descuadres.

## Workflow follow-up

- Archivar el change en la misma rama antes del PR.
- PR a `dev` con el change archivado, merge con `merge-pr-dev.ps1`, y FIN-104 a `Por revisar`.
- El update se aplica en Neon antes de que el merge a `main` despliegue la app.
