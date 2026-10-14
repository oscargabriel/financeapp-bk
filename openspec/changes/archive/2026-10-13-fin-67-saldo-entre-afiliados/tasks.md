# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Carpeta `bruno/affiliate-balances/` (`seq` 18, auth Bearer heredada con `tokenSaldoA`). Su
  `folder.yml` documenta el escenario: tres usuarios registrados en la corrida (`saldos-a-`,
  `saldos-b-` y `saldos-c-<timestamp>@bruno.local`) y un correo sin cuenta
  (`saldos-nadie-<timestamp>@bruno.local`). Se repite sin recargar datos. Requests, en orden:
  - alta y login de A, B y C; una cuenta COP con saldo para cada uno; categorías de cada uno;
  - A ↔ B, A ↔ C y B ↔ C afiliados (A envía, el otro acepta; B envía a C); A deja una solicitud sin
    aceptar al correo sin cuenta (`idSaldoSinAceptar`);
  - `saldo-vacio.yml` (A): las dos personas, C primero, con `"status": "ACCEPTED"` y
    `"balances": []`; comprueba las claves de cada entrada;
  - A comparte «Cena» de 90000 COP con B (30000) y C (20000), y «Hotel» de 50 USD con B (20);
  - B comparte «Taxi» de 40000 COP con A (10000) y con C (5000); A hace una transferencia compartida
    a B de 7000;
  - `saldo-a.yml` (A): B con COP `{30000, 10000, 20000}` y USD `{20, 0, 20}`; C con COP
    `{20000, 0, 20000}`; la transferencia y la parte B → C no suman;
  - `saldo-b.yml` (B): A con COP `{10000, 30000, -20000}` y USD `{0, 20, -20}`; C con COP
    `{5000, 0, 5000}`;
  - C rechaza su parte de «Cena» (busca su pendiente por descripción); `saldo-a-tras-rechazo.yml`:
    C con `"balances": []`;
  - B paga su parte de «Cena»; `saldo-a-tras-pago.yml`: B con COP `{0, 10000, -10000}` y USD igual;
  - A comparte «Almuerzo» de 30000 COP con B (12000) y lo borra; B ve esa parte `VOIDED`;
  - `detalle-a-b.yml` (A): «Almuerzo» `VOIDED`, «Taxi» `PENDING` `I_OWE`, «Hotel» `PENDING`
    `OWED_TO_ME` en USD con `resolvedAt` null, «Cena» `PAID` `OWED_TO_ME` con `resolvedAt`; ninguna de
    B con C; comprueba las claves de cada parte;
  - `detalle-b-a.yml` (B, con `idAfAB`): las mismas cuatro con `direction` invertida;
  - `detalle-a-c.yml` (A): «Cena» `REJECTED` `OWED_TO_ME`;
  - A comparte «Mercado» de 30000 COP con C (15000) y desvincula a C; `saldo-a-desvinculado.yml`: C
    con `"status": "UNLINKED"`, el `idAfAC` y COP `{15000, 0, 15000}`; `saldo-c-desvinculado.yml`
    (C): A `UNLINKED` con `net` -15000;
  - `detalle-a-c-desvinculado.yml` (A, con `idAfAC`): «Mercado» `PENDING` y «Cena» `REJECTED`;
  - A envía otra solicitud a C, C la acepta (`idAfAC2`); `saldo-a-reafiliado.yml`: C una sola vez,
    `ACCEPTED`, con `idAfAC2` y la parte de «Mercado»; `detalle-con-la-vieja.yml` (A, con `idAfAC`):
    200 con las mismas partes;
  - A desvincula a C otra vez; C paga «Mercado»; `saldo-a-sin-c.yml`: C ya no sale;
  - `detalle-sin-aceptar.yml` (A, `idSaldoSinAceptar`): 409 `INVALID_STATE` en `status`;
  - `detalle-ajeno.yml` (A, la afiliación B ↔ C): 404 `NOT_FOUND` en `id`;
    `detalle-inexistente.yml`: 404 en `id`; `detalle-id-mal-formado.yml`: 400 `VALIDATION_ERROR` en
    `id`;
  - `saldo-sin-credencial.yml`, `detalle-sin-credencial.yml`, `saldo-con-basic.yml` y
    `detalle-con-basic.yml`: 401 `UNAUTHENTICATED` en `authorization`, `WWW-Authenticate: Bearer`.
- [x] 1.2 Comentario de `docs/database/test-data.sql` que lista los prefijos de `@bruno.local`:
  agregar `affiliate-balances/` con `saldos-*-<timestamp>@bruno.local`, y `shares/` con
  `compartidos-*-<timestamp>@bruno.local`, que FIN-66 no anotó.
