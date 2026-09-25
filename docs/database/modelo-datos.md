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

### El signo lo da el tipo, nunca el monto

`amount` siempre es positivo (`CHECK amount > 0`). `EXPENSE` y `TRANSFER` restan
del origen, `INCOME` suma. Guardar montos negativos rompería tanto los reportes
como el trigger.

### Tarjetas de crédito

Una cuenta `CREDIT` con `current_balance` negativo está en deuda; el cupo
disponible es `credit_limit + current_balance`. Los campos `credit_limit`,
`statement_day` y `payment_due_day` solo pueden tener valor si `type = 'CREDIT'`
(lo garantiza `ck_accounts_credit_fields`).

### Transferencias

Una transferencia es **una** fila con `account_id` (origen) y
`destination_account_id` (destino), sin categoría. Si las dos cuentas tienen
monedas distintas, `destination_amount` guarda lo que efectivamente entra al
destino; con `NULL` el trigger asume el mismo monto. Sin esa columna, una
transferencia de USD a COP sumaría dólares a una cuenta en pesos.

Las transferencias **no** cuentan como gasto en las vistas: mueven dinero entre
cuentas propias, no lo consumen.

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
  test-data.sql                   escenario de pruebas, re-ejecutable
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
  checksums, sobre entornos que no tocas a mano— **no tiene consumidor todavía**.
  No hay despliegue: `PROD_HOST` está vacío y `SPRING_PROFILES_ACTIVE=prod` no se
  ha ejecutado nunca.
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
psql -f docs/database/test-data.sql

# 2. Borrar todo el modelo y reconstruirlo (tras cambiar schema.sql).
psql -f docs/database/drop.sql
psql -f docs/database/schema.sql
psql -f docs/database/seed.sql
psql -f docs/database/test-data.sql
```

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

El esquema no es `public`, así que la conexión R2DBC apunta a `finance` con el parámetro de la
URL, que fija el `search_path` de cada conexión del pool:

```
spring.r2dbc.url=r2dbc:postgresql://host:5432/financeapp?schema=finance
```

Se eligió la URL y no `ALTER ROLE <usuario> SET search_path = finance, public;` (FA-38,
24-09-2026) porque la URL queda versionada con la aplicación, mientras que el rol es un cambio en
la base que habría que repetir a mano en cada entorno.

Hoy lo llevan `application.yaml` y `application-local.yaml`. `application-prod.yaml` sobrescribe la
URL **sin** el parámetro: se ajusta junto con las credenciales de producción, antes del pase. Los
adapters siguen calificando `finance.` en el SQL, lo que funciona con o sin el parámetro.

## Pendiente para próximas iteraciones

- Préstamos, dinero bloqueado y dinero de terceros: no son saldo líquido y
  necesitan su propia tabla, no una cuenta más.
- Personas afiliadas y finanzas compartidas entre usuarios.
- Carga automática de tasas de cambio (`exchange_rates.source = 'API'`).
