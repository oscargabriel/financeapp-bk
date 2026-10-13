## Why

FIN-43. Pedido desde la raíz de coordinación el 12-10-2026: «la siguiente tarea que quiero es para
desplegar en main los cambios que se tienen de ambas, back y front». Esta es la parte del back; la
del front es FIN-44, que depende de esta.

`origin/dev` va 20 commits por delante de `origin/main` (`4894df2`, PR #64, desplegado el
11-10-2026 en FA-128): FA-133, FA-134, FA-115, FA-123, FA-132, FA-116, FA-121, FA-148, FA-149 y el
registro de FA-128. Nada de eso está en producción. Además, la copia local de `dev` tiene sin
commitear la migración del tablero de Notion a Jira, y el usuario decidió que entra en esta
promoción.

Como en FA-128, el merge a `main` y los cambios en Neon y GCP los hace el usuario. La sesión prepara
los comandos, abre los PR, verifica lo que se puede verificar sin credenciales de producción y deja
el registro escrito.

## What Changes

### 1. La migración a Jira entra en `dev`

PR a `dev` con lo que hoy está sin commitear: `.claude/skills/tareas-jira/` nueva,
`.claude/skills/tareas-notion/` borrada, `AGENTS.md`, `.claude/rules/claude-code.md`,
`.claude/settings.json`, `openspec/config.yaml`, `.mcp.json`, `.gitattributes` y el `.gitkeep`
borrado de `openspec/changes/archive/`.

Los no versionados que no son de la migración, según decidió el usuario el 12-10-2026:

| Archivo | Decisión | Por qué |
|---|---|---|
| `docs/superpowers/specs/` | Se versiona | `.claude/rules/claude-code.md` cita `2026-09-11-notion-tareas-design.md`: hoy apunta a un archivo fuera de git |
| `docs/database/drop.sql`, `docs/database/reset-data.sql` | `.gitignore` | Son del 05-09-2026 y no coinciden con `schema.sql`: a `drop.sql` le faltan 9 objetos y a `reset-data.sql` 3 tablas. No se versiona un script que contradice el esquema |
| `bruno/environments/local-oz.yml` | `.gitignore` | Entorno personal (usuario `demo@`); sin secretos, pero no es de la colección |

### 2. Verificación sobre el commit que se promueve

`gradlew build` y `verificar-bruno.ps1 -RecargarDatos` en verde sobre el `dev` que resulta del
paso 1, que es lo que va a `main`.

### 3. Neon recreado con la demo, antes del merge (usuario, con el script que da la sesión)

**Cambio de plan del 12-10-2026, decisión del usuario:** en vez de aplicar los dos updates
pendientes, Neon se borra y se recrea con el `schema.sql` y el `seed.sql` de `dev`, y se carga la
demo del front para mostrarle la app a unos amigos. Esto contradice la regla de `AGENTS.md`
«Producción es Neon, y ahí no se carga ningún dato de prueba»: la regla se reescribe (ver
*Repositorio*). El usuario decidió también:

- **Su cuenta real en Neon se pierde.** La había conservado en FA-128. Si la necesita, se registra
  de nuevo con su correo en la lista de admitidos.
- **Solo `demo@financeapp.local`**, no `dev@`, y nada de `test-data.sql`.
- **La contraseña de la demo queda en `claveDePrueba123`**, aunque el repo es público y la Basic la
  ve quien abra el front: cualquiera con las dos entra a la demo y puede usar el asistente, que
  gasta cuota de Gemini. Riesgo aceptado por el usuario: los datos son de mentira.
- **`@front.local` queda en `registration_allowlist`**, como lo deja `demo-data.sql`: quien tenga
  la Basic puede registrarse en producción con un correo `@front.local` inventado. Riesgo aceptado.

Los dos updates (`20261011_01`, FA-134, y `20261011_02`, FA-132) no se aplican: el `schema.sql` de
`dev` (`970844c`) ya los incluye, igual que en FA-128. Ese `schema.sql` funciona con la app de
`4894df2` hasta el merge, porque los encabezados de los dos updates lo garantizan.

**Al ensayarlo en una base local vacía, `demo-data.sql` falla** con `No hay tasa de cambio
USD→COP`: el trigger `set_usd_equivalent` exige una tasa, y en local solo funciona porque
`cargar-datos-local.sql` corre antes `test-data.sql`, que las inserta. Su encabezado dice que
«corrido solo carga la demo del front», y no es cierto en una base nueva. En Neon se resuelve
insertando antes una tasa USD→COP real, tomada de ExchangeRate-API, la misma fuente que usa la app.
`usd_rate` usa la primera tasa posterior cuando no hay una anterior, así que una sola alcanza para
los seis meses. Corregir el script queda para un issue nuevo (ver *Fuera de alcance*).

La secuencia, en un script ensayado contra una base local desechable antes de correrlo en Neon:

1. Snapshot en la consola de Neon.
2. `DROP SCHEMA finance CASCADE`. No se usa `drop.sql`: le faltan 9 objetos.
3. `schema.sql` y `seed.sql`, con `ON_ERROR_STOP`.
4. Una tasa USD→COP del día, de ExchangeRate-API.
5. `demo-data.sql` con sus valores por defecto (`demo@`, `u = 0`).
6. Comprobación: el usuario, sus cuentas y movimientos, la lista de admitidos, las monedas y las
   categorías semilla, `v_monthly_spending` con `total_income`, y `check-saldos.sql`.
