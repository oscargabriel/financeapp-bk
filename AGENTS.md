# AGENTS.md

Guía del proyecto para cualquier agente de código y para quien lo lea. Lo propio de una herramienta
va en su carpeta (`.claude/rules/` para Claude Code); aquí solo lo que vale para todas.

Backend de finanzas personales. Java 25 + Spring Boot 4.1.1 (WebFlux), R2DBC contra PostgreSQL 18,
Gradle 9.7.1 con wrapper. Arquitectura hexagonal. Se construye por etapas. La base de producción es
Neon (PostgreSQL 18 administrado), y la app corre en Cloud Run: ver *Despliegue*.

## Comandos

Shell habitual: PowerShell 7. En Git Bash, `./gradlew` equivalente.

```powershell
.\gradlew.bat build                 # compila, corre la suite y exige el umbral de cobertura
.\gradlew.bat test                  # solo tests — no necesita Docker ni base
$env:SPRING_PROFILES_ACTIVE = 'local'; .\gradlew.bat bootRun   # levanta la app; sin perfil no arranca

.\gradlew.bat test --tests "*MonthlySpendingIT"                        # una clase
.\gradlew.bat test --tests "*MonthlySpendingIT.devuelve401*"           # un método

docker build -f deployment/Dockerfile -t financeapp-bk .   # la imagen de Cloud Run, desde la raíz
```

**No hay checkstyle ni linter configurado** en `build.gradle`. La verificación es la suite más la
colección Bruno; no prometas un paso de lint que no existe.

Verificación contra la app real. El script se detiene si el puerto está ocupado o si la base
resuelta, con las variables `DB_*` y `SPRING_R2DBC_URL` de la terminal, no es `localhost/financeapp`.
Si pasa, levanta la app de la rama con el perfil `local`, recarga `test-data.sql`, corre la
colección y apaga la app (FA-61):

```powershell
pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos
```

Para depurar un request suelto, desde `bruno/` y con una app que hayas levantado tú contra la base
local:

```powershell
bru run . -r --env local
bru run system -r --env local --env-var baseUrl=http://localhost:8081/api --env-var host=http://localhost:8081
```

El entorno `local` apunta al 8080. Contra la app en otro puerto hay que sobrescribir **las dos**
variables: los requests que prueban el base-path usan `host`, no `baseUrl`.

`bruno-personal/` es una segunda colección, **ignorada por git**, para el uso real de la app con
datos propios contra Neon (app local con `SPRING_PROFILES_ACTIVE=prod`). No tiene tests y no se
corre con `bru run`. **Todo cambio de un endpoint que toque `bruno/` se replica también ahí**
(ruta, cuerpo, autenticación), y el endpoint nuevo recibe su request; como no está versionada, el
PR no lo muestra y nadie más lo va a notar.

Toda etapa cierra con las dos cosas en verde, nunca una sola. El request Bruno de un endpoint nuevo
se escribe en el mismo ciclo TDD que los tests JUnit, antes del endpoint y fallando.

`bru run` no es un complemento. La estrategia es de **dos capas** —JUnit
con mocks, integración desde Bruno contra la app y la base reales—, así que el SQL de las vistas, el
cálculo de las metas y el corte de mes por zona horaria **no los verifica nada más**. Una etapa que
cierre solo con Gradle en verde no está verificada.

## Arquitectura

Hexagonal estricta en tres paquetes bajo `com.oscargabriel.financeapp`:

```
domain/          model, port/in, port/out, exceptions   ← sin Spring (salvo Reactor)
application/     usecase                                 ← implementa port/in, usa port/out
infrastructure/  adapter/in/web, adapter/out/persistence, config
```

