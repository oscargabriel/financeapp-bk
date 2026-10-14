# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Carpeta `bruno/pending-batch/` (`seq` 19, auth Bearer heredada con `tokenLoteA`). Su
  `folder.yml` documenta el escenario: tres usuarios registrados en la corrida (`lote-a-`, `lote-b-` y
  `lote-c-<timestamp>@bruno.local`) y un correo sin cuenta (`lote-nadie-<timestamp>@bruno.local`). Se
  repite sin recargar datos. Requests, en orden:
  - alta y login de A, B y C; una cuenta COP con saldo para cada uno; categorías de cada uno;
  - A ↔ B y A ↔ C afiliados (A envía, el otro acepta);
  - B comparte «Cena» de 60000 COP con A (20000); C comparte «Taxi» de 30000 COP con A (10000) y
    «Mercado» de 45000 COP con A (15000); A comparte «Almuerzo» de 40000 COP con B (10000), para
    tener un pendiente ajeno (`idPendienteDeB`);
  - A registra un gasto en USD sobre su cuenta COP, que queda pendiente (`idPropioUsd`), y un gasto
    COP confirmado (`idConfirmadoA`); `pendientes-de-a.yml` guarda los ids de «Cena», «Taxi» y
    «Mercado» y comprueba que son cuatro;
  - `filtro-b.yml`: `?sharedBy=<correo de B>` da solo «Cena»; `filtro-c-mayusculas.yml`: el correo
    de C en mayúsculas y con espacios da «Taxi» y «Mercado», por `occurredAt` descendente;
    `filtro-sin-cuenta.yml` (correo sin cuenta) y `filtro-propio.yml` (correo de A): `[]`;
  - `filtro-vacio.yml` (`?sharedBy=`) y `filtro-sin-formato.yml` (`?sharedBy=laura`): 400
    `VALIDATION_ERROR` en `sharedBy`;
  - `lote-sin-ids.yml` (`{}`), `lote-vacio.yml` (`{"ids": []}`), `lote-101.yml` (101 UUID generados
    en el script) y `lote-repetido.yml` («Cena» dos veces, una en mayúsculas): 400 en `ids`;
  - `lote-mal-formado.yml` (`["<Cena>", "abc"]`): 400 en `ids[1]`; `lote-nulo.yml` (`[null]`): 400
    en `ids[0]`; `lote-no-json.yml`: 400 `JSON_PARSING_ERROR` en `body`;
  - `lote-ajeno.yml` (`["<Cena>", idPendienteDeB]`): 404 `NOT_FOUND` en `ids[1]`;
    `lote-inexistente.yml` (UUID nuevo, «Cena»): 404 en `ids[0]`; `lote-confirmado.yml`
    (`["<Cena>", idConfirmadoA]`): 409 `INVALID_STATE` en `ids[1]`; `lote-ajeno-y-confirmado.yml`
    (`[idPendienteDeB, idConfirmadoA]`): 404 con un solo error, en `ids[0]`;
  - `pendientes-sin-cambios.yml`: A sigue con sus cuatro pendientes; `pendientes-b-sin-cambios.yml`
    (B): sigue «Almuerzo»;
  - `lote-rechazar.yml` («Cena», «Taxi», `idPropioUsd`): 204; `pendientes-tras-lote.yml`: solo
    «Mercado»; `cuenta-a-tras-lote.yml`: el saldo de A no cambió;
  - `saldo-a-tras-lote.yml` (`/affiliations/balances`): B con COP `{10000, 0, 10000}` (solo queda
    «Almuerzo»; «Cena» dejó de ser deuda), C con COP `{0, 15000, -15000}`;
    `detalle-b-tras-lote.yml` (B, `/affiliations/{id}/shares`): «Cena» `REJECTED`;
  - `lote-repetir.yml` (el mismo lote): 404 en `ids[0]`, `ids[1]` e `ids[2]`;
  - A desvincula a C; `filtro-c-desvinculado.yml`: sigue «Mercado»;
  - `lote-sin-credencial.yml` y `lote-con-basic.yml`: 401 `UNAUTHENTICATED` en `authorization`,
    `WWW-Authenticate: Bearer`.
- [x] 1.2 Comentario de `docs/database/test-data.sql` que lista los prefijos de `@bruno.local`:
  agregar `pending-batch/` con `lote-*-<timestamp>@bruno.local`.
- [x] 1.3 Verificar que fallan por la razón correcta: `verificar-bruno.ps1 -Objetivo pending-batch`
  con la app de `dev`. Se espera que los filtros devuelvan la lista completa, que el lote responda
  400 en `id` (hoy `pending` cae en `/{id}/reject`) y que los 401 pasen. Anotar aquí el conteo.

  52 requests: 29 pasan y 23 fallan; 5 de 13 tests y 69 de 101 asserts. Pasan el montaje (altas,
  cuentas, afiliaciones, gastos compartidos, el pendiente en dólares y el confirmado), las
  comprobaciones que no dependen del cambio y los dos 401. Los seis filtros responden 200 con la
  lista completa, y los doce lotes, 400 `VALIDATION_ERROR` en `id`: `pending` cae en `/{id}/reject`.

