---
name: java-testing
description: >
  Use when writing, designing, reviewing or structuring tests in a Java reactive project
  (Spring WebFlux / Project Reactor), and when driving TDD on one: "escribamos los tests
  para...", "generemos primero los tests", "hagamos esto con TDD", "cómo pruebo este
  Mono/Flux", "tests de integración con R2DBC/Mongo", or standardizing a project's test
  architecture. Covers the RED-GREEN-REFACTOR cycle, hexagonal layer placement
  (domain/application/infrastructure), StepVerifier, WebTestClient, Testcontainers, test
  slice selection (@WebFluxTest, @DataR2dbcTest, @DataMongoTest, @SpringBootTest) and test
  data with Object Mother + Builder.
  Skip for non-Java projects or non-reactive (servlet-based) Spring projects.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Java Testing — TDD reactivo sobre arquitectura hexagonal

Dos objetivos: que el test exista **antes** que el código que lo satisface, y que todos los tests
del proyecto se vean igual sin importar quién los escriba. La skill es genérica: los ejemplos usan
dominios neutros (usuario, cuenta, pedido) y se adaptan a cualquier proyecto Java reactivo.

## Archivos de referencia

Léelos cuando la tarea lo pida, no de entrada:

- `references/layers.md` — plantilla concreta de cada capa (dominio, caso de uso, slice web,
  persistencia R2DBC/Mongo, cliente HTTP, E2E, ArchUnit). Léelo al escribir un test de una capa.
- `references/reactor-patterns.md` — recetario de StepVerifier y WebTestClient: valores, errores,
  tiempo virtual, `PublisherProbe`, contexto reactivo, depuración. Léelo antes de una aserción
  reactiva no trivial.
- `references/test-data.md` — Object Mother + Builder y dónde viven los fixtures en Gradle/Maven.
- `references/anti-patterns.md` — anti-patterns con before/after. Léelo al revisar tests ajenos o
  cuando un test "se siente raro".

## El ciclo

```
1. Acordar el incremento     → la pieza más pequeña con valor observable
2. Ubicar la capa            → define tipo de test, qué se aísla, qué herramientas
3. RED   → escribir el test  → debe fallar por la razón correcta (no por no compilar)
4. GREEN → código mínimo     → lo justo para pasar, sin adornos
5. REFACTOR → limpiar        → con los tests como red de seguridad
6. Repetir                   → siguiente incremento
```

El test va primero: sin un RED que falle apuntando al comportamiento, el código de producción no
tiene especificación y el test escrito después tiende a confirmar lo que el código ya hace —
incluido el bug. Si el usuario pide "implementa X", reencuadra: primero el test que define qué es
"X funcionando", luego la implementación mínima.

Única excepción: cubrir **código heredado** con characterization tests. Ahí el test documenta el
comportamiento actual, no uno deseado, y se marca como tal.

**Un test prueba un comportamiento.** Si la funcionalidad es grande, descomponla en incrementos,
propón el orden empezando por las reglas de dominio (más baratas y estables) y confirma el primer
incremento antes de escribir.

En RED, el test debe fallar porque la funcionalidad no existe o no cumple — no porque no compila
ni porque el test está mal armado. Un RED por la razón equivocada es un test que miente.

En GREEN, el mínimo para pasar. La generalización llega cuando un segundo test la exige.

## Adaptarse al proyecto antes de escribir

Las convenciones de abajo son el default; el proyecto manda. Antes del primer test de la sesión,
detecta:

1. **Build:** Gradle o Maven, ¿multi-módulo? → define cómo separas `Test`/`IT` y dónde van los
   fixtures.
2. **Arquitectura real:** hexagonal estricto, capas clásicas o mezcla. Si es clásico, mapea:
   controller → adaptador de entrada, service → aplicación, repository → adaptador de
   persistencia, modelos con lógica → dominio.
3. **Convenciones existentes:** mira 2-3 tests ya escritos. Si el proyecto ya tiene un estilo de
   nombres, una librería de aserciones o una forma de fixtures, confórmate: la consistencia con lo
   que existe vale más que la convención "ideal".
4. **Dependencias:** `reactor-test`, Testcontainers, MockWebServer/WireMock, ArchUnit. Si falta
   una necesaria, dilo y propón añadirla en vez de asumirla presente.

Si no hay tests previos, aplica los defaults de esta skill y deja constancia de las convenciones
elegidas para que el resto del proyecto las siga.

## La capa decide el test

Este es el corazón de la consistencia: a la misma capa, siempre el mismo tipo de test.