El cableado que importa: un controlador depende del **puerto de entrada**
(`GetMonthlySpendingPort`), nunca de la clase del caso de uso. El caso de uso
(`GetMonthlySpendingUseCase`, `@Service`) implementa ese puerto y consume un **puerto de salida**
(`MonthlySpendingQueryPort`), que implementa un adapter de persistencia. Para un endpoint nuevo se
tocan los cuatro archivos en ese orden. La excepción es un endpoint que solo expone los valores de un
enum del dominio, como `GET /api/catalogs/account-types` (FA-59): no hay persistencia ni regla que
orquestar, así que el controlador lee el enum sin puerto ni caso de uso.

Los beans (controladores, casos de uso, adapters) inyectan por constructor con `@AllArgsConstructor`
de Lombok sobre campos `private final`; el constructor explícito queda solo para los que reciben
un `@Value`, construyen algo con la dependencia (`TransactionalOperator.create(txManager)` en
`TransactionR2dbcAdapter` y `UserR2dbcAdapter`) o necesitan `@Qualifier` por haber varias
implementaciones de un mismo puerto.

El dominio sí usa `Flux`/`Mono` en las firmas de los puertos: acoplamiento aceptado en este
proyecto, no un descuido.

### Base path

`spring.webflux.base-path` vale `/api`. Los `@RequestMapping` de los controladores **no** lo
incluyen, pero los tests, Bruno y cualquier cliente piden `/api/...`. Es la fuente número uno de
404 confusos.

### Errores

Todos los errores salen con el mismo JSON: `{"errors":[{code, description, field}]}`, producido por
`WebExceptionHandler` (extiende `AbstractErrorWebExceptionHandler`, `@Order(HIGHEST_PRECEDENCE)`) —
`@ControllerAdvice` es servlet-only y aquí no aplica. Para agregar un tipo de excepción: un `case`
nuevo en el switch de patrones, **siempre antes del `default` y antes de su supertipo**, porque el
switch no admite un subtipo dominado.

**Con una excepción: el 401 de las cadenas de seguridad.** Se resuelve dentro del filtro, antes de
que exista una excepción que el handler global pueda ver, así que su cuerpo lo escribe
`UnauthenticatedEntryPoint`. Si cambias el formato de error, hay que tocar los dos. Ese 401 no dice
nunca por qué falló la autenticación: el motivo va al log, y `WWW-Authenticate` sale como el esquema
pelado —`Basic` o `Bearer` según la cadena, sin `realm`— porque el entry point de Spring publicaría
ahí el detalle técnico como `error_description`. El esquema es un parámetro del constructor y
`SecurityConfig` crea una instancia por cadena, por eso la clase no es un `@Component`.

Los códigos viven en el enum `ErrorCodes`, en SCREAMING_SNAKE_CASE describiendo la categoría, no el
mensaje.

Dos patrones que se repiten y conviene imitar:

- **Los controladores parsean a mano** los path y query params (UUID, `YearMonth`) en vez de dejar
  la conversión a Spring. La conversión fallida de Spring termina en `ServerWebInputException`, que
  el handler global reporta como `JSON_PARSING_ERROR` sobre el body — engañoso para un parámetro de
  ruta.
- **La validación se lanza dentro de un `Flux.defer`**, para que un rango inválido salga como señal
  de error del Flux y no como excepción al ensamblar la cadena.

**Dónde va cada validación de un cuerpo** (FA-55). El formato y las reglas cruzadas que se deciden
mirando solo el cuerpo van como anotaciones en el **record de request** de `adapter/in/web/dto/`:
Bean Validation sobre el componente, o una constraint de clase si la regla cruza campos
(`@CamposDeCredito`, `@ReglasDeTransferencia`), con cada error sobre su propio campo. Las constraints
propias viven en `dto/validation/`, y las de formato aceptan vacío para que un campo vacío dé solo el
error de `@NotBlank`. El controlador recibe `@Valid @RequestBody`. Lo que necesita la base (que la
moneda exista, el correo esté libre, la cuenta o la categoría sean del usuario) y la normalización
(`trim`, `toLowerCase`) se quedan en el caso de uso; los Command no se validan. Una lista como cuerpo
lleva las constraints en el parámetro (`@Size`, `List<@NotNull @Valid …>`), lo que activa la
validación de método de Spring: sus errores llegan como `HandlerMethodValidationException`, que el
handler traduce con el índice (`[3].amount`). Los path y query params siguen el primer patrón de
arriba, no este. El orden de los errores no está garantizado: los tests comparan por contenido.

