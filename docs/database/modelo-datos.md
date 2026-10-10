# Modelo de datos — financeapp-bk

Esquema `finance` sobre PostgreSQL 14+. La fuente de verdad es
[`schema.sql`](schema.sql); este documento explica el porqué de las decisiones y
cómo se usa el modelo.

## Diagrama

```mermaid
erDiagram
    currencies      ||--o{ exchange_rates : "from / to"
    currencies      ||--o{ users          : "moneda base"
    currencies      ||--o{ accounts       : denomina
    currencies      ||--o{ transactions   : denomina
    currencies      ||--o{ budgets        : denomina
    users           ||--o{ accounts       : posee
    users           ||--o{ categories     : personaliza
    users           ||--o{ transactions   : registra
    users           ||--o{ budgets        : define
    accounts        ||--o{ transactions   : origen
    accounts        ||--o{ transactions   : destino
    categories      ||--o{ transactions   : clasifica
    users           ||--o{ recurrences    : programa
    accounts        ||--o{ recurrences    : "carga a"
    categories      ||--o{ recurrences    : clasifica
    recurrences     |o--o{ transactions   : "ocurrencia de"
    categories      ||--o{ budgets        : "tope por categoría"
    default_categories ..|| categories    : "se copia al registrarse"

    users {
        uuid id PK
        varchar email UK
        varchar password_hash
        varchar first_name
        varchar last_name
        varchar phone
        date birth_date
        bigint telegram_chat_id UK
        char base_currency_code FK
        varchar timezone
        boolean is_active
        timestamptz deleted_at
    }

    accounts {
        uuid id PK
        uuid user_id FK
        varchar name
        varchar type
        char currency_code FK
        numeric initial_balance
        numeric current_balance
        numeric credit_limit
        smallint statement_day
        smallint payment_due_day
        numeric monthly_interest_rate
        boolean is_active
        timestamptz deleted_at
    }

    categories {
        uuid id PK
        uuid user_id FK
        varchar name
        varchar applies_to
        varchar icon
        char color
        boolean is_system
        timestamptz deleted_at
    }

    transactions {
        uuid id PK
        uuid user_id FK
        uuid account_id FK
        uuid destination_account_id FK
        uuid category_id FK
        varchar type
        numeric amount
        numeric destination_amount
        char currency_code FK
        numeric exchange_rate
        numeric amount_base
        varchar description
        timestamptz occurred_at
        varchar origin
        varchar status
        uuid recurrence_id FK
    }

    recurrences {
        uuid id PK
        uuid user_id FK
        uuid account_id FK
        uuid category_id FK
        varchar type
        numeric amount
        varchar description
        varchar frequency
        smallint interval_count
        smallint day_of_week
        smallint day_of_month
        date start_date
        date end_date
        smallint occurrence_limit
        integer generated_count
        integer prior_count
        varchar status
    }

    budgets {
        uuid id PK
        uuid user_id FK
        date period_month
        uuid category_id FK
        numeric amount
        char currency_code FK
    }

    currencies {
        char code PK
        varchar name
        varchar symbol
        smallint decimal_places
        boolean is_active
    }

    exchange_rates {
        uuid id PK
        char from_currency_code FK
        char to_currency_code FK
        numeric rate
        date rate_date
        varchar source
    }

    default_categories {
        uuid id PK
        varchar name UK
        varchar applies_to
        varchar icon
        char color
        smallint sort_order
    }

    registration_allowlist {
        varchar entry PK
        timestamptz created_at
    }
```

## Convenciones