7. Comparación de esquemas de `docs/despliegue.md` con `$hasta = '20261011_02'`: `sin diferencias`.
   Compara solo estructura, así que los datos de la demo no la afectan.

### 4. Merge y despliegue

La sesión abre el PR `dev` → `main` con la lista de tareas que entran y la confirmación de que Neon
ya está recreado con el esquema de `dev`. **No lo mergea**: `merge-pr-dev.ps1` rechaza los PR contra
`main` y el merge es del usuario. Después, Cloud Build en verde y la revisión activa de
`financeapp-bk-git` con la etiqueta `commit-sha` del merge.

`application.yaml` solo agrega `TASAS_REINTENTO_TRAS_FALLO`, con default `1h`, y es la única
propiedad `@Value` nueva entre `main` y `dev`: el servicio no necesita variables nuevas. Se
despliega con las mismas credenciales; la rotación de la Basic es FIN-14.

### 5. Pruebas contra producción (usuario, con los comandos que da la sesión)

La sesión no lee secretos de producción: los comandos toman la Basic de Secret Manager en la
terminal del usuario y guardan el token en una variable, sin imprimirlos.

- `GET /api/status` con la Basic: 200 y `UP`.
- Login con `demo@financeapp.local` y `GET /api/monthly-spending`: el mes en curso trae
  `totalIncome` distinto de 0, porque la demo tiene ingresos este mes (FA-132).

### 6. Repositorio

Va a `dev` **al final**, en un PR con este change archivado, porque lo que registra no existe hasta
entonces. Llega a `main` en la siguiente promoción, como el de FA-128.

- `AGENTS.md`: la regla «Producción es Neon, y ahí no se carga ningún dato de prueba» pasa a decir
  que en Neon vive la demo de `demo@`, cargada con una tasa previa (FIN-43), y que `test-data.sql`
  y `cargar-datos-local.sql` siguen sin cargarse ahí. También la frase «En Neon no aplica: ahí no
  hay datos de prueba» del reinicio de datos.
- `docs/database/demo-data.sql`: su encabezado dice «Solo para la base local; en Neon no se carga».
  Pasa a remitir a `docs/despliegue.md` para Neon, sin tocar el SQL (eso es FIN-45).
- `docs/despliegue.md`:
  - «Estado al 12-10-2026» con el PR, el commit y la revisión;
  - el registro de updates reiniciado: `schema.sql` de `970844c` y `seed.sql` de `00c3b63`,
    cargados el 12-10-2026 sobre el esquema borrado, más la tasa y la demo; `20261011_01` y
    `20261011_02` incluidos, no aplicados;
  - `$hasta = '20261011_02'` en el script de comparación;
  - *Crear la base desde cero*: el `DROP SCHEMA` y la carga de la demo con su tasa previa; la
    consulta de usuarios de prueba excluye a `demo@`;
  - una nota con los dos riesgos que aceptó el usuario: la contraseña pública de `demo@` y
    `@front.local` en la lista de admitidos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna. Es despliegue, base de producción, configuración del repo y documentación. El change lleva
`skip_specs: true`.

## Cobertura de los criterios

| Criterio de FIN-43 | Cómo se cumple |
|---|---|
| 1. Migración a Jira en `dev` por PR; los demás no versionados, versionados o ignorados | Paso 1, con las decisiones de la tabla |
| 2. `gradlew build` y `bru run` en verde sobre el `dev` que se promueve | Paso 2 |
| 3. Snapshot de Neon antes de los updates | **No cumplido**: el usuario borró el esquema sin snapshot y lo aceptó el 12-10-2026, porque la app todavía no se libera y la base se puede reiniciar |
| 4. `20261011_01` y `20261011_02` aplicados antes del merge, terminando en `COMMIT` | **Sustituido** por decisión del usuario: Neon recreado con el `schema.sql` de `dev`, que ya los incluye. La comparación del criterio 5 lo comprueba |
| 5. Comparación con `$hasta = '20261011_02'` en `sin diferencias` | Paso 3, el usuario con el script de `docs/despliegue.md` |
| 6. PR `dev` → `main` mergeado por el usuario y Cloud Build en verde | Paso 4 |
| 7. Revisión activa con el `commit-sha` del merge | Paso 4, `gcloud run revisions list` |
| 8. `/api/status` 200 `UP` y `GET /api/monthly-spending` con los ingresos | Paso 5, con `demo@` |
| 9. `docs/despliegue.md` al día | Paso 6 |

## Impact

- Producción: Neon se borra y se recrea antes del merge. Se pierden los datos que había, entre ellos
  la cuenta del usuario, a sabiendas. Queda la demo de `demo@` con contraseña pública y
  `@front.local` admitido. El servicio pasa de `4894df2` al merge de `dev`.
- Repo: `.gitignore`, `docs/superpowers/` y la migración a Jira (PR #75); al final, `AGENTS.md`,
  el encabezado de `demo-data.sql` y `docs/despliegue.md`. Sin código, ni esquema, ni Bruno.
- FIN-44 (front) queda desbloqueada cuando esta pase a `Por revisar`.

## Fuera de alcance

- Que `demo-data.sql` cargue solo en una base nueva: FIN-45.
- Poner al día `drop.sql` y `reset-data.sql`: siguen siendo herramientas locales sin versionar.
- Cambiar la contraseña de `demo@` o quitar `@front.local` en Neon: el usuario decidió dejarlos.
- Rotar la Basic: FIN-14.
- Mover Neon de región (FA-70) o separar el rol de la app (FA-69).
