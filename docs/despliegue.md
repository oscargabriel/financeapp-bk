# Despliegue

Cloud Run (`financeapp-bk-git`, región `europe-west1`) contra Neon. Los dos en plan gratuito y con
escala a 0: hay un solo usuario.

**Estado al 05-10-2026:** el servicio corre la app. El primer despliegue fue el del merge de `dev`
a `main` (PR #29, commit `b163a65`, revisión `financeapp-bk-git-00006-dqs`). Neon tiene el esquema
de `schema.sql` con todos los `update/` hasta `20261005_02` (ver el registro).

## Base de datos (Neon)

Sin Flyway (decisión y porqué en `docs/database/modelo-datos.md`; su reevaluación es FA-71), la base
de producción se mantiene a mano con psql. Esta sección es el procedimiento.

### El proyecto

| | |
|---|---|
| Plan | Gratuito |
| Región | AWS `us-east-1`. Cloud Run está en `europe-west1`: cada consulta cruza el Atlántico (FA-70) |
| PostgreSQL | 18 (18.6 al 05-10-2026) |
| Base | `neondb`, esquema `finance` |
| Rol | `neondb_owner`, el dueño del proyecto. La app usa el mismo rol (FA-69) |
| Host | El **directo**, sin `-pooler` (FA-44). Es el secreto `db-host` |

### Conectarse

Los valores salen de Secret Manager, los mismos que lee Cloud Run, así que la contraseña no se
escribe ni se imprime:

```powershell
$env:PGHOST     = gcloud secrets versions access latest --secret=db-host
$env:PGDATABASE = gcloud secrets versions access latest --secret=db-name
$env:PGUSER     = gcloud secrets versions access latest --secret=db-username
$env:PGPASSWORD = gcloud secrets versions access latest --secret=db-password
$env:PGSSLMODE  = 'require'
$env:PGOPTIONS  = '-c default_transaction_read_only=on'   # solo lectura: quítalo solo para aplicar un update
```

Con `PGOPTIONS` así, la sesión rechaza cualquier escritura, aunque se pegue un `UPDATE` por error.
Para inspeccionar, se deja puesto. Al terminar, `Remove-Item env:PGPASSWORD`.

### Crear la base desde cero

Solo si se recrea el proyecto. En la consola de Neon, **New project**, con PostgreSQL 18. De
**Connect**, con *Connection pooling* apagado, se toman host, base, rol y contraseña. Después:

```powershell
psql -X -v ON_ERROR_STOP=1 -f docs/database/schema.sql
psql -X -v ON_ERROR_STOP=1 -f docs/database/seed.sql
```

- **`seed.sql` es obligatorio.** Trae las monedas y las 22 `default_categories`, y sin ellas el
  registro de usuario falla, porque las copia dentro de su transacción.
- **`test-data.sql` no se carga nunca.** Es el escenario de Bruno, con usuarios y movimientos falsos.
- El `schema.sql` del día ya incluye todos los `update/` emitidos hasta entonces: **no se
  aplican**. El registro de abajo se reinicia, con el commit del `schema.sql` cargado.
- Después, actualizar los secretos `db-*` que cambiaron y forzar una revisión (ver *Rotar un
  secreto*).

Para comprobar que no hay datos de `test-data.sql`, en solo lectura:

```powershell
psql -X -c "SELECT count(*) AS de_prueba FROM finance.users WHERE email LIKE '%@financeapp.local' OR email LIKE '%@bruno.local'"
```

Tiene que dar `0`.

### Comparar Neon con el esquema esperado

Construye en un `postgres:18` desechable la línea base más los `update/` hasta el último aplicado,
y compara su estructura con la de Neon. Con `$hasta` en el último update del registro, el
resultado es el `schema.sql` que Neon debería tener. Necesita Docker y la conexión de arriba.

```powershell
$hasta = '20261005_02'   # AAAAMMDD_NN del último update aplicado en Neon, según el registro
$ruido = 'restrict |Dumped from database version'   # cambia en cada volcado o según el build del servidor
function Volcar($db) {
    pg_dump --schema-only --no-owner --no-privileges -n finance $db |
        Where-Object { $_ -notmatch $ruido -and $_.Trim() -ne '' }
}
$neon = Volcar $env:PGDATABASE

$guardadas = 'PGHOST','PGPORT','PGDATABASE','PGUSER','PGPASSWORD','PGSSLMODE','PGOPTIONS' |
    ForEach-Object { [pscustomobject]@{ n = $_; v = [Environment]::GetEnvironmentVariable($_) } }
$clave = [guid]::NewGuid().ToString('N')
$cid = docker run --rm -d -e POSTGRES_PASSWORD=$clave -p 127.0.0.1:55432:5432 postgres:18
$env:PGHOST = '127.0.0.1'; $env:PGPORT = '55432'; $env:PGUSER = 'postgres'; $env:PGPASSWORD = $clave
Remove-Item env:PGSSLMODE, env:PGOPTIONS, env:PGDATABASE -ErrorAction SilentlyContinue
do { Start-Sleep 1; psql -X -q -d postgres -c 'SELECT 1' *> $null } until ($LASTEXITCODE -eq 0)

$tmp = New-Item -ItemType Directory -Path (Join-Path $env:TEMP "neon-$(Get-Random)")
git show 00c3b63:docs/database/schema.sql | Set-Content "$tmp\b.sql"
git show 00c3b63:docs/database/seed.sql   | Set-Content "$tmp\s.sql"
psql -X -q -d postgres -c 'CREATE DATABASE esperado'
psql -X -q -v ON_ERROR_STOP=1 -d esperado -f "$tmp\b.sql"
psql -X -q -v ON_ERROR_STOP=1 -d esperado -f "$tmp\s.sql"
Get-ChildItem docs/database/update/*.sql |
    Where-Object { $_.Name -notlike '*baseline*' -and $_.Name.Substring(0, 11) -le $hasta } |
    Sort-Object Name | ForEach-Object { psql -X -q -v ON_ERROR_STOP=1 -d esperado -f $_.FullName }
$esperado = Volcar esperado

docker stop $cid | Out-Null
$guardadas | ForEach-Object { [Environment]::SetEnvironmentVariable($_.n, $_.v) }

"Neon: $($neon.Count) líneas · esperado: $($esperado.Count) líneas"
$dif = Compare-Object $esperado $neon
if ($dif) { 'HAY DIFERENCIAS (=> solo en Neon)'; $dif } else { 'sin diferencias' }
```

La línea base es `00c3b63`, igual que en la comprobación de `modelo-datos.md`. Si sale una
diferencia, hay un update aplicado que no está en el registro, o uno del registro que no se aplicó.

### Aplicar un `update/` nuevo

1. **Elegir el momento.** El encabezado del script dice si va *antes* o *después* del despliegue de
   su app:
   - *Antes*: se aplica antes del merge a `main`.
   - *Después*: se aplica cuando el pipeline haya desplegado el commit.
     `gcloud run revisions list --service financeapp-bk-git --region europe-west1` muestra la
     etiqueta `commit-sha` de cada revisión.
2. **Snapshot.** En la consola de Neon, en el branch de producción, **Backup & Restore → Create
   snapshot**. Reemplaza al anterior (ver *Respaldo*).
3. **Conectarse** como arriba y quitar la solo lectura: `Remove-Item env:PGOPTIONS`.
4. **Aplicar:**

   ```powershell
   psql -X -v ON_ERROR_STOP=1 -f docs/database/update/<archivo>.sql
   ```

   Cada script trae su `BEGIN`/`COMMIT`. `ON_ERROR_STOP` corta en el primer error, dentro de la
   transacción, y la base queda como estaba. La salida tiene que terminar en `COMMIT`.
5. **Comparar**, con la solo lectura de vuelta y `$hasta` en el update recién aplicado:
   `sin diferencias`.
6. **Anotarlo** en el registro, en el mismo PR que lo emitió si ya estaba aplicado, o en uno
   posterior.

Para deshacer un update, cada script trae al final su reversión comentada. Si eso no alcanza, se
restaura el snapshot del paso 2.

### Registro de updates aplicados

Es la única tabla de control que existe. La comparación de arriba es la que comprueba que dice la
verdad.

| Update | Aplicado en Neon | Notas |
|---|---|---|
| Línea base (`schema.sql` y `seed.sql` de `00c3b63`) | Antes del primer despliegue (05-10-2026) | Comparada el 05-10-2026: sin diferencias. Sin datos de `test-data.sql` |
| `20261005_02_cuentas_saldo_inicial_editable.sql` | 05-10-2026 (FA-49) | Antes de promover FA-24, como pide su encabezado |
| `20261005_01_categorias_icono_color_obligatorios.sql` | 05-10-2026 (FA-49) | Antes de FA-66 en `main`, aunque su encabezado dice "después": el riesgo que nombra es un alta de categorías sin icono, y la app de `main` no da de alta categorías. No cambió filas |

### Respaldo

Lo que da el plan gratuito, según la documentación de Neon consultada el 05-10-2026
([precios](https://neon.com/pricing), [ventana de historia](https://neon.com/docs/postgres/backup-restore/history-window),
[backup y restore](https://neon.com/docs/guides/backup-restore)):

- **Ventana de historia de 6 horas**, con tope de 1 GB de WAL. Dentro de ella, el branch se
  restaura a cualquier instante (*instant restore*). Cubre **0,25 días**: un error que se note al
  día siguiente ya no se recupera así.
- **1 snapshot manual.** Sin snapshots programados. Uno nuevo reemplaza al anterior.

Por eso el paso 2 de *Aplicar un update*: el snapshot es lo único que cubre más de 6 horas, y solo
hasta el último que se tomó. Si algún día hay que sacar los datos de Neon, el camino es `pg_dump`
con la conexión de arriba, sabiendo que el archivo contiene los datos financieros reales.

## Pipeline

`deployment/cloudbuild.yaml` lo ejecuta un disparador de Cloud Build en cada push a `main`. Tres
pasos, y si uno falla los siguientes no corren:

1. **imagen**: `docker build -f deployment/Dockerfile`. La etapa de compilación corre
   `./gradlew build`: un test rojo o la cobertura bajo el 85 % terminan aquí.
2. **publicar**: sube la imagen a `europe-west1-docker.pkg.dev/<proyecto>/cloud-run-source-deploy/financeapp-bk-git`
   con dos etiquetas, el SHA corto del commit y `latest`.
3. **desplegar**: `gcloud run deploy` con la imagen del SHA. Solo cambia la imagen: variables,
   secretos, cuenta y escalado se quedan como están en el servicio. La revisión lleva la etiqueta
   `commit-sha`, así que `gcloud run revisions list --service financeapp-bk-git --region europe-west1`
   dice qué commit corre cada una.

Región, repositorio y servicio son sustituciones con default (`_REGION`, `_REPOSITORY`,
`_SERVICE`); el proyecto sale de `PROJECT_ID`. Cada build corre la suite completa dentro de Docker,
así que consume minutos de Cloud Build. Al ritmo de merges a `main` de este proyecto no debería
salirse de la cuota gratuita; si cambia, revisar el consumo en **Facturación → Informes**.

### Artifact Registry

Repositorio `cloud-run-source-deploy` en `europe-west1`, la misma región del servicio: la descarga
de la imagen hacia Cloud Run no sale de la región. Tiene una política de limpieza que **conserva
solo la versión más reciente** (FA-46): unos 227 MB comprimida (medido el 05-10-2026), dentro de los 0,5 GB gratuitos.

```powershell
gcloud artifacts repositories describe cloud-run-source-deploy --location europe-west1   # tamaño y política
```

La limpieza corre una vez al día, así que tras un despliegue la versión anterior puede durar unas
horas.

### Crear el disparador (una vez)

La cuenta del disparador es la misma del servicio, `financeapp@` (decisión de FA-48). Además de
`run.admin` e `iam.serviceAccountUser`, necesita publicar imágenes y escribir los logs del build:

```powershell
$p = gcloud config get project
$sa = "serviceAccount:financeapp@$p.iam.gserviceaccount.com"
gcloud projects add-iam-policy-binding $p --member=$sa --role=roles/artifactregistry.writer
gcloud projects add-iam-policy-binding $p --member=$sa --role=roles/logging.logWriter
```

En la consola, **Cloud Build → Activadores → Crear activador**:

| Campo | Valor |
|---|---|
| Región | `europe-west1` |
| Evento | Enviar a una rama |
| Repositorio | `oscargabriel/financeapp-bk` (la primera vez, *Conectar repositorio* con GitHub) |
| Rama | `^main$` |
| Configuración | Archivo de configuración de Cloud Build, ubicación *Repositorio*, `deployment/cloudbuild.yaml` |
| Cuenta de servicio | `financeapp@<proyecto>.iam.gserviceaccount.com` |

Comprobar que quedó: `gcloud builds triggers list --region europe-west1`.

### Desplegar

Lo normal es mergear a `main`. A mano, solo en una emergencia, porque despliega lo que haya en la
copia local y no lo que está en `main`:

```powershell
gcloud builds submit --config deployment/cloudbuild.yaml --region europe-west1 `
  --substitutions=SHORT_SHA=$(git rev-parse --short HEAD) `
  --service-account="projects/$p/serviceAccounts/financeapp@$p.iam.gserviceaccount.com"
```

### Revertir

Como solo se conserva una imagen, **revertir es revertir el commit en `main`** y dejar que el
pipeline reconstruya. Con escala a 0, cada arranque en frío vuelve a descargar la imagen de la
revisión, así que pasar el tráfico a una revisión cuya imagen ya se borró deja el servicio sin
poder arrancar.

Solo en las horas siguientes a un despliegue, mientras la limpieza no haya borrado la imagen
anterior, se puede devolver el tráfico a la revisión previa:

```powershell
gcloud run revisions list --service financeapp-bk-git --region europe-west1
gcloud run services update-traffic financeapp-bk-git --region europe-west1 --to-revisions=<revisión>=100
```

Eso fija el tráfico en esa revisión: los despliegues siguientes ya no lo reciben hasta volver con
`gcloud run services update-traffic financeapp-bk-git --region europe-west1 --to-latest`.

## Secretos y configuración del servicio

Los valores nunca se escriben aquí ni en ningún archivo del repo. El proyecto de GCP es el de
`gcloud config get project`.

### Secretos

Todos en Secret Manager, montados como variables de entorno con la versión `latest`.
`application.yaml` los lee **sin default**: si falta uno, la app no arranca (verificado en FA-48 con
la imagen de `deployment/Dockerfile`, ver abajo).

| Secreto | Variable | Propiedad de `application.yaml` | Qué es |
|---|---|---|---|
| `db-host` | `DB_HOST` | host de `spring.r2dbc.url` | Host **directo** de Neon, sin `-pooler` (FA-44) |
| `db-port` | `DB_PORT` | puerto de `spring.r2dbc.url` | `5432` |
| `db-name` | `DB_NAME` | base de `spring.r2dbc.url` | Base de Neon |
| `db-username` | `DB_USERNAME` | `spring.r2dbc.username` | Rol de Neon con el que se conecta la app |
| `db-password` | `DB_PASSWORD` | `spring.r2dbc.password` | Contraseña de ese rol |
| `jwt-secret` | `JWT_SECRET` | `spring.security.jwt.secret` | Clave HS256 de los tokens. 32 bytes o más, o `JwtConfig` aborta el arranque |
| `basic-username` | `BASIC_USERNAME` | `spring.security.basic.username` | Usuario de la credencial compartida de `/auth/register`, `/auth/login` y `/status` |
| `basic-password` | `BASIC_PASSWORD` | `spring.security.basic.password` | Su contraseña |

Si falta cualquiera de estas variables, la revisión no arranca y el log dice
`Could not resolve placeholder '<VARIABLE>'`. Con `DB_USERNAME` y `DB_PASSWORD` pasa lo mismo, aunque
`STARTUP_DB_CHECK_ENABLED` esté en `false` (FA-53).

Producción y local **no deben** compartir `JWT_SECRET` ni la credencial Basic: así un token emitido
en local no vale en producción, y quien tenga la Basic de desarrollo no puede registrarse en
producción.

### Variables en texto plano

| Variable | Valor | Por qué |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` | La imagen ya lo trae; el servicio lo repite |
| `CORS_ALLOWED_ORIGINS` | `*` | Provisional mientras no haya frontend (26-09-2026). Se reemplaza en FA-65 |

El resto de propiedades (`DB_SSL_MODE`, `JWT_EXPIRATION`, `APP_TIMEZONE`, `STARTUP_DB_CHECK_*`…)
usa el default de `application.yaml`.

### Rotar un secreto

Cloud Run resuelve `latest` al arrancar cada instancia, así que rotar es agregar una versión y
forzar una revisión nueva. El orden importa: la versión anterior se deshabilita **después** de
comprobar que la nueva funciona, para poder volver atrás.

```powershell
# 1. Versión nueva. El valor va a un archivo temporal sin salto de línea: un "\n" final
#    terminaría dentro del secreto (y en la contraseña o la clave).
$tmp = New-TemporaryFile
Set-Content -Path $tmp -Value '<valor nuevo>' -NoNewline
gcloud secrets versions add <secreto> --data-file=$tmp
Remove-Item $tmp

# 2. Revisión nueva para que las instancias tomen la versión nueva.
gcloud run services update financeapp-bk-git --region europe-west1 --update-labels=rotado=<AAAAMMDD>

# 3. Comprobar /api/status con la Basic: 200 y status UP.

# 4. Deshabilitar la versión anterior (se puede volver a habilitar; destroy no).
gcloud secrets versions list <secreto>
gcloud secrets versions disable <n> --secret=<secreto>
```

Con escala a 0, una instancia que arranque entre el paso 1 y el 2 ya toma la versión nueva: no
agregues una versión que todavía no quieras usar.

Lo propio de cada uno:

- **`jwt-secret`**: invalida todos los tokens emitidos; cada usuario vuelve a hacer login. Valor
  nuevo al azar de 32 bytes o más, por ejemplo `openssl rand -base64 48`. Nunca el de local.
- **`basic-username` / `basic-password`**: actualizar después el entorno `prod` de la app de Bruno
  (`basicUser` / `basicPass`) y cualquier cliente que la use. Sin la Basic nadie puede registrarse
  ni hacer login.
- **`db-password`**: se cambia **primero en Neon** (Roles → reset password), después se agrega la
  versión y se fuerza la revisión enseguida: entre los dos pasos la app no puede abrir conexiones
  nuevas. Actualizar también `application-prod.yaml` en local.
- **`db-host`, `db-port`, `db-name`, `db-username`**: solo cambian si se mueve la base de proyecto o
  de rol en Neon.

### Cuenta de servicio

El servicio corre con `financeapp@<proyecto>.iam.gserviceaccount.com`, con
`roles/secretmanager.secretAccessor`, `roles/run.admin` y `roles/iam.serviceAccountUser` sobre todo
el proyecto. Es una decisión (FA-48, 04-10-2026): una sola cuenta para todo, administrada por el
usuario.

El precio: si la app se compromete, quien la controle puede leer **cualquier** secreto del proyecto
y redesplegar servicios, no solo leer sus ocho credenciales. La alternativa descartada era una cuenta
de ejecución con `secretAccessor` solo sobre esos ocho secretos y otra de despliegue con los roles
de Run.

### Escalado

De 0 a 1 instancia, fijado tanto en el servicio como en la revisión. Cloud Run aplica el menor de
los dos, así que si alguna vez difieren, manda el más bajo. El pool R2DBC abre hasta 10 conexiones
por instancia: 1 × 10 frente a las 901 de `max_connections` de Neon. Si sube el máximo de
instancias, rehacer esa cuenta aquí, en `application.yaml` y en `AGENTS.md`.

Con mínimo 0, el primer request tras unos minutos sin uso paga el arranque en frío de la JVM y,
si Neon también se suspendió, el de la base.