| Tema | Decisión |
|---|---|
| Esquema | `finance`, no `public`. Aísla el modelo de extensiones y de cualquier otra cosa instalada en la base. |
| Nombres | `snake_case`, tablas en plural, FK `<entidad>_id`, índices `ix_`, únicos `ux_`, checks `ck_`, triggers `trg_`. |
| PK | `UUID` v7 generado por la aplicación con `domain/model/UuidV7`. Ordenables por tiempo, seguros de exponer en la API. **El JDK 25 no trae v7** —`java.util.UUID` solo ofrece `randomUUID` (v4), `nameUUIDFromBytes` y `fromString`—, así que la clase implementa el RFC 9562 a mano; comprobado el 15-09-2026 al escribir el primer INSERT (FA-12). Cuando el SQL genera la PK en una sola sentencia, como la copia de la semilla al registrarse, usa `uuidv7()`, nativo desde PostgreSQL 18. Las tablas de catálogo (`currencies`, `default_categories`, `exchange_rates`) sí llevan default en la base, porque se pueblan por SQL. |
| Montos | `NUMERIC(18,4)`. Cuatro decimales para no perder precisión al convertir moneda, aunque el COP se maneje en enteros. |
| Enums | `VARCHAR` + `CHECK`, no tipos `ENUM` nativos: R2DBC los mapea sin códec extra y agregar un valor es cambiar un CHECK, no un `ALTER TYPE` irreversible. |
| Tiempo | `TIMESTAMPTZ` siempre. `created_at` / `updated_at` en toda tabla, `updated_at` mantenido por el trigger `set_updated_at()`. |
| Borrado | Lógico (`deleted_at`) en `users`, `accounts` y `categories`; físico en `transactions`. Los índices únicos son parciales con `WHERE deleted_at IS NULL`, de modo que borrar libera el nombre. |
| FK de `transactions` | `NO ACTION DEFERRABLE INITIALLY DEFERRED` hacia `accounts` y `categories`. Impide igual borrar una cuenta o categoría con movimientos, pero al verificarse al cierre de la transacción no choca con el borrado en cascada de un usuario, donde `RESTRICT` fallaría según el orden en que Postgres procesa las cascadas. |

## Decisiones que conviene conocer antes de tocar el modelo

### El saldo lo mantiene la base, no la aplicación

`accounts.current_balance` lo escribe únicamente el trigger
`trg_transactions_sync_balance`. La aplicación **nunca** lo actualiza: si lo
hiciera, el saldo se desincronizaría al primer camino que se olvide de ajustarlo.
En `UPDATE` el trigger revierte el efecto de la fila vieja y aplica el de la
nueva, así que cambiar la cuenta, el tipo o el monto de un movimiento es seguro.

`initial_balance` es el punto de partida y lo copia a `current_balance` el
trigger `trg_accounts_seed_balance` al crear la cuenta.

### Movimientos pendientes

`transactions.status` vale `CONFIRMED` (el default) o `PENDING`, y lo pone el
asistente de IA (FA-76). Un pendiente no tiene efecto hasta que el usuario lo
aprueba:

- el trigger de saldos lo ignora;
- aprobarlo es un `UPDATE` a `CONFIRMED`, y ahí el trigger aplica la fila;
- rechazarlo es un `DELETE`, sin nada que revertir.

Las vistas de gasto mensual, el reporte de movimientos y la consulta de saldo
filtran `status = 'CONFIRMED'`. Un pendiente sí cuenta como referencia: la FK
impide borrar la cuenta o la categoría que usa. El porqué está en el `design.md`
de `openspec/changes/archive/2026-10-08-fa-76-movimientos-pendientes/`.

### Movimientos programados

Un movimiento `CONFIRMED` con `occurred_at > now()` está programado (FA-106):
no cuenta en el saldo vigente ni en el gasto mensual hasta su fecha. No hay un
proceso que lo "active", porque el servicio puede estar apagado: todo se decide
al leer.

- El trigger de saldos no mira la fecha, así que `current_balance` incluye lo
  programado. El **saldo vigente** es
  `current_balance - finance.scheduled_balance_delta(id)`, y es el que lee la
  aplicación. Quien consulte la columna a mano con psql ve el saldo con lo
  programado incluido.
- Las vistas de gasto mensual y la consulta de saldo filtran
  `occurred_at <= now()`. El reporte de movimientos sí lista los programados, y
  la aplicación los deja fuera de sus totales.
- Cambiar la fecha con un `UPDATE` no necesita nada especial: el trigger revierte
  y reaplica lo mismo, y la resta se recalcula con la fecha nueva.

`test-data.sql` recorta a `now()` los movimientos del mes en curso para que
ninguno quede programado y los totales del escenario no dependan del día en que
se cargue. El porqué está en el `design.md` del change de FA-106.

### Series recurrentes

Una serie (`recurrences`, FA-107) es la plantilla y la regla de un gasto o
ingreso que se repite. Sus ocurrencias son movimientos normales con
`recurrence_id`, a las 00:00 de su día en la zona del usuario, y quedan
programadas hasta su fecha como cualquier otro movimiento futuro.

