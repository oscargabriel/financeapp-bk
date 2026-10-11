# Tasks

## 1. Datos y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 En `docs/database/test-data.sql`, sección de series, sembrar para `series@` tres series
  semanales sin fin de 10.000, del día de la semana de hoy, que empezaron hace 28 días, con
  `generated_count` 1 y solo su primera ocurrencia creada:
  - `serie_desactivada` (`80000000-…-002`) sobre la cuenta `Serie desactivada` (`20000000-…-016`):
    `CASH`, 1.000.000, `is_active = false`;
  - `serie_cuenta_borrada` (`80000000-…-003`) sobre la cuenta `Serie cuenta borrada`
    (`20000000-…-017`): `CASH`, 10.000, con `deleted_at`. Queda en saldo 0;
  - `serie_sin_categoria` (`80000000-…-004`) sobre la cuenta `Serie sin categoria`
    (`20000000-…-018`): `CASH`, 1.000.000, activa. Usa la categoría `Club viejo`
    (`30000000-…-171`), `EXPENSE`, con `deleted_at`.

  Actualizar el comentario de saldos de la sección y los `docs` de `bruno/recurrences/folder.yml`.
  En `bruno/recurrences/login.yml`, guardar los ids nuevos como variables. Verificar cargando el
  script con psql sin errores y con `docs/database/check-saldos.sql` sin cuentas descuadradas.
- [x] 1.2 Al final de `bruno/recurrences/` (seq 74 en adelante), con `tokenSeries`, crear en orden:
  - `huerfanas-tras-leer`: `GET /api/reports/transactions` del rango de la carpeta, después de que
    las lecturas anteriores ya pusieron al día. Cada una de las tres series sembradas tiene una
    sola ocurrencia.
  - `cuentas-de-huerfanas`: `GET /api/accounts?includeInactive=true`. `Serie desactivada` y
    `Serie sin categoria` siguen en 990.000.
  - `listar-sin-huerfanas`: `GET /api/recurrences` no trae ninguna de las tres.
  - `crear-cuenta-desactivable`: `CASH` `Desactivable` en COP, sin saldo. Guarda
    `idDesactivable`.
  - `crear-categoria-club`: `Club`, `EXPENSE`. Guarda `idClub`.
  - `alta-serie-desactivable`: serie `MONTHLY` sin fin de 50.000 sobre `Desactivable` y `Club`, con
    `startDate` dentro de diez días. Guarda `idSerieDesactivable`.
  - `desactivar-cuenta-con-serie`: `PATCH` de `Desactivable` con `{"name": "Vieja", "isActive": false}`.
    Responde 409 con un solo error, `RESOURCE_IN_USE` en `isActive`, que dice «1 serie activa».
  - `cuenta-con-serie-sigue-activa`: `GET /api/accounts` trae `Desactivable` con su nombre.
  - `borrar-categoria-con-serie`: `DELETE /api/categories/{{idClub}}`. Responde 409 con
    `RESOURCE_IN_USE` en `id`.
  - `borrar-cuenta-con-serie-y-programado`: `DELETE` de `Desactivable`. Responde 409 con un solo
    error en `id`, que dice «1 serie activa» y «1 movimiento programado».
  - `quitar-programada-desactivable`: borra con `DELETE /api/transactions/{id}` la ocurrencia
    programada de la serie. Su id se busca en `reports/transactions` por `recurrenceId`. Responde 204.
  - `borrar-cuenta-solo-con-serie`: `DELETE` de `Desactivable`. Responde 409 con un solo error en
    `id`, que dice «1 serie activa» y no menciona programados.
  - `cancelar-serie-desactivable`: `DELETE /api/recurrences/{{idSerieDesactivable}}`. Responde 204.
  - `desactivar-tras-cancelar`: `PATCH` con `{"isActive": false}`. Responde 200 con `isActive` en
    `false`.
  - `borrar-categoria-tras-cancelar`: `DELETE` de `Club`. Responde 204.
  - `borrar-cuenta-tras-cancelar`: `DELETE` de `Desactivable`. Responde 204.
