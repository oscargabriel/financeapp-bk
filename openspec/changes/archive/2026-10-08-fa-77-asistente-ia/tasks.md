# Tasks

## 1. Origen del movimiento

Skills: `java-architect`, `bruno-cli`.

- [x] 1.1 `bruno/transactions/`: las aserciones de `"origin": "WEB"` en el alta y en el caso del
  cliente que manda `"origin"`, escritas antes y fallando.
- [x] 1.2 `TransactionOrigin` con su estado inicial, y `Transaction.origin`. Verificar:
  `TransactionOriginTest` (`TELEGRAM` → `PENDING`, los demás → `CONFIRMED`).
- [x] 1.3 `CreateTransactionsPort.create` recibe el origen y `TransactionBatchValidator` toma de él
  el estado. El controlador de `/transactions` pasa `WEB`. Verificar:
  `CreateTransactionsUseCaseTest` (un lote `TELEGRAM` sale `PENDING`) y `TransactionControllerTest`.
- [x] 1.4 `TransactionR2dbcAdapter` escribe y lee `origin`, y `TransactionResponse` lo expone.
  Verificar: el request de 1.1 en verde y `bruno/pending/` con `origin` en la respuesta.

## 2. Configuración y errores

Skills: `java-exceptions`.

- [x] 2.1 `asistente.*` en el `application.yaml` de main y en el de test, con `GEMINI_API_KEY` y
  `GEMINI_MODEL` sin default. Verificar: `CloudRunConfigTest` con las dos claves entre las que no
  resuelven sin su variable.
- [x] 2.2 `ErrorCodes.EXTERNAL_SERVICE_ERROR`, lanzado como `BadRequestException` con 502 en `server`:
  el handler ya lo traduce, sin `case` nuevo. Verificar: el 502 de `GeminiAssistantAdapterTest` (3.3)
  y de `AssistantControllerTest` (5.2).

## 3. Puerto del modelo y sus adapters

Skills: `java-architect`, `java-exceptions`, `java-logging`.

- [x] 3.1 Puerto de salida `AssistantModelPort` con la decisión del modelo (una de las tres
  funciones con sus argumentos, o ninguna) y el contexto que recibe (hoy, cuentas y categorías).
  Verificar: compila; se prueba en 3.2 a 3.4.
- [x] 3.2 `src/main/resources/asistente/instrucciones.txt` y `funciones.json` con las reglas y
  funciones de `design.md`. Verificar: `GeminiAssistantAdapterTest` comprueba que el cuerpo enviado
  lleve la instrucción con la fecha y los nombres, las tres declaraciones y `mode` `AUTO`.
- [x] 3.3 `GeminiAssistantAdapter` sobre `WebClient`: la key en `x-goog-api-key`, timeout de 20 s,
  y la respuesta traducida a la decisión. Verificar: `GeminiAssistantAdapterTest` contra un
  `HttpServer` del JDK, que cubre la función con sus argumentos, la respuesta solo texto, una función
  desconocida, un 500, un timeout y un cuerpo ilegible (los tres últimos → `EXTERNAL_SERVICE_ERROR`).
  Un appender de prueba comprueba que el log no tenga la key, el prompt ni el cuerpo.
- [x] 3.4 `StubAssistantAdapter` (`@Profile("!prod")` y `asistente.proveedor=stub`) que entiende
  `<función> <json>`. Verificar: `StubAssistantAdapterTest`, y un test de contexto donde `prod` con
  `stub` no arranca.

## 4. Caso de uso del asistente

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 Resolución de nombres de cuenta y categoría (sin mayúsculas, tildes ni espacios en los
  extremos; ninguna o varias coincidencias → aclaración). Verificar: `ResolutorDeNombresTest`.
- [x] 4.2 `crear_movimiento`: valida el formato de `design.md` §5, arma el comando con la moneda de la
  cuenta y la fecha según §5, y crea con origen `TELEGRAM`. Un `BadRequestException` del alta se
  convierte en `NEEDS_CLARIFICATION`. Verificar: `AssistantUseCaseTest`, con gasto, ingreso,
  transferencia, cuenta inexistente, categoría ambigua, monto inválido, sin fecha y con fecha de otro
  día.