- `generated_count` cuenta las ocurrencias de la regla actual ya creadas. La
  siguiente se crea desde ese número, no desde las filas que existen: una
  ocurrencia borrada a mano no vuelve. `prior_count` guarda las de reglas
  anteriores para que el tope de `occurrence_limit` sobreviva a un cambio de
  periodicidad, que reinicia `start_date` en el día siguiente.
- Una serie con fin tiene todas sus ocurrencias creadas desde el alta. Una **sin
  fin** (`end_date` y `occurrence_limit` en NULL) tiene creadas las de hasta hoy
  y una más. No hay proceso que cree la siguiente cuando llega la fecha: **toda
  lectura de saldos o de movimientos pone al día las series sin fin del usuario
  antes de consultar**. Hoy lo hacen las cuentas, `reports/balance`,
  `reports/transactions`, `monthly-spending` y el listado de series. Una lectura
  nueva de saldos o movimientos tiene que hacerlo también, o mostrará el saldo
  sin las ocurrencias atrasadas.
- La puesta al día escribe las ocurrencias y el contador nuevo en una
  transacción, con el `UPDATE` condicionado al contador que leyó: dos lecturas a
  la vez no duplican nada.
- Cancelar borra las ocurrencias futuras y marca `status = 'CANCELLED'`. La fila
  se conserva por las ocurrencias pasadas que la referencian.

### El signo lo da el tipo, nunca el monto

`amount` siempre es positivo (`CHECK amount > 0`). `EXPENSE` y `TRANSFER` restan
del origen, `INCOME` suma. Guardar montos negativos rompería tanto los reportes
como el trigger.

### Tarjetas de crédito

Una cuenta `CREDIT` con saldo vigente negativo está en deuda; el cupo
disponible es `credit_limit` más el saldo vigente. Los campos `credit_limit`,
`statement_day`, `payment_due_day` y `monthly_interest_rate` solo pueden tener
valor si `type = 'CREDIT'` (lo garantiza `ck_accounts_credit_fields`).

`monthly_interest_rate` es la tasa de interés mensual en **porcentaje**, no en
fracción: `2.15` es el 2,15 % mensual. Va de 0 a 10; el tope atrapa una tasa
anual escrita por error en el campo mensual (FA-105).

### Transferencias

Una transferencia es **una** fila con `account_id` (origen) y
`destination_account_id` (destino), sin categoría. Si las dos cuentas tienen
monedas distintas, `destination_amount` guarda lo que efectivamente entra al
destino; con `NULL` el trigger asume el mismo monto. Sin esa columna, una
transferencia de USD a COP sumaría dólares a una cuenta en pesos.

Las transferencias **no** cuentan como gasto en las vistas: mueven dinero entre
cuentas propias, no lo consumen. Los pendientes tampoco cuentan, hasta aprobarse.

### Multi-moneda

Cada movimiento guarda tres cosas: `amount` en la moneda de la cuenta,
`exchange_rate` usada, y `amount_base` ya convertido a la moneda base del
usuario. Los reportes suman `amount_base`, que es lo único comparable entre
monedas. Mientras todo se lleve en pesos, `currency_code = 'COP'`,
`exchange_rate = 1` y `amount_base = amount`.

La tasa se busca en `exchange_rates` tomando la fila con `rate_date` más reciente
menor o igual a la fecha del movimiento. El valor se congela en la transacción:
recalcularlo después cambiaría el histórico.

### Categorías

`default_categories` es el catálogo semilla. Al registrarse un usuario se copian
sus filas a `categories` con `is_system = TRUE`. Desde ahí cada quien edita,
agrega o borra las suyas sin afectar a nadie más, y cambiar la semilla no toca a
los usuarios existentes.

`applies_to` (`EXPENSE` / `INCOME` / `BOTH`) evita ofrecer «Salario» al registrar
un gasto.

### Correos admitidos en el registro

`registration_allowlist` dice quién puede registrarse cuando la restricción del
alta está encendida (FA-103). Cada `entry`, en minúsculas, es:

- un correo exacto (`ana@correo.com`);
- o, sin nada antes de la `@`, un dominio completo (`@bruno.local`).

El dominio se compara exacto, así que no admite subdominios. La tabla no se
relaciona con `users` a propósito: guarda personas que todavía no tienen cuenta,
y el login no la consulta. Las filas se insertan y se borran, nunca se
modifican, por eso no lleva `updated_at`. Se mantiene a mano: las sentencias
están en `docs/despliegue.md`. El porqué del diseño está en el `design.md` de
`openspec/changes/archive/2026-10-09-fa-103-registro-admitidos/`.