| Código bajo test | Tipo | Anotación | Qué se aísla | Herramienta reactiva |
|---|---|---|---|---|
| `domain/model`, `domain/service` | Unitario puro | ninguna | nada | JUnit 5 + AssertJ |
| `application/usecase` | Unitario con mocks | `@ExtendWith(MockitoExtension.class)` | los puertos de salida | StepVerifier |
| `adapter/in/web` (controllers) | Slice | `@WebFluxTest(XController.class)` | el caso de uso | WebTestClient |
| `adapter/out/persistence` | Integración | `@DataR2dbcTest` / `@DataMongoTest` + Testcontainers | nada (DB real) | StepVerifier |
| `adapter/out` HTTP externo | Integración | ninguna (MockWebServer/WireMock) | el servidor remoto | StepVerifier |
| Flujo crítico completo | Integración amplia | `@SpringBootTest(RANDOM_PORT)` | nada | WebTestClient |

Pirámide: muchos tests de dominio y aplicación (rápidos, sin Spring), pocos de integración, muy
pocos E2E. Escribir `@SpringBootTest` para probar una regla de negocio significa estar en la capa
equivocada.

Las plantillas exactas de cada fila están en `references/layers.md`.

## Convenciones fijas

**Nombres de clase:** `XxxTest` para unitarios, `XxxIT` para integración (el sufijo `IT` permite
separarlos por source set o tag en el build), `XxxArchitectureTest` para ArchUnit.

**Nombres de método:** describen comportamiento, no implementación. Una convención por clase:
`should_[expected]_when_[condition]`, `given[Context]_when[Action]_then[Outcome]` o frases en
español (`retornaErrorCuandoElSaldoEsInsuficiente`). Acepta la del proyecto si ya existe.

**Cuerpo Given/When/Then** separado con comentarios o líneas en blanco. En tests reactivos, When y
Then se fusionan dentro de `StepVerifier.create(...)`. Una sola razón para fallar por test: si son
comportamientos distintos, son tests distintos. `@Nested` agrupa escenarios del mismo método.

**Datos de prueba:** nunca `new` inline. Object Mother que devuelve Builder con defaults válidos,
sobreescribiendo solo la dimensión que ese test prueba (`unUsuario().conSaldo(Money.of(100)).build()`).
Ver `references/test-data.md`.

**Aserciones reactivas:** todo `Mono`/`Flux` pasa por `StepVerifier`, terminado con
`verifyComplete()`, `verify()` o `verifyError()`. Sin `.block()` y sin `Thread.sleep()`: para
lógica temporal, `StepVerifier.withVirtualTime(...)`. Ver `references/reactor-patterns.md`.

**Cobertura mínima de seguridad (OWASP):** cada endpoint protegido tiene un test de 401 sin
credenciales (y 403 con rol insuficiente si hay roles), y cada VO que valida entrada externa tiene
al menos un test de rechazo con input inválido o malicioso.

## Red flags

| Señal | Por qué importa |
|---|---|
| `.block()` en un test reactivo | Puede hacer deadlock y oculta backpressure |
| `StepVerifier` sin `.verify*()` | El verifier no se suscribe: el test es un no-op que siempre pasa |
| `@SpringBootTest` para un unit test | 10-30x más lento sin ganar cobertura |
| Test sin assertions | Da falsa confianza |
| Mockear la clase bajo test | Se prueba el mock, no el código |
| `verify()` como única assertion | Prueba que se llamó un método, no el resultado |
| `Thread.sleep()` en tests reactivos | Flaky en CI; usar tiempo virtual |
| Falla al cambiar el orden de ejecución | Estado compartido — bug de diseño del test |
| `@MockBean` en Spring Boot 4 | Removida; usar `@MockitoBean` |

## Skills relacionadas

- Test que falla sin causa obvia y con frames de Reactor → `java-debugging`.
- Formato de `ErrorResponse`/`ErrorDetail` al testear errores → `java-exceptions`.
- Dónde vive el código bajo test si la capa no está clara → `java-architect`.

Al cerrar el trabajo, correr la suite completa con el wrapper del proyecto y reportar el conteo
(skill `verificar`).

## Compact Rules

Bloque inyectable para sub-agentes que generen tests en este stack:

- El test se escribe antes que el código de producción y se ve fallar por la razón correcta.
- Aserciones sobre `Mono`/`Flux` con `StepVerifier`, siempre con llamada terminal. Sin `.block()`,
  sin `Thread.sleep()`; tiempo virtual para delays, timeouts y retries.
- Tests de `domain/`: sin imports de `org.springframework`. Tests de `application/`: mockear los
  puertos (interfaces), con `@ExtendWith(MockitoExtension.class)`. Tests de `adapter/in/web`:
  `@WebFluxTest(XController.class)` + `WebTestClient` + `@MockitoBean`. Tests de persistencia:
  slice de datos + Testcontainers con `@Container static`.
- Nada de `@SpringBootTest` para unit tests de dominio o de caso de uso.
- Naming: `XxxTest` unitario, `XxxIT` integración; métodos que describen comportamiento.
- Datos de prueba vía Object Mother + Builder (o el `@Builder` de Lombok si el tipo ya lo tiene),
  nunca `new` inline.
