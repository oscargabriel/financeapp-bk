# Tasks

## 1. Esquema, escenario y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`: función `finance.scheduled_balance_delta(p_account_id UUID)`
  (`STABLE`, `LANGUAGE sql`) que suma el efecto de los movimientos `CONFIRMED` con
  `occurred_at > now()` sobre la cuenta, como origen (`balance_delta`) y como destino de una
  transferencia (`COALESCE(destination_amount, amount)`). Las dos vistas de gasto mensual suman solo
  `occurred_at <= now()`, y su comentario de reglas comunes lo dice. El comentario de
  `current_balance` dice que incluye lo programado y que el vigente es la resta. Emitir lo mismo en
  `docs/database/update/20261009_03_movimientos_programados.sql`, con encabezado (va ANTES de
  desplegar la app; en local hay que recargar los datos) y la reversión comentada.
  Documentar en `docs/database/modelo-datos.md` el saldo vigente y los programados. Aplicarlo a la base
  local y correr la comparación de esquemas de `modelo-datos.md`: tiene que salir limpia.
- [x] 1.2 `docs/database/test-data.sql`:
  - Los movimientos del mes en curso —gastos e ingresos, las dos transferencias y el bloque de
    pendientes— pasan a `LEAST(m0 + desfase, now())`, con un comentario del porqué (FA-106).
  - La cena de 50.000 deja de ser la fila de frontera y pasa a ser un gasto más del mes en curso,
    recortado.
  - Los `Buses` de 35.000 de hace dos meses pasan al último día de ese mes a las 21:30 de Bogotá y
    quedan como la fila de frontera.

  Recargar con `cargar-datos-local.sql`.
- [x] 1.3 Frontera de mes en `bruno/reports/`: `categorias-escenario.yml` calcula también el último
  día de hace dos meses y el primero del mes anterior. `reporte-borde-ultimo-dia`,
  `reporte-borde-dia-siguiente` y `saldo-borde-ultimo-dia` apuntan a esos días y esperan los `Buses`
  (`saldo-borde-ultimo-dia`: `expense` 35000). Verificar que toda la colección pasa contra la app de
  `dev` con `verificar-bruno.ps1 -RecargarDatos`: con el escenario recortado, ninguna constante cambia.
- [x] 1.4 `scheduled` en las listas exactas de claves de `transactions/lote-de-uno.yml`,
  `pending/listar-pendientes.yml` y `reports/reporte-mes.yml`, con `false` en los movimientos del
  escenario.