### El mes se corta en la zona del usuario

Las vistas agrupan por
`date_trunc('month', occurred_at AT TIME ZONE u.timezone)`. Un gasto del 30 de
septiembre a las 21:00 en Bogotá se guarda como 1 de octubre UTC; sin la
conversión caería en el mes equivocado.

### `occurred_at` no es `created_at`

`occurred_at` es cuándo ocurrió el movimiento; `created_at`, cuándo se registró.
El bot de Telegram va a registrar gastos de días anteriores y los reportes deben
usar el primero.

## Vistas

### `v_monthly_spending`

Una fila por usuario y mes.

```
user_id | period_month | currency_code | total_spent | budget_amount | remaining | percent_used | transaction_count
```

```sql
SELECT * FROM finance.v_monthly_spending
 WHERE user_id = $1
   AND period_month = date_trunc('month', CURRENT_DATE)::date;
```

`budget_amount`, `remaining` y `percent_used` son `NULL` cuando el mes no tiene
meta definida — no cero, para poder distinguir «no configuró meta» de «su meta es
cero». Un mes con meta y sin gastos aparece igual, con `total_spent = 0`.

### `v_monthly_spending_by_category`

El desglose del mismo mes, una fila por categoría con gasto o con tope.

```
user_id | period_month | category_id | category_name | category_icon | category_color |
currency_code | total_spent | category_budget | remaining | percent_used | share_of_month | transaction_count
```

`share_of_month` es el peso porcentual de la categoría sobre el total gastado ese
mes. `category_budget` solo tiene valor si se definió un tope para esa categoría.

Una categoría borrada sigue apareciendo, con su nombre, icono y color, en los
meses en que tuvo gasto: la vista no filtra `deleted_at`, y es a propósito
(FA-21). Ocultarla haría que el desglose dejara de sumar el `total_spent` de
`v_monthly_spending` y que `share_of_month` no llegara a 100.

### Metas

La meta global de un mes es la fila de `budgets` con `category_id IS NULL`:

```sql
INSERT INTO finance.budgets (id, user_id, period_month, category_id, amount, currency_code)
VALUES (:id, :user_id, DATE '2026-09-01', NULL, 1000000, 'COP');
```

Los topes por categoría son filas con `category_id` diligenciado, sobre el mismo
`period_month`. Dos índices únicos parciales impiden duplicados: uno para las
metas por categoría y otro para la global, porque en Postgres `NULL` no colisiona
con `NULL` y un único convencional dejaría crear varias metas globales del mismo
mes.

## Construcción y versionado

```
docs/database/
  schema.sql                      DDL completo desde cero
  seed.sql                        monedas y categorías por defecto
  reset-data.sql                  vacía los datos, conserva estructura y semilla
  drop.sql                        elimina todos los objetos del modelo
  test-data.sql                   escenario de pruebas de bruno/, re-ejecutable
  demo-data.sql                   datos de demo para demo@ y dev@, re-ejecutable
  cargar-datos-local.sql          carga los dos anteriores de una vez
  test-checks.sql                 consultas de verificación
  update/
    20260905_01_baseline.sql      línea base
```

Base nueva:

```bash
psql -U <usuario> -d financeapp -f docs/database/schema.sql
psql -U <usuario> -d financeapp -f docs/database/seed.sql
```

Cada cambio posterior se aplica **en `schema.sql`** y además se emite como script
incremental en `update/`, con nombre `AAAAMMDD_NN_descripcion.sql` (el año
delante para que el orden alfabético sea el cronológico). No hay herramienta de
migraciones ni tabla de control: aplicar los updates a una base concreta es
responsabilidad de quien administra la base.

### Por qué no hay Flyway

Decidido el **14-09-2026**, comparando esta convención contra meter Flyway sobre
JDBC solo para migrar. Gana la convención manual, y el porqué importa más que la
conclusión:

- El beneficio de Flyway —aplicar migraciones sin que nadie mire, en orden, con
  checksums, sobre entornos que no tocas a mano— **no tenía consumidor**: en esa
  fecha no había despliegue ni base de producción.
- Adoptarlo obliga a reescribir `20260905_01_baseline.sql` para que cargue las 509
  líneas de DDL, cuando hoy dice explícitamente que no las duplica, y a degradar
  `schema.sql` de fuente de verdad a archivo derivado. Es churn contra un
  beneficio que nadie cobra.

