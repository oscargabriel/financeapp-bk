# Design

## Context

Hoy es un solo proyecto de Gradle con un solo árbol de paquetes. El asistente (FA-77) es una rebanada
de ese árbol:

- `AssistantUseCase`, con sus puertos `AssistantPort` y `AssistantModelPort` y los modelos
  `Assistant*`.
- `ResolutorDeNombres`, que solo usa él.
- `AssistantController` y sus DTO.
- `adapter/out/assistant/`, con Gemini y el stub, y los recursos de `asistente/`.

Para trabajar depende de cinco piezas del core: `AccountQueryPort`, `CategoryQueryPort`,
`GetTransactionReportPort`, `GetBalancePort` y `CreateTransactionsPort`. Su respuesta reutiliza
`TransactionResponse`, `TransactionReportResponse` y `BalanceResponse`.

Las lecturas de reporte y saldo del core no son puras. Llaman a `SeriesAlDia.ponerAlDia`, que
registra las ocurrencias vencidas de las series (FA-107), y convierten los montos a la moneda del
usuario con el fragmento SQL `MontoDeLaPersona.DE_T` (FA-122).

## Goals / Non-Goals

**Goals:**

- Que el límite entre módulos lo haga cumplir el compilador, no una convención.
- Que agregar el módulo de Telegram sea repetir el molde de `ia`.

**Non-Goals:**

- Mover lógica del core. Fuera del puerto nuevo de series, el core queda con el mismo
  comportamiento; solo cambian los nombres de paquete.
- Cambiar cómo se configuran la seguridad, los errores o la trazabilidad. Cambian de módulo, no de
  forma.

## Decisions

### 1. Subproyectos de Gradle ensamblados en una sola app

`settings.gradle` incluye `compartido`, `core`, `ia` y `app`, cada uno en su carpeta en la raíz del
repo. Solo `app` aplica el plugin de Spring Boot y produce el jar ejecutable; los demás son
`java-library` con el BOM de Boot por el plugin `io.spring.dependency-management`. La configuración
común (toolchain 25, Lombok, JUnit) va en el `build.gradle` raíz con `subprojects {}`.

Descartadas:

- **Servicios separados.** Lo descartó el usuario: duplica despliegue, pipeline y autenticación
  entre servicios.
- **Paquetes dentro de un solo proyecto, vigilados por ArchUnit.** El límite fallaría en el test,
  no al compilar. Además, ArchUnit no lo gestiona el BOM de Boot, y la regla global pide que las
  dependencias nuevas sí lo estén.
- **Convention plugin en `buildSrc`.** Con cuatro subproyectos y una sola convención, `subprojects
  {}` se lee en un archivo y no agrega una compilación más. Si aparecen convenciones distintas por
  tipo de módulo, ese es el momento de pasar a `buildSrc`.

### 2. Dos módulos de soporte además de los de negocio

`core` e `ia` son los módulos que pidió el usuario. Hacen falta otros dos:

- **`compartido`.** Lo que cualquier módulo de negocio necesita para hablar el mismo idioma:
  - `ErrorCodes`, `BadRequestException`, `ErrorDetail` y `ErrorResponse`;
  - `WebExceptionHandler`, `UnauthenticatedEntryPoint` y `UsuarioDelToken` (este último entra
    con el grupo 4 de tasks, cuando la IA lo necesita; hasta entonces es package-private del core);
  - `SecurityConfig` y `JwtConfig`;
  - `LoggingFilter`, `ReactorMdcHook` y `WebConfig`;
  - `ClockConfig` y `ResourcesWebPropertiesConfig`.

  Sigue el mismo orden interno: lo que no usa Spring en `domain/`, el resto en `infrastructure/`.
- **`app`.** El `main`, `R2dbcSearchPathConfig`, `R2dbcCredentialsCheck`, `application.yaml` y los
  adaptadores puente de la decisión 3. Los `*IT`, `FinanceappBkApplicationTests` y
  `CloudRunConfigTest` viven aquí, porque levantan la aplicación entera o leen su yaml.

`DatabaseStartupCheck` se queda en el core con `HealthCheckPort`, que es suyo. `UuidV7` también:
solo el core genera ids, y la IA únicamente lee.

Dos ajustes de build que el corte hizo visibles. Los dos están en el `build.gradle` de su módulo:

- **`-parameters` en todos los subproyectos.** El plugin de Spring Boot solo agrega ese flag
  donde se aplica, que es `app`; sin él, los controladores de los demás módulos no conocen el
  nombre de sus parámetros.
- **El yaml de test se copia a los recursos de test de `app`.** En el classpath de test de `app`
  el jar de fixtures va después de `src/main/resources`, y si no se copia gana el yaml de Cloud
  Run.

`SecurityConfig` nombra rutas del core (`/auth/register`, `/auth/login`, `/status`). Se acepta: es
la configuración de las dos cadenas de toda la aplicación, y partirla por módulo separaría lo que
`AGENTS.md` pide mantener junto (el método `comun(...)`).

Descartada: que `ia` dependa de `core` para tomar sus errores y su seguridad. Le abriría todas las
clases del core, que es justo lo que la tarea quiere cerrar.

### 3. La IA no depende del core: los puentes viven en `app`

`ia` depende solo de `compartido`. Lo que necesita del core lo declara como puertos de salida
propios en `ia/domain/port/out`:

- **registrar un movimiento pendiente.** Recibe el usuario, el tipo, los ids resueltos, el monto,
  la moneda, la descripción y la fecha. Devuelve `id`, `status` y `occurredAt`.
