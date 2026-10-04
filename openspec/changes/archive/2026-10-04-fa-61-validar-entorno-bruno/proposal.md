# Proposal

Origen: FA-61. El encargo llegó por chat el 04-10-2026, al cerrar FA-60: validar, antes de correr
Bruno como parte de la verificación de un cambio, que la app esté detenida y que la configuración
apunte al ambiente de pruebas, porque el usuario puede tener la app corriendo contra producción u
otra base y el puerto ocupado.

## Why

En FA-60 había una app escuchando en el 8080 que no era la de la rama, y Bruno corrió contra ella.
Si esa app apunta a Neon, `bru run` crea usuarios, cuentas y movimientos en producción, y recargar
`test-data.sql` la reescribe. Hoy nada lo impide: `verificar` y el paso 6 de `tareas-notion` solo
dicen "con la app levantada", sin decir cuál ni contra qué base.

El archivo no basta como garantía. `application-local.yaml` arma la URL con `${DB_HOST:localhost}`,
`${DB_NAME:financeapp}`, etc., así que una variable `DB_*` o `SPRING_R2DBC_URL` heredada de la
terminal lleva la app a otra base con el perfil `local` intacto.

## What Changes

- Script versionado `.claude/scripts/verificar-bruno.ps1` que hace en orden: validar, levantar,
  correr Bruno y apagar.
  - **Puerto.** Toma el puerto de `host` en `bruno/environments/local.yml`. Si está ocupado, se
    detiene, nombra el proceso que lo tiene y no lo mata.
  - **Base.** Resuelve la URL R2DBC de `application-local.yaml` con las variables del entorno. Si el
    host no es `localhost`, `127.0.0.1` ni `::1`, o la base no es `financeapp`, se detiene antes de
    levantar nada.
  - **Recarga opcional** (`-RecargarDatos`): carga `test-data.sql` con psql contra `-h localhost`,
    explícito.
  - **Arranque.** Levanta la app con `SPRING_PROFILES_ACTIVE=local` y sin `DB_*` ni
    `SPRING_R2DBC_*` en su entorno, y espera el `Started`.
  - **Bruno.** Corre `bru run . -r --env local`, con las variables `nombre=valor` que reciba como `--env-var`.
  - **Apagado.** Detiene siempre la app que levantó, aunque Bruno falle, y comprueba que el puerto
    quede libre. Termina con el código de salida de `bru`.
- `verificar` (sección 2), el paso 6 de `tareas-notion` y la sección *Comandos* de `AGENTS.md` pasan
  a usar el script en lugar de `bru run` contra una app levantada a mano.

## Capabilities

### New Capabilities

Ninguna (`skip_specs: true`): es un cambio de herramientas, no de comportamiento de la app.

### Modified Capabilities

Ninguna.

## Fuera de alcance

- Que `bru` no esté en el PATH de las sesiones: es FA-39. El script falla con un mensaje claro si no
  lo encuentra.
- Que falte `bruno/.env`: hoy da 401 en cascada. Si molesta, va como tarea aparte.
- Validar la corrida contra `bruno-personal/` o contra prod: esa colección no se corre con `bru run`.
- Matar procesos ajenos o elegir otro puerto automáticamente.

## Impact

- Nuevo `.claude/scripts/verificar-bruno.ps1`.
- `.claude/skills/verificar/SKILL.md`, `.claude/skills/tareas-notion/SKILL.md` y `AGENTS.md`.
- Sin cambios en `src/`, `bruno/` ni la base. El script **no** se agrega a `permissions.allow`:
  corre `bru run`, y la skill `bruno-cli` deja ese comando sin autorizar a propósito.
