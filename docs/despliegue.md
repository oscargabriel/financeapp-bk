# Despliegue

Cloud Run (`financeapp-bk-git`, región `europe-west1`) contra Neon. Los dos en plan gratuito y con
escala a 0: hay un solo usuario.

**Estado al 04-10-2026:** el servicio existe con sus secretos y su escalado, pero corre
`gcr.io/cloudrun/placeholder`, no la app. El pipeline que construye y despliega la imagen es FA-46.
Hasta entonces el uso real es la app en local con `SPRING_PROFILES_ACTIVE=prod` contra Neon.

Pendiente de este documento (FA-49): creación del proyecto en Neon, carga del esquema y de los
`update/`, respaldo del plan, comandos de `gcloud` para desplegar y revertir a la imagen anterior.

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

`DB_USERNAME` y `DB_PASSWORD` no fallan al enlazar sino en `DatabaseStartupCheck`, cuando Neon
rechaza la autenticación: Boot deja pasar el placeholder sin resolver como texto literal.

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
