# Proposal

Origen: FA-49, etapa de despliegue. La tarea pedía cargar el esquema en Neon y escribir el
procedimiento. Cuando se tomó (05-10-2026), la app ya corría en Cloud Run contra Neon desde el
primer despliegue (PR #29), así que la base ya estaba cargada. El trabajo pasa de "cargarla" a
comprobar qué tiene, ponerla al día y escribir cómo se mantiene.

## Why

No hay Flyway: en producción, cada cambio de esquema es un `psql` que alguien tiene que recordar
correr, en orden y en el momento correcto respecto del despliegue. Hoy no hay nada escrito sobre
Neon: ni cómo se creó el proyecto, ni cómo se aplica el siguiente `update/`, ni qué respaldo hay si
uno sale mal. Ya hay dos `update/` esperando (FA-66 y FA-24), y FA-24 exige aplicarse antes de su
despliegue.

Lo que se encontró en Neon el 05-10-2026, con una sesión `default_transaction_read_only=on`:

- PostgreSQL 18.6 en AWS `us-east-1`, base `neondb`, rol `neondb_owner`, host directo (sin
  `-pooler`).
- El esquema `finance` es idéntico al `schema.sql` de la línea base (`00c3b63`), que es lo que hay
  en `main`. Son 634 líneas por lado en `pg_dump --schema-only`, y la única diferencia es la línea
  `-- Dumped from database version`, que trae el build del servidor.
- `seed.sql` está cargado: 9 monedas y 22 `default_categories`.
- `test-data.sql` no está: no hay ningún usuario `@financeapp.local` ni `@bruno.local`, ni cuentas
  con los ids fijos del escenario. Hay 1 usuario real, con 2 cuentas y 9 movimientos.
- Faltan `20261005_01_categorias_icono_color_obligatorios.sql` (FA-66) y
  `20261005_02_cuentas_saldo_inicial_editable.sql` (FA-24): `icon` y `color` siguen admitiendo
  nulos, y `trg_accounts_shift_balance` no existe. Ninguno de los dos toca filas en Neon: ninguna
  categoría tiene `icon` ni `color` nulo, y ninguna cuenta tiene un saldo inicial distinto de 0.
- En un `postgres:18` desechable, la base construida desde el `schema.sql` de hoy y la construida
  desde la línea base más los `update/` coinciden (649 líneas). Aplicar los dos updates en Neon la
  deja, entonces, igual a `schema.sql`.
- El plan es el gratuito. Según la documentación de Neon consultada el 05-10-2026, la ventana de
  historia para restaurar es de **6 horas**, con un tope de 1 GB de WAL, más **1 snapshot manual**
  y ningún snapshot programado.

## What Changes

- `docs/despliegue.md`, sección nueva *Base de datos (Neon)*:
  - Cómo es el proyecto (región, versión, base, rol, host directo) y cómo se crea uno igual desde
    cero, con la carga de `schema.sql` y `seed.sql`. Dice explícitamente que `test-data.sql` no se
    carga, y da la consulta que lo comprueba.
  - Cómo conectarse sin escribir la contraseña: variables `PG*` desde `application-prod.yaml`, y la
    sesión de solo lectura para inspeccionar.
  - La comparación de esquemas contra Neon, que necesita filtrar además la línea
    `Dumped from database version`.
  - Cómo se aplica un `update/` nuevo a producción:
    1. Leer el orden respecto del despliegue en el encabezado del script.
    2. Snapshot manual.
    3. `psql` con `ON_ERROR_STOP` en una transacción.
    4. Comparación contra `schema.sql`.
    5. Anotarlo en el registro.
  - El registro de los `update/` aplicados en Neon, con su fecha: es la única "tabla de control"
    que hay.
  - El respaldo del plan gratuito y lo que eso significa: más allá de 6 horas, solo queda el último
    snapshot manual.
  - Se quita la línea "Pendiente de este documento (FA-49)".
- **Neon se pone al día** (decisión del usuario, 05-10-2026):
  1. Snapshot manual, que hace el usuario desde la consola.
  2. Se aplica `20261005_02` y después `20261005_01`.
  3. Se compara Neon con el `schema.sql` de hoy, y tiene que salir sin diferencias.

  Así, la promoción de `dev` a `main` deja de depender del orden de los scripts.
- `docs/database/modelo-datos.md`:
  - El porqué de no tener Flyway todavía dice "No hay despliegue". Se corrige.
  - Su condición para revisar la decisión ("cuando exista el primer despliegue real") ya se
    cumplió. Se anota y se enlaza la tarea nueva que la reevalúa (ver *Fuera de alcance*).
- `AGENTS.md`, sección *Base de datos*: una línea que remite al procedimiento de Neon en
  `docs/despliegue.md`.

## Criterios de FA-49

| Criterio | Cómo queda |
|---|---|
| `schema.sql` y `seed.sql` cargados en Neon, verificados con la comparación | YA EXISTÍA la carga; la comparación se hizo y se repite tras los updates |
| `test-data.sql` explícitamente no cargado | Comprobado con consulta; escrito en `despliegue.md` |
| `despliegue.md` con proyecto en Neon, carga, `gcloud`, variables y secretos, desplegar, revertir | `gcloud`, secretos, desplegar y revertir YA EXISTÍAN (FA-46, FA-48); se agrega Neon y la carga |
| Cómo se aplica un `update/` nuevo a producción | Sección nueva en `despliegue.md` |
| Qué respaldo da el plan y cuántos días cubre | Sección nueva: 6 horas (0,25 días), 1 GB de WAL, 1 snapshot manual |
| `AGENTS.md` sin el roadmap que declaraba el despliegue fuera de alcance | YA EXISTÍA: `AGENTS.md` no tiene ese texto y ya tiene la sección *Despliegue* |

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es documentación y mantenimiento de la base de producción, no
comportamiento de la app.

### Modified Capabilities

Ninguna.

## Fuera de alcance

Todo esto va a tareas nuevas del Backlog:

- **La app se conecta a Neon como `neondb_owner`**, el rol dueño, con `CREATEROLE` y `CREATEDB`, y
  dueño de todas las tablas. Un rol de aplicación con solo DML sobre `finance` limitaría el daño de
  una app comprometida.
- **Neon está en `us-east-1` y Cloud Run en `europe-west1`.** Cada consulta cruza el Atlántico.
  Arreglarlo es mover una de las dos cosas: la región de un proyecto de Neon no se cambia, así que
  mover la base exige un proyecto nuevo y migrar los datos.
- **Reevaluar Flyway**, ahora que se cumplió la condición que `modelo-datos.md` fijó para hacerlo.
- **Rotar la contraseña de `neondb_owner`.** La pegaste en el chat de esta sesión, así que quedó en
  el historial local de la conversación. No es una tarea del repo, sino una acción tuya, con el
  procedimiento de *Rotar un secreto* (`db-password`). Se recuerda al cerrar.

## Impact

- `docs/despliegue.md`, `docs/database/modelo-datos.md` y `AGENTS.md`. Sin cambios de código ni de
  `bruno/`.
- Base de producción (Neon): dos `update/` aplicados. No cambian datos. Agregan dos `NOT NULL` y
  un trigger.
