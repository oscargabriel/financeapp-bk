# Proposal

Origen: FA-59. El encargo llegó por chat el 04-10-2026: un endpoint de catálogos para saber qué
valores aceptan `type` y `currencyCode` de una cuenta, `type` de un movimiento y `categoryId`, con su
propio controlador y su carpeta en Bruno, sin mezclarlo con los flujos de cada recurso. En el mismo
chat se sumó a la tarea cerrar un hueco del flujo: la réplica en `bruno-personal/` dependía de que
alguien recordara un párrafo de `AGENTS.md`.

## Why

Un cliente que arma el formulario de una cuenta o de un movimiento hoy tiene que copiar a mano los
valores válidos de `docs/api/contrato-api.md`, y se entera de que una moneda está inactiva solo al
recibir el 400. Los catálogos le dan esos valores desde el API, con una etiqueta en español para
mostrar.

La regla de replicar en `bruno-personal/` cada request nuevo o cambiado de `bruno/` vive solo en
`AGENTS.md`. Como esa carpeta está en `.gitignore`, el PR no muestra si se hizo, y ni
`openspec/config.yaml` ni `tareas-notion` la piden.

## What Changes

- `GET /api/catalogs/account-types`: los valores de `AccountType` como `{code, description}`.
- `GET /api/catalogs/transaction-types`: los valores de `TransactionType` como `{code, description}`.
- `GET /api/catalogs/currencies`: las monedas activas de `finance.currencies` como
  `{code, name, symbol}`, ordenadas por código.
- `GET /api/catalogs/categories`: las categorías vivas del usuario del token como `{id, name}`, con
  el filtro opcional `appliesTo` y el mismo 400 que `GET /api/categories`.
- Los cuatro van en la cadena JWT: sin credencial o con el Basic compartido dan 401.
- Un controlador nuevo, `CatalogController`. `AccountController`, `TransactionController` y
  `CategoryController` no cambian, ni sus casos de uso.
- `docs/database/test-data.sql` agrega una moneda inactiva (`XTS`, el código ISO 4217 reservado para
  pruebas) para que Bruno pueda comprobar que no aparece.
- Carpeta `bruno/catalogs/` y su réplica en `bruno-personal/catalogos/`.
- `openspec/config.yaml` pide en `tasks.md` la réplica en `bruno-personal/` de todo change que toque
  `bruno/`; el paso de entrega de `tareas-notion` la confirma antes de `Por revisar`.

Descripciones propuestas:

| Catálogo | Código | Descripción |
|---|---|---|
| account-types | `CASH` | Efectivo |
| account-types | `DEBIT` | Cuenta débito |
| account-types | `CREDIT` | Tarjeta de crédito |
| account-types | `SAVINGS` | Cuenta de ahorros |
| account-types | `INVESTMENT` | Inversión |
| account-types | `OTHER` | Otra |
| transaction-types | `EXPENSE` | Gasto |
| transaction-types | `INCOME` | Ingreso |
| transaction-types | `TRANSFER` | Transferencia |

## Capabilities

### New Capabilities
- `catalogos`: los valores válidos que el API expone para armar formularios (tipos de cuenta, tipos
  de movimiento, monedas y categorías del usuario).

### Modified Capabilities

Ninguna. `GET /api/categories` no cambia.

## Fuera de alcance

- Que el catálogo de monedas refleje que el alta de movimientos solo acepta COP: eso es FA-51.
- `decimalPlaces`, `icon`, `color` o `appliesTo` en las respuestas del catálogo: la tarea pide los
  campos para elegir un valor, no para pintarlo. Si el frontend los necesita, es una tarea nueva.
- Caché o cabeceras de caché HTTP en los catálogos.
- Comprobar de forma automática que `bruno-personal/` quedó replicado: está fuera de git y apunta a
  Neon, así que nada lo corre. La regla nueva lo vuelve una casilla de `tasks.md`.

## Impact

- Código nuevo: `CatalogController`, sus DTOs, `ListCurrenciesPort` con su caso de uso, el modelo
  `Currency` y un método nuevo en `CurrencyQueryPort` y `CurrencyR2dbcAdapter`.
- API: cuatro rutas nuevas bajo `/api/catalogs`; ninguna existente cambia.
- Datos de prueba: `test-data.sql` inserta o desactiva `XTS`. Neon no se toca.
- Documentación: `docs/api/contrato-api.md`.
- Flujo: `openspec/config.yaml` y `.claude/skills/tareas-notion/SKILL.md`.