- **poner al día las series de un usuario.**

`app` los implementa con dos adaptadores que llaman a los puertos de entrada del core: el primero a
`CreateTransactionsPort` con `TransactionOrigin.TELEGRAM`, el segundo al puerto nuevo de la
decisión 4. Así, `ia` no ve ni el modelo del core. El `transaction` liviano lo arma la IA con lo que
ya resolvió (los nombres) y lo que devuelve el puente.

Los 400 del alta siguen llegando como `BadRequestException`, que ahora es de `compartido`. Por eso
la IA los sigue traduciendo a `NEEDS_CLARIFICATION` igual que hoy.

Descartadas:

- **`ia` depende de `core` y llama directo a `CreateTransactionsPort`.** Es más corto, pero Gradle
  ya no impediría usar cualquier otra clase del core.
- **Partir `core` en `core-dominio` y el resto.** Cerraría el límite, pero obliga a la IA a usar
  los modelos del core en su propio dominio y agrega un subproyecto más.

### 4. Puerto de entrada nuevo en el core para las series

`CatchUpSeriesPort.ponerAlDia(UUID userId): Mono<Void>`, implementado por un caso de uso que delega
en `SeriesAlDia`. `SeriesAlDia` sigue siendo interno del core.

Descartada: que la IA registre las ocurrencias vencidas por su cuenta. Sería la escritura más
delicada del sistema, duplicada en dos lugares.

### 5. Las lecturas propias de la IA y su deriva

`ia/infrastructure/adapter/out/persistence` tiene sus `*R2dbcAdapter` con SQL a la medida:

- cuentas activas y categorías vivas, con id, nombre, moneda y ámbito;
- movimientos del rango, con los filtros del reporte y los nombres ya unidos;
- sumas del periodo y desde siempre;
- saldo de las cuentas activas.

Las reglas que hoy aplica el core se copian, no se importan:

- `status = 'CONFIRMED'`;
- el corte de días en la zona del usuario;
- "programado" como `occurred_at > now()`;
- la conversión a la moneda del usuario de `MontoDeLaPersona.DE_T`.

Ese duplicado es el precio de la decisión del usuario. Lo vigilan los requests de `bruno/assistant/`
que comparan los totales y la lista del asistente con los de `/reports`: si una de las dos copias
cambia sola, la colección falla.

### 6. Paquetes por módulo

- `com.oscargabriel.financeapp.core…`, `…ia…` y `…compartido…`, cada uno con `domain`,
  `application` e `infrastructure` debajo.
- La clase `main` queda en `com.oscargabriel.financeapp`, en `app`, así que el escaneo de
  componentes de Boot cubre los cuatro sin configurar nada.
- `ResolutorDeNombres` y los modelos `Assistant*` pasan a `ia`.

### 7. Tests por módulo, con soporte común en test fixtures

- **Soporte común.** `compartido` aplica `java-test-fixtures` (de Gradle, sin dependencia nueva) y
  ahí publica:
  - el `application.yaml` de test;
  - `TokenMother`, `BasicMother`, `RelojFijo` y `Violaciones`.

  Los demás módulos lo consumen con `testImplementation testFixtures(project(':compartido'))`. Las
  Object Mother de cada dominio van con su módulo.
- **Slices web.** Un `@WebFluxTest` necesita una `@SpringBootConfiguration` en su classpath, y en
  `core` e `ia` no hay `main`. Cada uno lleva en `src/test` una clase vacía anotada con
  `@SpringBootConfiguration` y `@EnableAutoConfiguration` para eso.

### 8. Cobertura agregada en `app`

`app` aplica `jacoco-report-aggregation` (de Gradle) y verifica el 85 % de línea sobre el reporte
agregado de los cuatro módulos. Las exclusiones son las mismas: el `main` y `**/*R2dbcAdapter`, que
ahora cubre también los adaptadores de la IA. El reporte queda en
`app/build/reports/jacoco/testCodeCoverageReport/`.

Descartada: un umbral por módulo. `compartido` lo ejercitan sobre todo los tests de los otros
módulos, y medido solo con los suyos daría un número que no dice nada. El agregado conserva el
significado que tiene hoy el umbral.

## Risks / Trade-offs

- **[Riesgo] Las consultas de la IA se desvían de las del core.** → Los requests de Bruno comparan
  totales, ids y orden con `/reports`. El duplicado queda nombrado en `AGENTS.md`.
- **[Riesgo] Un diff enorme de paquetes esconde un cambio de comportamiento.** → El movimiento se
  hace en commits separados: primero mover sin tocar lógica (la suite y Bruno en verde), después la
  IA con sus lecturas. Para el primer paso, `git diff -M` debe mostrar renombres.
- **[Riesgo] La imagen deja de construir porque el Dockerfile copia rutas viejas.** → `docker build`
  forma parte de la verificación de este change, no solo del pipeline.
- **[Trade-off] `app` tiene lógica de traducción (los puentes).** Es poca y sin reglas propias:
  arma un comando y devuelve tres campos.
- **[Trade-off] Los yaml sin versionar cambian de carpeta.** Se mueven en la máquina local durante
  el change. Quien tenga otra copia del repo tiene que moverlos a mano; `AGENTS.md` da la ruta
  nueva.

## Migration Plan

- Sin cambios de base ni de variables del servicio. El despliegue es el de siempre al llegar a
  `main`.
- Revertir es revertir el commit.
- El único cliente afectado por la forma nueva es quien llame a `/assistant/messages`, que hoy no
  tiene consumidores fuera de Bruno.
