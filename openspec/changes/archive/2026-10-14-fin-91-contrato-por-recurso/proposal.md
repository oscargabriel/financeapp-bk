# Proposal

Origen: FIN-91, del epic FIN-74 (Calidad y mantenimiento). Desbloquea FIN-94, que parte igual la
copia del contrato en `financeapp-fr`.

## Why

`docs/api/contrato-api.md` mide 137k caracteres (unos 40k tokens) en un solo archivo. Las sesiones
lo abren buscando la sección del recurso que tocan y, aunque lo lean por rangos, primero tienen que
ubicarse en un documento de 2.600 líneas. Partido en un archivo por recurso, quien toca cuentas lee
`cuentas.md` (13k) y nada más.

Aplica la decisión del epic FIN-74 «El contrato del API va en un archivo por recurso, con
`contrato-api.md` como índice. El front mantiene su copia partida igual y la trae con un script
desde el repo vecino» (14-10-2026).

## What Changes

- `docs/api/` pasa a tener un archivo por recurso con sus endpoints, nombrado como la capacidad de
  `openspec/specs/` que lo describe: `auth.md`, `usuarios.md`, `cuentas.md`, `categorias.md`,
  `catalogos.md`, `tasas-de-cambio.md`, `transacciones.md`, `compartidos.md`, `recurrentes.md`,
  `cuotas.md`, `reportes.md`, `asistente.md` y `afiliaciones.md`.
- Lo común a todos (generalidades, autenticación, errores, CORS y lo que el API todavía no tiene)
  va a `generalidades.md`.
- `contrato-api.md` queda como índice: la tabla de endpoints de hoy, con el archivo de cada uno
  enlazado, y la lista de archivos. No repite el contenido de ninguno.
- Cada encabezado `###` del contrato de hoy aparece en exactamente un archivo, con el mismo texto.
  El contenido se mueve sin reescribirse; lo único que cambia dentro de las secciones son los
  enlaces que ahora cruzan de archivo (`#ancla` → `archivo.md#ancla`).
- Ningún comportamiento del API cambia: `skip_specs: true`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: es documentación.

## Fuera de alcance

- **`AGENTS.md` y `openspec/config.yaml`.** El criterio pide que nombren el archivo del recurso
  donde hoy remiten a una sección de `contrato-api.md`. En `dev`, ninguno de los dos menciona el
  contrato. La única mención es la línea que la raíz agregó a `openspec/config.yaml` («Las specs y
  docs/api/contrato-api.md se leen por sección») y que está sin commit en el working tree. Esos dos
  archivos los mantiene la raíz (decisión del epic del 14-10-2026), y el cambio del usuario no se
  toca: la raíz ajusta esa línea junto con los skills cuando esta tarea cierre.
- Los skills que nombran `contrato-api.md` (`tareas-jira` de los dos repos, `crear-tareas`): los
  ajusta la raíz, como dice el issue.
- La copia del front y su script de sincronización: FIN-94.
- Corregir o actualizar el texto del contrato. Si al mover aparece algo desactualizado, va a una
  tarea nueva.
- Los changes archivados que citan secciones de `contrato-api.md`: son historia y no se reescriben.

## Impact

- `docs/api/`: un archivo nuevo por recurso, `generalidades.md`, y `contrato-api.md` reducido a
  índice.
- `bruno-personal/opencollection.yml` (sin versionar) remite a `docs/api/contrato-api.md`: sigue
  siendo válido, ahora como índice.
- Ningún archivo de código, test, configuración ni colección Bruno.