### Trazabilidad

`LoggingFilter` mete un `requestId` en el Reactor Context; `ReactorMdcHook` lo copia al MDC de SLF4J
en cada evento del pipeline, porque en WebFlux el MDC no cruza schedulers. El patrón de log incluye
`[%X{requestId}]`. Si tocas cualquiera de los dos, verifica que el id siga apareciendo en logs que
pasen por `boundedElastic`.

### Configuración y arranque

- **`application.yaml` es la configuración de Cloud Run**: la imagen solo lleva ese archivo y el
  servicio completa el resto con variables y Secret Manager. `application-local.yaml` sobrescribe
  lo que difiere en desarrollo. `application-prod.yaml` tampoco se versiona y **Cloud Run nunca lo
  lee**: es una copia de `application-local.yaml` que solo cambia la conexión R2DBC a Neon (y el
  pool, con el idle por debajo de la suspensión), para levantar la app en local contra la base
  real con `SPRING_PROFILES_ACTIVE=prod`. Una clave nueva en el local hay que copiarla también
  ahí. Lo que el servicio necesite va en
  `application.yaml`. Ahí están el puerto (`PORT`, que inyecta Cloud Run, antes que `SERVER_PORT`),
  `sslMode=require` porque Neon exige TLS, y un pool dimensionado para Neon: 1 instancia × 10
  frente a sus 901 conexiones (el servicio escala de 0 a 1, FA-48), e idle por debajo de los 5 min
  en los que Neon suspende. Si sube el máximo de instancias, rehacer esa cuenta.
- Lee sin **default** los secretos y `CORS_ALLOWED_ORIGINS`: un despliegue sin `DB_USERNAME` /
  `DB_PASSWORD` / `JWT_SECRET` / `BASIC_USERNAME` / `BASIC_PASSWORD` / `CORS_ALLOWED_ORIGINS` falla
  al arrancar en vez de levantar con valores implícitos. `DB_USERNAME` y `DB_PASSWORD` los enlaza el
  binder de Boot, que deja pasar el placeholder sin resolver como texto literal. Lo que los hace
  fallar en el acto, como a los demás, es `R2dbcCredentialsCheck`, que los vuelve a pedir con
  `@Value` (FA-53): no lo borres por parecer vacío. `JWT_SECRET`
  además tiene que medir 32 bytes o más: `JwtConfig` lo comprueba al construir la clave, porque
  HS256 no firma con menos y el fallo aparecería en el primer login en vez de en el arranque.
- **El esquema `finance` lo fija `R2dbcSearchPathConfig`, no la URL.** Neon ignora `?schema=` y el
  parámetro de arranque `search_path`; solo respeta `options=-c search_path=...`. Como el driver no
  puede expresar `-c k=v` en la URL, un `ConnectionFactoryOptionsBuilderCustomizer` lo manda como
  opción. Ese bean **reemplaza el mapa de opciones completo**: cualquier otra opción de arranque de
  Postgres va ahí, no en la URL, o se pierde. El porqué está en `docs/database/modelo-datos.md`.
- `spring.profiles.active` vale `${SPRING_PROFILES_ACTIVE}`, **sin default**: sin la variable la
  app no arranca, porque Boot rechaza el placeholder sin resolver como nombre de perfil. En local
  (bootRun, IDE, `java -jar`) hay que definir `SPRING_PROFILES_ACTIVE=local`. La imagen de
  `deployment/Dockerfile` ya trae `prod`, y `CloudRunConfigTest` comprueba que la clave no tenga
  default.