- [x] 1.5 Carpeta nueva `bruno/scheduled/` con un usuario `programados-<algo>@bruno.local` propio. Las
  fechas las calculan los scripts: "dentro de 30 días", "ayer", "dentro de 5 segundos".
  - Alta y login del usuario.
  - Una cuenta `CASH` con saldo 500000, otra `CASH` en 0 y una `CREDIT` con cupo 3000000 y saldo -200000.
  - El id de la categoría `Mercado`.
  - `gasto-programado`: un lote con un gasto de 40000 dentro de 30 días y otro de 10000 sin fecha →
    201 con `scheduled` `true` y `false`.
  - `cuentas-con-programado`: la `CASH` en 490000.
  - `transferencia-programada`: 50000 de la primera a la segunda `CASH`, dentro de 30 días → ninguna
    de las dos cambia.
  - `tarjeta-con-programado`: un gasto programado de 100000 en la `CREDIT` → `currentBalance` -200000 y
    `availableCredit` 2800000.
  - `reporte-con-programado`: `reports/transactions` desde hoy hasta dentro de 31 días → el gasto de
    40000 en la lista con `scheduled` `true`; `EXPENSE` con `total` 10000 y `count` 1; `net` -10000.
  - `reporte-solo-programados`: el día del gasto programado → la lista lo trae, los tres tipos en cero,
    `totalsByCategory` vacía y `net` 0.
  - `saldo-con-programado`: `reports/balance` de ese día → `period.expense` 0 y `allTime.expense` 10000.
  - `gasto-mensual-con-programado`: `monthly-spending` del mes de ese día → no le suma los 40000 (si el
    mes es el actual, `totalSpent` 10000; si no, sin fila o en cero).
  - `programado-a-pasado`: PATCH `occurredAt` ayer → 200, `scheduled` `false`, y la `CASH` en 450000.
  - `programado-a-futuro`: PATCH otra vez a dentro de 30 días → `scheduled` `true`, y la `CASH` en
    490000.
  - `vence-en-segundos`: un gasto de 40000 a 5 segundos → `scheduled` `true`, y la `CASH` sigue en
    490000.
  - `cuentas-al-vencer`: con `await bru.sleep(6000)` en el pre-request → la `CASH` en 450000.
  - `saldo-al-vencer`: `allTime.expense` 50000.
  - `gasto-mensual-con-basic`: si a `monthly-spending/gasto-mensual-sin-credenciales` y
    `gasto-mensual-con-basic` les falta `field` `authorization` o `WWW-Authenticate: Bearer`, se
    agregan ahí.

  Verificar que los requests nuevos de lo programado fallan hoy con `verificar-bruno.ps1
  -RecargarDatos`.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `TransactionReportTest` en RED: con un `asOf`, los movimientos con `occurredAt` posterior no
  entran en `totalsByType`, en `totalsByCategory` ni en `net`, pero siguen en `transactions`; un
  reporte con solo programados da los tipos en cero y `totalsByCategory` vacía; un movimiento justo
  en `asOf` cuenta. `TransactionReport` gana `asOf`, y `of(...)` lo recibe. `Transaction` y
  `ReportedTransaction` ganan `scheduledAt(Instant)`. Verificar con la clase en verde.

## 3. Aplicación

Skills: `java-architect`.

- [x] 3.1 `GetTransactionReportUseCaseTest` en RED: el reporte se arma con el instante del `Clock`.
  `GetTransactionReportUseCase` recibe el `Clock`. `AssistantUseCase` no arma el reporte: lo pide por
  `GetTransactionReportPort`, asi que no cambia. Verificar con `GetTransactionReportUseCaseTest` y
  `AssistantUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-security`.

- [x] 4.1 `TransactionControllerTest` en RED: el alta, el PATCH, la aprobación y la lista de
  pendientes devuelven `scheduled` según el `Clock` (un movimiento a futuro en `true`, uno pasado en
  `false`); un `scheduled` en el cuerpo del alta se ignora. `TransactionResponse.from(t, ahora)` y el
  `Clock` en `TransactionController` y en `AssistantController`. Verificar con
  `TransactionControllerTest` y `AssistantControllerTest` en verde.
- [x] 4.2 `TransactionReportControllerTest` en RED: cada movimiento trae `scheduled` según `asOf`.
  `TransactionReportResponse` lo calcula. Verificar con la clase en verde.
- [x] 4.3 `MonthlySpendingIT`: sin credencial y con el Basic compartido dan 401 con `UNAUTHENTICATED`
  en `authorization` y `WWW-Authenticate: Bearer`; agregar los casos que falten. Verificar con la
  clase en verde.

## 5. Persistencia

- [x] 5.1 `AccountR2dbcAdapter`: `current_balance` sale como
  `current_balance - finance.scheduled_balance_delta(id)` en todas sus lecturas y en sus `RETURNING`.
  `BalanceR2dbcAdapter`: el `LEFT JOIN` suma solo `t.occurred_at <= now()`. Sin test de suite: lo
  verifican en verde los requests del grupo 1.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`:
  - Una sección sobre los movimientos programados.
  - `scheduled` en la respuesta de un movimiento y en el ítem de `reports/transactions`.
  - Los totales, el neto, `reports/balance` y `monthly-spending` sin lo programado.
  - `currentBalance` como saldo vigente.

  Verificar leyendo las secciones.
- [x] 6.2 `bruno-personal/`: este change no cambia rutas, cuerpos ni autenticación. En los `docs` de
  `movimientos/` (alta y modificación) agregar que una fecha futura deja el movimiento programado. No
  se ejecutan.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