- [x] 4.3 `consultar_movimientos` y `consultar_saldo` sobre `GetTransactionReportPort` y
  `GetBalancePort`, con el mes en curso sin rango y un filtro sin resolver → aclaración. Verificar:
  `AssistantUseCaseTest`.
- [x] 4.4 Respuesta sin función o con una desconocida → `UNSUPPORTED`, sin tocar los otros puertos.
  Verificar: `AssistantUseCaseTest`.

## 5. Endpoint

Skills: `java-architect`, `java-exceptions`, `java-security`, `bruno-cli`.

- [x] 5.1 `bruno/assistant/`, escrita antes del endpoint y fallando. La carpeta registra su propio
  usuario `@bruno.local` y le crea cuentas y categorías: no depende de `test-data.sql` y se puede
  correr dos veces. Cubre:
  - 401 sin token y con la Basic;
  - 400 con `data` ausente, en blanco y de 1001 caracteres;
  - con el stub: gasto pendiente con `origin` `TELEGRAM` que aparece en `GET /transactions/pending`
    y no cambia el `currentBalance`; transferencia; cuenta inexistente; categoría ambigua; monto
    inválido; consulta de movimientos y de saldo, comparadas con `/reports/*`; texto no soportado;
  - la aprobación del pendiente creado, con `origin` `TELEGRAM`.
- [x] 5.2 `AssistantMessageRequest` (`@NotBlank`, `@Size(max = 1000)`), `AssistantMessageResponse`,
  el puerto de entrada y `AssistantController` en `/assistant/messages`. Verificar:
  `AssistantControllerTest` (validación, 401 y la forma de la respuesta por `intent`) y 5.1 en verde.
- [x] 5.3 `verificar-bruno.ps1` arranca la app con `ASISTENTE_PROVEEDOR=stub`. Verificar: 5.1 en
  verde desde el script.
- [x] 5.4 Replicar en `bruno-personal/` un request al asistente con un texto real (ruta, cuerpo y
  JWT), y el `origin` en los de movimientos si los comprueban. No se ejecuta: apunta a Neon.

## 6. Documentación

- [x] 6.1 `AGENTS.md`: `asistente.gemini.*` en la lista de `application-local.yaml`, y el stub en la
  sección de verificación.
- [x] 6.2 `docs/despliegue.md`: `GEMINI_API_KEY` (Secret Manager) y `GEMINI_MODEL` como variables del
  servicio, que tienen que existir antes de promover a `main`.

## 7. Verificación

- [x] 7.1 `gradlew build` en verde con los conteos reales.
  Resultado: 738 tests, 0 fallos, 0 omitidos; cobertura de línea 98,31 % (1392/1416). Corrido
  con la línea `spring.profiles.active` de git: el working tree tiene un default ajeno a la tarea que
  hace fallar `CloudRunConfigTest`; con autorización del usuario se apartó y se repuso.
- [x] 7.2 `verificar-bruno.ps1 -RecargarDatos` en verde con los conteos reales.
  Resultado: 312/312 requests, 254/254 tests, 700/700 aserciones, con `-Puerto 8081` (la app del
  usuario ocupaba el 8080). `application-local.yaml` no tiene todavía `asistente.gemini.*`: la corrida
  pasó `GEMINI_API_KEY` y `GEMINI_MODEL` como variables de la terminal, que el stub no usa.
- [x] 7.3 `openspec validate fa-77-asistente-ia --strict` en verde.

## Notas de implementación

- `WebClient.builder()` dentro del adapter y no el bean: en Boot 4 el builder autoconfigurado vive en
  `spring-boot-starter-webclient`, que no está, y para mandar un `Map` y leer un `String` no hace
  falta. Agregar el starter habría sido una dependencia nueva que el diseño descartaba.
- El 502 sale como `BadRequestException` con `field` `server`, como el 500 genérico, y el handler ya
  lo traduce: no hizo falta un `case` nuevo (ajuste al diseño hecho durante la implementación).
- `test-data.sql` siembra los pendientes de `pendientes@` con `origin` `TELEGRAM`, para que
  `bruno/pending/` verifique el origen en la lista y al aprobar.
- Las categorías de `bruno/assistant/` llevan icono y color: el alta de categorías los exige.