- `DatabaseStartupCheck` hace `SELECT 1` antes de que Netty abra el puerto y aborta el contexto si
  la base no responde. Apagado en la suite (`startup.db-check.enabled: false`), así que ni Gradle
  ni Bruno lo ejercitan: al tocarlo, o tocar `PostgresHealthCheckAdapter` o las propiedades
  `startup.db-check.*`, la verificación es el procedimiento manual de
  [`docs/verificaciones-manuales.md`](docs/verificaciones-manuales.md), que cubre los dos caminos.
- El bean `Clock` (`ClockConfig`, zona `app.timezone`) existe para que los casos de uso que dependen
  de «hoy» se puedan probar con fecha fija. Inyéctalo en vez de llamar a `YearMonth.now()`.
- **Dos cadenas y ninguna ruta pública**: `SecurityConfig` expone dos
  `SecurityWebFilterChain`. La de `@Order(0)` lleva un `securityMatcher` con `POST /auth/register`,
  `POST /auth/login` y `GET /status`, y las autentica con `httpBasic` contra el único usuario del
  `MapReactiveUserDetailsService`; la de `@Order(1)` recoge todo lo demás con
  `oauth2ResourceServer().jwt()` y el Basic deshabilitado. CSRF, CORS y `formLogin` salen del método
  `comun(...)`, para que las dos no se separen con el tiempo. Las reglas se escriben **sin** el
  prefijo `/api`: el base-path lo quita el `HttpHandler` antes de que la cadena vea la petición.
  Lo que hay que probar al tocar esto no es que cada credencial sirva, sino que la otra **no**: un
  Bearer válido contra `/status` y un Basic válido contra una ruta de la API tienen que dar 401, y
  `JwtSecurityIT` es lo que avisa si el matcher se corre. El precio, aceptado en FA-43: ya no hay
  ruta pública, así que un cliente sin la credencial compartida no puede ni registrarse, y un
  frontend de navegador la expone a quien abra las DevTools.
- **La clave de firma vive solo en `JwtConfig`**, que expone el `SecretKey` y el `ReactiveJwtDecoder`.
  `JwtTokenIssuerAdapter` recibe ese mismo bean: leer el secreto dos veces por separado dejaría
  emitir tokens que el propio API no acepta. El decoder valida además expiración y emisor.
- Preferir `@Value` sobre inyectar `Environment`.

`src/main/resources/application-local.yaml` no se versiona y tiene las credenciales reales. No hay
archivo de ejemplo: se borró el 15-09-2026 porque se quedaba viejo cada vez que aparecía una clave
nueva. Estas son las que el perfil local necesita, y este párrafo es el que hay que actualizar
cuando cambien:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://localhost:5432/financeapp   # sin TLS; el esquema lo pone R2dbcSearchPathConfig
    username: postgres
    password: ...
  security:
    jwt:
      secret: ...        # 32 bytes o más, o el contexto no arranca
      expiration: 1h
    basic:
      username: ...      # credencial compartida de /auth/* y /status
      password: ...      # la misma que BASIC_USERNAME/BASIC_PASSWORD de bruno/.env
cors:
  allowed-origins: "*"   # el base no trae default: sin esta clave local no arranca
```

## Tests

`*Test` son unitarios o de slice, `*IT` de integración. **No hay source set aparte**: `test` los
corre todos, y ninguno necesita base ni Docker.

- Persistencia: no se prueba en la suite. Los adapters R2DBC se verifican desde `bruno/` contra
  PostgreSQL real; no vuelvas a introducir `@Testcontainers` sin cambiar esta decisión.
- Web con servidor real: `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `WebTestClient`.
- Reactivo: `StepVerifier`, no `block()`.
- Datos de prueba: Object Mother en `src/test/java/.../support/`.