- [x] 1.3 Verificar que fallan por la razón correcta: `verificar-bruno.ps1 -Objetivo
  affiliate-balances` con la app de `dev`. Se espera 404 o 405 de ruta inexistente en el saldo y el
  detalle, y el 401 en los de credencial. Anotar aquí el conteo.

  58 requests: 40 pasan y 18 fallan; 4 de 32 tests y 60 de 82 asserts. Pasan el montaje (altas,
  cuentas, categorías, afiliaciones, gastos, pagos, rechazos y borrado) y los cuatro 401. Los 18 de
  saldo y detalle responden 404 `NOT_FOUND` en `request`, de ruta inexistente.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 Records `CurrencyBalance` (currencyCode, owedToMe, iOwe, con `net()`), `AffiliateBalance`
  (affiliationId, status, person `Sharing.Person`, balances) y `ShareWithAffiliate` (id, description,
  occurredAt, amount, currencyCode, status, resolvedAt, direction), y el enum `ShareDirection`
  (`OWED_TO_ME`, `I_OWE`). Verifica `CurrencyBalanceTest` (`net` positivo, negativo y cero).
- [x] 2.2 Puertos de entrada `ListAffiliateBalancesPort` y `ListSharesWithAffiliatePort`, y de salida
  `AffiliateBalanceQueryPort` (`findBalances(userId)`, `findSharesBetween(userId, otherUserId)`).
  Sin test propio: los cubren los de 3.x.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `ListAffiliateBalancesUseCase`: delega en `findBalances`. Verifica
  `ListAffiliateBalancesUseCaseTest`.
- [x] 3.2 `ListSharesWithAffiliateUseCase`: lee la afiliación con `findForParticipant`; vacía → 404
  `NOT_FOUND` en `id`; `REQUESTED`, `REJECTED` o `CANCELLED` → 409 `INVALID_STATE` en `status`;
  `ACCEPTED` o `UNLINKED` → partes entre el usuario y la otra persona, sea él remitente o
  destinatario. Verifica `ListSharesWithAffiliateUseCaseTest` (un test por camino, y los dos papeles
  del usuario).

## 4. Web

Skills: `java-architect`, `java-security`.

- [x] 4.1 `GET /affiliations/balances` y `GET /affiliations/{id}/shares` en `AffiliationController`,
  con `AffiliateBalanceResponse` y `ShareWithAffiliateResponse`; el id se parsea con el `parseId`
  existente. Verifica `AffiliationControllerTest`: la forma del JSON de los dos (claves, `net`,
  `direction`, `resolvedAt` null), `[]`, el 400 del id mal formado sin llamar al puerto, y que el
  usuario sale del token.
- [x] 4.2 `AffiliationsIT`: las dos rutas en los parametrizados sin credencial y con Basic (401,
  `WWW-Authenticate: Bearer`); actualizar el comentario de la clase («las seis rutas»).

## 5. Persistencia

Skills: `java-architect`.

- [x] 5.1 `AffiliateBalanceR2dbcAdapter`: la consulta del saldo (decisión 5 del `design.md`) agrupada
  por persona en orden, y la del detalle entre dos usuarios por `occurred_at DESC, id DESC`. La
  verifican los requests de saldo y detalle de `bruno/affiliate-balances/`, que tienen que pasar a
  verde con `verificar-bruno.ps1 -RecargarDatos -Objetivo affiliate-balances`.

## 6. Contrato y documentación

- [x] 6.1 `docs/api/contrato-api.md`, sección Afiliaciones: las dos rutas en la tabla («ocho rutas»),
  sus respuestas con ejemplo, qué personas salen en el saldo, cómo se calcula, y el 409 del detalle
  en la tabla de errores. Quitar de «Lo que el API todavía no tiene» la línea del saldo y el detalle.
  Verifica: releer contra la spec delta.
- [x] 6.2 `docs/database/modelo-datos.md`, sección Finanzas compartidas: la línea «La deuda entre dos
  personas…» cita las dos rutas. Verifica: releer.
- [x] 6.3 `bruno-personal/afiliados/`: `saldo.yml` y `detalle.yml` (Bearer heredado, el detalle con
  un id de ejemplo), sin ejecutarlos. Verifica: comparar ruta y autenticación con `bruno/`.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea.

  BUILD SUCCESSFUL: 1431 tests, 0 fallos, 0 errores, 0 omitidos; 98,52 % de línea en el reporte de
  JaCoCo (2864 cubiertas, 43 perdidas).
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo de requests y asserts, y sin cuentas descuadradas.

  PASS: 882 requests, 880 pasan y 2 omitidos; 629/629 tests y 1716/1716 asserts; las 68 cuentas
  cuadran. Los dos omitidos son `assistant/confirmar-categoria-borrada.yml` y
  `assistant/grupo-sin-saldo-confirmar.yml`, que Bruno descarta por un `value: [0].categoryId` sin
  comillas desde FIN-58: no son de este change y van a un issue aparte.
