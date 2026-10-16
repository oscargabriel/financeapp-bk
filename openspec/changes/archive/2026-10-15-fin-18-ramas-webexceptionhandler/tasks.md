# Tasks

El código de producción ya existe, así que los tests nuevos pasan de entrada. Para ver que cada uno
prueba lo que dice, se lo hace fallar una vez cambiando el `code` o el `field` esperado, y se
comprueba en el reporte de JaCoCo (`build/reports/jacoco/test/jacocoTestReport.xml`) que la rama
quedó cubierta. Si un test muestra una respuesta distinta de la que declara el código, se para y se
corrige el change con `/opsx:update`.

## 1. Ramas del switch de `WebExceptionHandler`

Skills: `java-testing`, `java-exceptions`.

- [x] 1.1 Crear `WebExceptionHandlerTest` en `compartido/infrastructure/adapter/in/web/` con el
  armado del handler de `design.md` (`DefaultErrorAttributes`, `WebProperties.Resources`,
  `StaticApplicationContext`, `ServerCodecConfigurer`, `JsonMapper`, `afterPropertiesSet()`) y un
  helper que llama a `handle(MockServerWebExchange, ex)` y devuelve status y `errors`. Primer test:
  `BadRequestException` sale con su status y su `ErrorResponse` tal cual. Verifica:
  `gradlew test --tests "*WebExceptionHandlerTest"`.
- [x] 1.2 Un test por cada `case` que falta, comprobando status, `code` y `field`:
  `NumberFormatException` → 400 `INVALID_NUMBER_FORMAT` `parameter` (y no `INVALID_ARGUMENT`, que es
  el de su supertipo); `IllegalArgumentException` → 400 `INVALID_ARGUMENT` con el mensaje como
  `description`; `ServerWebInputException` sin `reason` y con `reason` en blanco → 400
  `JSON_PARSING_ERROR` «Cuerpo de la peticion invalido» `body`; `ResponseStatusException` 405 → 405
  `VALIDATION_ERROR` `request`; `ResponseStatusException` 404 y 413 con sus códigos; una
  `RuntimeException` cualquiera → 500 `INTERNAL_SERVER_ERROR` `server` sin el mensaje original.
  Verifica: `WebExceptionHandlerTest` en verde.
- [x] 1.3 `WebExchangeBindException` con un error de campo (`field` = nombre del campo) y un error
  global de objeto (`field` = `unknown`). Verifica: `WebExceptionHandlerTest` en verde.
- [x] 1.4 Respaldo de serialización: con un `ObjectMapper` mockeado que lanza en
  `writeValueAsString`, la respuesta es 500 `INTERNAL_SERVER_ERROR` `server`. Verifica:
  `WebExceptionHandlerTest` en verde.

## 2. Validación de método y `BadRequestException`

Skills: `java-testing`, `java-exceptions`.

- [x] 2.1 `HandlerMethodValidationException` armada sobre los `MethodParameter` de un método dummy
  del test (`@RequestBody List<…>` y `@RequestParam`): error de campo de un elemento → `[3].amount`;
  error de objeto de un elemento (constraint de clase) → `[3]`; error de campo sin índice →
  `amount`; error del parámetro completo `@RequestBody` → `body`, y que se reporta solo ese aunque
  haya errores de elementos; error de un parámetro que no es `@RequestBody` → su nombre. Verifica:
  `WebExceptionHandlerTest` en verde y `WebExceptionHandler` en 34/34 ramas en el XML de JaCoCo.
- [x] 2.2 Crear `BadRequestExceptionTest` en `compartido/domain/exceptions/`: con lista vacía el
  mensaje es «Bad Request»; con detalles, el `description` del primero. Verifica:
  `gradlew test --tests "*BadRequestExceptionTest"` y 2/2 ramas en el XML de JaCoCo.

## 3. Umbral de rama y documentación

Sin skills de dominio.

- [x] 3.1 Agregar a `jacocoTestCoverageVerification` en `build.gradle` un `limit` de `BRANCH`
  `COVEREDRATIO` con `minimum = 0.90`, con el porqué en el comentario de arriba. Verifica: subirlo
  a mano a `0.99` hace fallar `gradlew build` con el mensaje de rama; con `0.90`, `gradlew build`
  pasa.
- [x] 3.2 `AGENTS.md`, sección *Cobertura*: el umbral es 85 % de línea **y** 90 % de rama; quitar
  «No hay umbral de rama a propósito…» y la frase que señala a `WebExceptionHandler`. Sin claves de
  tareas (decisión del epic, 14-10-2026). Verifica: `grep -n "rama" AGENTS.md` muestra la regla nueva.
- [x] 3.3 `openspec/config.yaml` (contexto: «cobertura de línea >= 85 % y de rama >= 90 %»),
  `.claude/skills/verificar/SKILL.md` (el reporte incluye la rama con su umbral) y
  `docs/despliegue.md` (el paso de Cloud Build habla de los umbrales, no solo del 85 %). Verifica:
  `grep -rn "85 %" AGENTS.md openspec/config.yaml .claude/skills/verificar docs/despliegue.md` no
  deja ninguna mención que omita la rama.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde; anotar el conteo de tests y la cobertura real de línea y
  rama, global y de `WebExceptionHandler`.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos de requests y assertions de la salida.

## Workflow follow-up

- Archivar el change en la misma rama antes del PR (`/opsx:archive`).
- PR a `dev`, merge con `merge-pr-dev.ps1`, comentario de cierre en FIN-18 y transición a `Por revisar`.