`src/test/resources/application.yaml` **reemplaza por completo** al de `main` en el classpath de
test. Toda propiedad nueva que un bean exija con `@Value` hay que replicarla ahí o el contexto
revienta con `PlaceholderResolutionException`. Su R2DBC apunta a `localhost:65535` a propósito, y
**nada sobrescribe esa URL**: cualquier test que intente hablar con
la base falla por diseño. Si necesitas ejercitar una consulta, el lugar es `bruno/`.

Por lo mismo, ningún test ve el `application.yaml` de `main` por el classpath. `CloudRunConfigTest`
lo lee del disco y lo resuelve contra variables simuladas del servicio: es el lugar para comprobar
cualquier cambio en la configuración que va a Cloud Run.

### Cobertura

JaCoCo mide sobre la suite de Gradle. El reporte queda en
`build/reports/jacoco/test/html/index.html` (ábrelo en el navegador; el XML del mismo directorio
es para herramientas). Se regenera solo: `test` lo produce como paso final.

El umbral es **85 % de línea** y lo exige `jacocoTestCoverageVerification`, colgado de `check`.
Es decir: `gradlew test` mide y deja el reporte, **`gradlew build` es el que falla** si se baja del
umbral. El mensaje del fallo dice el ratio real y el mínimo esperado.

Quedan fuera del cálculo dos patrones, listados en `build.gradle` con el porqué:
`FinanceappBkApplication` (el `main`) y `*R2dbcAdapter` (todos los adapters R2DBC: los verifica
`bruno/`, y la suite no tiene base). **Los DTOs y la configuración sí cuentan** — están entre el
90 y el 100 %, y excluirlos, como suele hacerse por inercia, solo bajaría el número y escondería
el dato.

No leas el porcentaje de rama como si fuera el de línea: va por debajo, y `WebExceptionHandler` es
la clase que más ramas deja sin cubrir. No hay umbral de rama a propósito, hasta que esa clase
tenga tests.

## Base de datos

No hay Flyway ni `schema.sql` en el arranque: el esquema se crea a mano desde `docs/database/`.
**Eso es una decisión, no una pendiente** — evaluada contra Flyway sobre JDBC y cerrada el
14-09-2026; el porqué y la condición para revisarla están en `docs/database/modelo-datos.md`. Su
precio es el doble apunte: cada cambio se escribe en `schema.sql` **y** se emite como update en
`docs/database/update/`, a mano las dos veces. Después de emitir un update, corre la comparación de
esquemas de ese mismo documento: es lo único que detecta que las dos copias hayan divergido.
Ningún test monta ya esos archivos, así que **un cambio en `schema.sql` o `seed.sql` no rompe la
suite: rompe `bru run`**, y solo si te acuerdas de correrlo. El escenario de la colección es
`docs/database/test-data.sql`, que se carga con psql y usa fechas relativas al mes en curso.

`src/test/resources/db/monthly-spending-fixture.sql` quedó sin uso al salir Testcontainers. Se
conserva porque sus fechas absolutas y su segundo usuario son la base del escenario que falta
montar en Bruno.