Lo que sí cuesta esta decisión, dicho sin adornos: **el doble apunte**. Editar
`schema.sql` y emitir el update son dos actos manuales, y nada garantiza que
sigan describiendo el mismo esquema. Si divergen, la base construida desde cero
y la base migrada dejan de ser la misma y no hay forma de enterarse por casualidad.
Para eso está la comprobación de abajo.

**Cuándo volver a mirar esto:** cuando exista el primer despliegue real. Ahí la
migración desatendida empieza a valer, y el precio de cambiar será reconciliar los
updates acumulados contra lo que tenga esa base.

**La condición se cumplió el 05-10-2026**: Neon es producción desde el primer
despliegue, y FA-49 aplicó ahí dos updates a mano. La reevaluación es FA-71.
Mientras tanto, la base de producción se mantiene con el procedimiento de
[`docs/despliegue.md`](../despliegue.md#base-de-datos-neon), que incluye el registro
de los updates aplicados.

### Comprobar que `schema.sql` y `update/` no han divergido

Construye dos bases desechables por los dos caminos y compara su estructura. Se
corre **después de emitir cada update**, que es cuando puede aparecer la
divergencia.

```powershell
$base = '00c3b63'   # commit de la línea base; no cambia
$tmp = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "equiv-$(Get-Random)")

psql -d postgres -q -c 'CREATE DATABASE fa_cero' -c 'CREATE DATABASE fa_mig'

# Camino 1: la base nueva, desde el schema.sql de hoy
psql -q -d fa_cero -f docs/database/schema.sql
psql -q -d fa_cero -f docs/database/seed.sql

# Camino 2: el schema.sql de la línea base, más cada update en orden
git show "${base}:docs/database/schema.sql" | Set-Content "$tmp\b.sql"
git show "${base}:docs/database/seed.sql"   | Set-Content "$tmp\s.sql"
psql -q -d fa_mig -f "$tmp\b.sql"
psql -q -d fa_mig -f "$tmp\s.sql"
Get-ChildItem docs/database/update/*.sql | Where-Object Name -notlike '*baseline*' |
    Sort-Object Name | ForEach-Object { psql -q -d fa_mig -f $_.FullName }

# pg_dump 18 inyecta un token \restrict distinto en CADA volcado: sin filtrarlo,
# la comparación nunca sale limpia. Las líneas en blanco tampoco significan nada,
# y si se dejan, Compare-Object inventa diferencias por su ventana de sincronía.
$ruido = 'restrict '
function Volcar($db) {
    pg_dump --schema-only --no-owner --no-privileges -n finance $db |
        Where-Object { $_ -notmatch $ruido -and $_.Trim() -ne '' }
}

$dif = Compare-Object (Volcar fa_cero) (Volcar fa_mig)
if ($dif) { 'HAY DERIVA'; $dif } else { 'sin diferencias' }

psql -d postgres -q -c 'DROP DATABASE fa_cero' -c 'DROP DATABASE fa_mig'
```

Verificado el 14-09-2026 contra PostgreSQL 18: 634 líneas comparadas por lado y
`sin diferencias`. Se probó además que detecta la deriva añadiendo una columna a
mano en la base migrada, y la reporta como `=> columna_intrusa text,`.

## Probar en local

`psql` vive en `C:\Program Files\PostgreSQL\18\bin` y ya está en el PATH de
usuario. Para no repetir credenciales en cada comando, una sola vez:

```powershell
# Evita que psql pregunte la contraseña. El archivo es de solo tu usuario.
New-Item -ItemType Directory -Force "$env:APPDATA\postgresql" | Out-Null
"localhost:5432:*:postgres:<tu-clave>" | Set-Content "$env:APPDATA\postgresql\pgpass.conf"

# Y para no repetir -U / -h / -d, en tu perfil de PowerShell:
$env:PGUSER = 'postgres'; $env:PGHOST = 'localhost'; $env:PGDATABASE = 'financeapp'
```

Con eso, `psql` entra directo y el ciclo de trabajo es:

```powershell
psql -f docs/database/test-data.sql     # borra y recrea el usuario de pruebas
psql -f docs/database/test-checks.sql   # saldos, vistas y metas
psql                                    # sesión interactiva para probar a mano
```

[`test-data.sql`](test-data.sql) es re-ejecutable: arranca borrando
`prueba@financeapp.local`, que arrastra en cascada sus cuentas, categorías,
movimientos y metas, y vuelve a construir el escenario. Correrlo dos veces
seguidas deja el mismo estado. Sus fechas son relativas al mes en curso, así que
siempre hay datos de «este mes» y «el mes pasado» sin tener que editarlo.

El escenario trae cuatro cuentas (efectivo, débito, tarjeta de crédito y ahorros
en dólares), las categorías de la semilla más una propia, gastos e ingresos de
dos meses, dos transferencias —una entre monedas distintas—, un gasto puesto a
propósito el último día del mes a las 21:30 en Bogotá, y metas donde
Restaurantes se pasa del tope.

[`test-checks.sql`](test-checks.sql) solo lee: saldos y cupo, la comprobación de
que el saldo del trigger coincide con el derivado de los movimientos, las dos
vistas, las categorías pasadas de tope y los últimos movimientos.

### Reiniciar el estado

Hay dos niveles, según qué tanto se quiera echar atrás:

```powershell
# 1. Vaciar los datos y conservar estructura, vistas y catálogo semilla.
psql -f docs/database/reset-data.sql
psql -f docs/database/cargar-datos-local.sql

# 2. Borrar todo el modelo y reconstruirlo (tras cambiar schema.sql).
psql -f docs/database/drop.sql
psql -f docs/database/schema.sql
psql -f docs/database/seed.sql
psql -f docs/database/cargar-datos-local.sql
```

[`cargar-datos-local.sql`](cargar-datos-local.sql) carga `test-data.sql` y la demo
de `demo@` y `dev@` (FA-94). Después de los dos niveles hay que correrlo, porque
los dos se llevan también los datos del front.

Para volver al escenario de pruebas sin tocar nada más, basta con volver a correr
`test-data.sql`: ya empieza borrando su propio usuario.

[`drop.sql`](drop.sql) lista los objetos uno por uno en vez de hacer
`DROP SCHEMA finance CASCADE`. Es equivalente, pero sirve de inventario y falla
de forma visible si alguien creó algo por fuera de `schema.sql`.

Cuidado con `TRUNCATE`: no dispara triggers de fila. Vaciar solo `transactions`
dejaría `accounts.current_balance` con el saldo viejo; `reset-data.sql` vacía
también las cuentas, así que no se da el caso, y el archivo trae la consulta de
recálculo por si alguna vez hace falta.

Dentro de una sesión interactiva:

```
\i docs/database/test-data.sql     -- recargar el escenario
\dt finance.*                      -- tablas
\d finance.transactions            -- estructura, checks e índices
\d+ finance.v_monthly_spending     -- definición de la vista
```

Al cambiar el modelo: se edita `schema.sql`, se emite el update en `update/`, se
reconstruye la base y se vuelve a correr `test-data.sql` y `test-checks.sql`.

## Configuración de la aplicación

El esquema no es `public`, así que cada conexión del pool abre con `search_path = finance`. Lo fija
`R2dbcSearchPathConfig`, que manda al driver la opción de arranque `options=-c search_path=finance`.

Hasta FA-47 (26-09-2026) lo hacía el parámetro `?schema=finance` de la URL. Contra PostgreSQL local
funcionaba, pero **Neon lo descarta**: el driver lo envía como parámetro de arranque `search_path`
y la conexión queda en `"$user", public`. Neon sí respeta `options=-c ...`, igual que `PGOPTIONS` en
`psql`. La URL no puede llevarlo porque el driver parte las opciones por `=` y el valor contiene uno.
El bean reemplaza el mapa de opciones del driver, así que un `?schema=` que quede en una URL (el
`application-local.yaml` lo conserva) no tiene efecto.

Se eligió resolverlo en código y no con `ALTER ROLE` o `ALTER DATABASE ... SET search_path` (FA-38
y FA-47) porque queda versionado con la aplicación; el cambio en la base habría que repetirlo a mano
en cada entorno. Los adapters siguen calificando `finance.` en el SQL.

## Pendiente para próximas iteraciones

- Préstamos, dinero bloqueado y dinero de terceros: no son saldo líquido y
  necesitan su propia tabla, no una cuenta más.
- Personas afiliadas y finanzas compartidas entre usuarios.
- Carga automática de tasas de cambio (`exchange_rates.source = 'API'`).
