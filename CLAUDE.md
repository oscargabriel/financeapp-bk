# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

Backend de finanzas personales. Java 25 + Spring Boot 4.1.1 (WebFlux), R2DBC contra PostgreSQL 18,
Gradle 9.7.1 con wrapper. Arquitectura hexagonal. Se construye por etapas. En producción corre en
Cloud Run contra Neon (PostgreSQL 18 administrado).

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

Verificación contra la app real, desde `bruno/` con la app levantada:

```powershell
bru run . -r --env local
bru run system -r --env local --env-var baseUrl=http://localhost:8081/api --env-var host=http://localhost:8081
```

El entorno `local` apunta al 8080. Contra la app en otro puerto hay que sobrescribir **las dos**
variables: los requests que prueban el base-path usan `host`, no `baseUrl`.

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
tocan los cuatro archivos en ese orden.

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
  `sslMode=require` porque Neon exige TLS, y un pool dimensionado para Neon: 3 instancias × 10
  frente a sus 901 conexiones, e idle por debajo de los 5 min en los que Neon suspende. Si sube el
  máximo de instancias, rehacer esa cuenta.
- Lee sin **default** los secretos y `CORS_ALLOWED_ORIGINS`: un despliegue sin `DB_USERNAME` /
  `DB_PASSWORD` / `JWT_SECRET` / `BASIC_USERNAME` / `BASIC_PASSWORD` / `CORS_ALLOWED_ORIGINS` falla
  al arrancar en vez de levantar con valores implícitos. Ojo con `DB_USERNAME` y `DB_PASSWORD`: el
  binder de Boot deja pasar el placeholder sin resolver como texto literal, así que no fallan al
  enlazar sino en `DatabaseStartupCheck`, cuando la base rechaza la autenticación. `JWT_SECRET`
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
y los `update/`, aplicados a mano con psql, igual que en local.

## Despliegue

Rama de trabajo `dev`; **`main` es producción**. Cloud Run tiene un disparador de despliegue
continuo sobre `main` que construye con `deployment/Dockerfile`, sin `cloudbuild.yaml`: un merge a
`main` despliega.

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
## Dónde viven las tareas

En Notion, no en este repo ni en la memoria. El servidor MCP `notion` está declarado en `.mcp.json`
con alcance de proyecto: solo existe aquí. Requiere `claude mcp login notion` una vez por máquina.

El ciclo —consumir la tarea, implementarla, actualizarla— está en el skill `tareas-notion`, que
tiene los IDs de las bases. El diseño completo está en
`docs/superpowers/specs/2026-09-11-notion-tareas-design.md`.

**Reparto con la memoria persistente:** Notion guarda lo accionable (pendientes de cobertura, mocks
por cerrar, etapas futuras). La memoria del proyecto guarda las decisiones y el porqué. No se
duplica.

Esto adapta la regla del CLAUDE.md global que pide registrar cada mock o stub acordado en una
memoria tipo `project`: aquí se cumple creando la tarea en Notion, y la memoria conserva la decisión
de diseño. Es deliberado, no un olvido.