**Producción es Neon, y ahí `test-data.sql` no se carga nunca.** Solo van `schema.sql`, `seed.sql`
y los `update/`, aplicados a mano con psql, igual que en local. Cómo se aplica un update ahí, el
registro de los ya aplicados y el respaldo del plan están en
[`docs/despliegue.md`](docs/despliegue.md#base-de-datos-neon). El encabezado de cada update dice
si va antes o después del despliegue de su app; ese orden es el que sigue el procedimiento.

## Despliegue

Rama de trabajo `dev`; **`main` es producción**. El servicio `financeapp-bk-git` (Cloud Run,
`europe-west1`) corre la app desde el 05-10-2026, desplegada por el pipeline (FA-46) con los
secretos y el escalado del servicio (FA-48).

`deployment/cloudbuild.yaml` lo ejecuta un disparador de Cloud Build en cada push a `main`: construye con
`deployment/Dockerfile`, publica en Artifact Registry (`europe-west1`) con el SHA corto y `latest`,
y despliega por el SHA. **El despliegue solo cambia la imagen**: variables, secretos, cuenta y
escalado viven en el servicio, no en el archivo, así que un cambio de configuración se hace en el
servicio y se anota en `docs/despliegue.md`. Artifact Registry conserva una sola versión, así que
revertir es revertir el commit en `main`, no mover el tráfico. El detalle, el disparador y los roles
están en `docs/despliegue.md`.

- El contexto del build es la **raíz del repo**, no `deployment/`. El `.dockerignore` de la raíz es
  una **lista de lo permitido** (`gradlew`, `gradle/`, `build.gradle`, `settings.gradle`, `src/`),
  que además excluye los dos yaml sin versionar. Un archivo nuevo que el build necesite fuera de
  esas rutas hay que agregarlo ahí, o el `COPY` falla.
- La etapa de compilación corre `./gradlew build`, no solo `bootJar`: un test rojo o la cobertura
  por debajo del umbral no producen imagen, y nada se despliega.
- La imagen usa el Java de Microsoft (`mcr.microsoft.com/openjdk`): compila en `jdk:25-ubuntu` y
  corre en `jdk:25-distroless`. Es una decisión, no la opción por defecto: no cambiarla por Temurin
  u otra por tamaño. La distroless **no tiene shell**. El usuario es el `app` que ya trae la imagen,
  y para inspeccionarla se usa `docker export`, no `docker run --entrypoint sh`.

## Flujo de trabajo: spec-driven con OpenSpec

Tres lugares, cada uno con una sola pregunta que responde:

| Dónde | Responde |
|---|---|
| Tablero de Notion (tareas `FA-n`) | Qué falta, con qué prioridad y en qué estado |
| `openspec/specs/` | Qué hace el sistema **hoy**, por capacidad. Es la documentación viva |
| `openspec/changes/` y `openspec/changes/archive/` | Qué cambio está en marcha, y el porqué de cada uno ya hecho |

Las reglas de cada artefacto (proposal, specs, design, tasks) están en `openspec/config.yaml`.

El ciclo de una tarea:

1. La tarea sale del tablero. Un encargo que llegue suelto —un mensaje, una conversación— se
   registra primero como tarea, para que tenga `ID`.
2. Rama `feature/fa-<n>-<slug>` desde `dev`.
3. Change `fa-<n>-<slug>`: explorar el código si hace falta y proponer (proposal, delta de spec con
   escenarios, design si aplica, tasks). **Lo que se aclare conversando se escribe en esos archivos**:
   un alcance que solo quedó dicho en el chat no existe para el siguiente que lea.
4. Revisión humana de la propuesta. Sin aprobación no se escribe código.
5. Implementar tarea por tarea con TDD —el test primero, fallando por la razón correcta— y marcar
   cada casilla de `tasks.md` al terminarla.
6. Verificar: `gradlew build` y `bru run`, los dos en verde.
7. Archivar el change **en la misma rama**: la delta se fusiona en `openspec/specs/` y el change
   pasa a `archive/`. El PR lleva juntos el código, la spec actualizada y el change archivado.
8. PR a `dev`, merge inmediato, y la tarea pasa a `Por revisar` con la evidencia.

Las specs crecen con los cambios: no se escriben specs de lo que no se está tocando, porque nada las
mantendría al día. Un change que no altera comportamiento (refactor, herramientas, documentación)
lleva `skip_specs: true` en su `.openspec.yaml`.

Las decisiones de arquitectura que no son comportamiento —por qué no hay Flyway, por qué el Basic
compartido— quedan en el `design.md` del change que las tomó, o en `docs/` cuando ya existían antes
de este flujo (`docs/database/modelo-datos.md`).