## 2. Puertos

Skills: `java-architect`.

- [x] 2.1 `ListPendingTransactionsPort.listPending(UUID userId, Optional<String> sharedBy)`; puerto de
  entrada nuevo `RejectPendingBatchPort.reject(UUID userId, List<UUID> ids)`.
- [x] 2.2 `TransactionRepositoryPort`: `findPendingSharedBy(UUID userId, String email)`,
  `findByIdsAndUser(List<UUID> ids, UUID userId)` y `deleteAllPending(List<UUID> ids, UUID userId)`
  (`Mono<Boolean>`, todo o nada). Se verifica al compilar los grupos 3 a 5.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `ListPendingTransactionsUseCase`: sin filtro llama a `findPendingByUser`; con filtro,
  normaliza (`trim`, minúsculas) y llama a `findPendingSharedBy`. `ListPendingTransactionsUseCaseTest`.
- [x] 3.2 `RejectPendingBatchUseCase`: lee con `findByIdsAndUser`; 404 `NOT_FOUND` en `ids[i]`
  por cada id ausente; si no hay, 409 `INVALID_STATE` en `ids[i]` por cada confirmado; sin errores,
  `deleteAllPending`, y `false` es 404 en `ids`. `RejectPendingBatchUseCaseTest`: todos
  pendientes, uno ajeno, uno confirmado, los dos juntos (solo 404), carrera, y que con error nunca
  llama a `deleteAllPending`.

## 4. Persistencia

Skills: `java-architect`.

- [x] 4.1 `TransactionR2dbcAdapter`: `PENDIENTES_DE` (los pendientes con `JOIN finance.users` por
  `shared_by_user_id` y `email = :email`, mismo orden), `POR_IDS` y `deleteAllPending`, que reutiliza
  `BORRAR_PENDIENTES` y `conPartes` y deshace si borra menos filas que ids. Lo verifica
  `bruno/pending-batch/` (la suite no tiene base).

## 5. Web

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `RejectPendingBatchRequest` (`dto/`) con `@NotNull @Size(min = 1, max = 100)` en `ids`,
  `@NotBlank` y `Formatos.UUID` (patrón nuevo) por elemento, y la constraint `@SinRepetidos` en
  `dto/validation/`. `RejectPendingBatchRequestTest` con `Violaciones`.
- [x] 5.2 `TransactionController`: `@RequestParam(required = false) String sharedBy` en `/pending`,
  validado a mano (vacío, más de 255, fuera de `Formatos.EMAIL`: 400 en `sharedBy`), y
  `POST /pending/reject` con `@Valid @RequestBody`, 204. `TransactionControllerTest`: filtro presente
  y ausente, los dos 400 del filtro, 204 del lote, los 400 del cuerpo con `ids` e `ids[i]`, el
  `JSON_PARSING_ERROR`, y el 404 y el 409 del caso de uso.
- [x] 5.3 Los 401 sin credencial y con Basic del lote en `TransactionControllerTest` o `JwtSecurityIT`,
  donde estén los del rechazo individual: resultaron estar en `TransactionsIT`, que suma la ruta a sus
  dos `@CsvSource`.

## 6. Contrato y documentación

- [x] 6.1 `docs/api/contrato-api.md`, «Movimientos pendientes»: «Los cinco endpoints» pasa a seis;
  `GET /api/transactions/pending` documenta `sharedBy` y su 400; sección del rechazo en lote con
  cuerpo, tope, todo o nada, efecto sobre las partes y tabla de errores. «Gasto compartido» nombra el
  lote junto al rechazo individual y al descarte de grupo. «Lo que el API todavía no tiene» deja solo
  **aprobar** una selección de pendientes de grupos distintos.

## 7. bruno-personal

Skills: `bruno-cli`.

- [x] 7.1 Replicar en `bruno-personal/` un request del filtro (`GET /pending?sharedBy=`) y uno del
  rechazo en lote, con la misma ruta, cuerpo y autenticación. Sin ejecutarlos: apuntan a Neon.

## 8. Verificación

- [x] 8.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura.

  BUILD SUCCESSFUL: 1463 tests, 0 fallidos, 0 omitidos; cobertura de línea 98,54 % (umbral 85 %).
- [x] 8.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el
  conteo de requests, tests y asserts, y `check-saldos.sql` sin cuentas descuadradas.

  934/934 requests, 642/642 tests y 1825/1825 asserts, sin omitidos; las 71 cuentas cuadran.