- [x] 1.3 En `bruno/recurrences/borrar-cuenta-con-serie.yml`, ajustar el test para que el único error
  en `id` mencione la serie activa, y actualizar sus `docs` a FA-116.

  Verificar con `verificar-bruno.ps1 -RecargarDatos` que los requests nuevos fallan hoy por la razón
  esperada: hoy se crean las ocurrencias de las tres series, aparecen en el listado y los 409 dan 200
  o 204. Los requests de desactivar y borrar que ya existían siguen como están.

## 2. Puerto y casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 Agregar a `RecurrenceRepositoryPort` los métodos `countActiveByAccount(UUID accountId,
  UUID userId)` y `countActiveByCategory(UUID categoryId, UUID userId)`, los dos con `Mono<Long>`.
  Ajustar los mocks que haga falta. Verificar con `.\gradlew.bat test` en verde, todavía sin
  comportamiento nuevo.
- [x] 2.2 `UpdateAccountUseCaseTest` en RED:
  - `isActive: false` sobre una cuenta activa con 1 serie da 409 `RESOURCE_IN_USE` en `isActive`
    con «1» en la `description`, sin llamar a `update`;
  - con 0 series desactiva, como hoy;
  - sobre una cuenta ya desactivada no consulta las series;
  - un parche sin `isActive`, o con `true`, no consulta las series.

  Inyectar `RecurrenceRepositoryPort` en `UpdateAccountUseCase` e implementar. Verificar con la
  clase en verde.
- [x] 2.3 `DeleteAccountUseCaseTest` en RED:
  - en cero, con 1 serie y 1 programado, da un solo error en `id` que menciona los dos;
  - con 1 serie y 0 programados, da el error en `id` solo con la serie;
  - con 0 series y 1 programado, el mensaje de FA-115 sigue igual;
  - con saldo y una serie, da el error de `currentBalance` y el de `id`;
  - en cero, sin series ni programados, borra.

  Implementar en `DeleteAccountUseCase`. Verificar con la clase en verde.
- [x] 2.4 `DeleteCategoryUseCaseTest` en RED:
  - con 1 serie da 409 `RESOURCE_IN_USE` en `id`, sin llamar a `softDelete`;
  - con 0 series borra como hoy, y `softDelete` en `false` sigue dando 404.

  Inyectar `RecurrenceRepositoryPort` en `DeleteCategoryUseCase` e implementar. Verificar con la
  clase en verde.

## 3. Persistencia

- [x] 3.1 En `RecurrenceR2dbcAdapter`, con bind variables:
  - extraer la condición de serie activa a un fragmento compartido: `status = 'ACTIVE'` y, además,
    una ocurrencia posterior a `now()`, o sin fin con la cuenta activa y no borrada y la categoría
    no borrada;
  - usarlo en `ACTIVAS`;
  - implementar los dos conteos: por cuenta, y por categoría no borrada, filtrados por `user_id`;
  - agregar a `SIN_FIN` el join con `accounts` y `categories` que excluye cuenta desactivada o
    borrada y categoría borrada.

  No lleva test de suite, porque el adapter está excluido de la cobertura. Lo verifican en verde los
  requests del grupo 1.

## 4. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 4.1 En `docs/api/contrato-api.md`:
  - `PATCH /api/accounts/{id}`: el 409 en `isActive`, en el párrafo de desactivar y en la tabla;
  - `DELETE /api/accounts/{id}`: las series activas en el 409 de `id`;
  - `DELETE /api/categories/{id}`: el 409 nuevo, en las viñetas y en la tabla;
  - *Series recurrentes*: qué pasa con las series sin fin sobre una cuenta o categoría no válida,
    y la definición de activa en `GET /api/recurrences`.

  Verificar leyendo las secciones.
- [x] 4.2 En `bruno-personal/cuentas/desactivar.yml`, `bruno-personal/cuentas/borrar.yml` y
  `bruno-personal/categorias/borrar.yml`, agregar a `docs` el 409 por series activas y cómo
  resolverlo. La ruta, el cuerpo y la autenticación no cambian: comparar con
  `bruno/accounts/desactivar-cuenta.yml`, `bruno/accounts/borrar-cuenta.yml` y
  `bruno/categories/borrar-categoria.yml`. No se ejecuta.

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones, y `check-saldos.sql` sin cuentas descuadradas.
