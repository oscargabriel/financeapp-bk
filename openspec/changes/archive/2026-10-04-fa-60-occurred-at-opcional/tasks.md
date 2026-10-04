# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Escribir `bruno/transactions/lote-sin-fecha.yml` (seq 16, después de `lote-sin-credenciales`):
  un lote con cuatro elementos válidos (sin `occurredAt`, con `null`, con `""` y con
  `"2026-09-20T10:15:00-05:00"`). Asserts: 201; los tres primeros con el mismo `occurredAt`, dentro
  de ±60 s de la hora de envío; el cuarto con `2026-09-20T15:15:00Z`. Verificar que hoy **falla con
  400** (`bru run transactions -r --env local` desde `bruno/`).
- [x] 1.2 Escribir `bruno/transactions/lote-con-basic.yml` (seq 17): el Basic de `/auth/*` contra
  `POST /api/transactions` devuelve 401 `UNAUTHENTICATED` con `WWW-Authenticate: Bearer`. Cubre un
  escenario de la spec que hoy no prueba nada; pasa desde el inicio, no hay RED.

## 2. Request sin fecha obligatoria

Skills: `java-architect`.

- [x] 2.1 En `CreateTransactionRequestTest`, cambiar el caso "sin fecha", que esperaba
  "La fecha es obligatoria", por un caso parametrizado: `null`, `""` y `"   "` no producen
  violaciones. Verlo fallar.
- [x] 2.2 Quitar `@NotBlank` de `occurredAt` en `CreateTransactionRequest` y actualizar el javadoc de
  `@FechaConOffset` si menciona que el campo es obligatorio. Verificar con `CreateTransactionRequestTest`
  en verde: los casos de fecha sin offset y mal formada siguen dando violación.
- [x] 2.3 En `TransactionControllerTest`, agregar el caso de un elemento sin `occurredAt`: llega al
  puerto y responde 201, no 400. Verificar con la clase en verde.

## 3. Instante del lote en el caso de uso

Skills: `java-architect`.

- [x] 3.1 En `CreateTransactionsUseCaseTest`, con el `RELOJ` fijo, agregar estas pruebas: un
  elemento sin fecha guarda `RELOJ.instant()`; tres elementos sin fecha guardan el mismo instante; un
  lote mixto conserva la fecha explícita. Verlas fallar por `NullPointerException` en el parseo.
- [x] 3.2 `CreateTransactionsUseCase` lee `clock.instant()` una vez por lote y se lo pasa a
  `TransactionBatchValidator`, que lo usa cuando `occurredAt` está en blanco. Verificar con
  `CreateTransactionsUseCaseTest` en verde.

## 4. Contrato y colección personal

- [x] 4.1 `docs/api/contrato-api.md`: `occurredAt` pasa a opcional, con la regla del instante de la
  petición compartido por el lote. Verificar leyendo la tabla y el ejemplo.
- [x] 4.2 Replicar en `bruno-personal/movimientos/` un request de alta sin `occurredAt`. No se
  ejecuta: la colección no tiene tests.

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 5.2 Con `test-data.sql` recargado y la app levantada con el perfil `local`, correr
  `bru run . -r --env local` desde `bruno/` en verde, con requests, tests y aserciones reales.
  Incluye `lote-sin-fecha` en verde.
