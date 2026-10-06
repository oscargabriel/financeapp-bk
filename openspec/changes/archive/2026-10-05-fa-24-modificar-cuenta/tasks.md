# Tasks

## 1. Esquema, escenario y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`: función `finance.shift_account_balance()` y trigger
  `trg_accounts_shift_balance` (`BEFORE UPDATE OF initial_balance`, `WHEN` distinto), y el comentario
  de `initial_balance` actualizado. Emitir lo mismo en
  `docs/database/update/20261005_02_cuentas_saldo_inicial_editable.sql` con la reversión comentada
  al final. Aplicarlo a la base local con psql y correr la comparación de esquemas de
  `docs/database/modelo-datos.md`: tiene que salir limpia.
- [x] 1.2 `docs/database/test-data.sql`: dar a `Davivienda` el id fijo
  `20000000-0000-7000-8000-000000000006` con un `\set`. Verificar que la colección sigue en verde
  contra la app de `dev` con `verificar-bruno.ps1 -RecargarDatos`.
- [x] 1.3 Escribir en `bruno/accounts/`, a partir de seq 16, con `token: {{tokenCuentas}}` salvo
  donde se indica:
  - `modificar-cuenta`: `idCuentaEfectivo` con `{"name": " Bolsillo ", "initialBalance": 200000}` →
    200, `Bolsillo`, `initialBalance` y `currentBalance` 200000, mismo `id`, `type` `CASH`, `COP`, y
    los cuatro campos de crédito en `null`.
  - `modificar-cuenta-credito`: `idCuentaCredito` con `{"creditLimit": 4000000, "statementDay": 25, "paymentDueDay": 10}`
    → 200, `availableCredit` 3800000, `initialBalance` -200000.
  - `modificar-cuenta-campo-en-null`: `{"paymentDueDay": 12, "creditLimit": null}` → 200,
    `creditLimit` sigue 4000000.
  - `modificar-moneda-sin-movimientos`: `idCuentaCredito` con `{"currencyCode": "usd"}` → 200 `USD`.
  - `modificar-moneda-inexistente`: `{"currencyCode": "XYZ"}` → 400 `VALIDATION_ERROR` en `currencyCode`.
  - `categorias-usuario-cuentas` (GET `/categories?appliesTo=EXPENSE`): guarda el id de `Mercado`
    en `idMercadoCuentas`.
  - `gasto-en-bolsillo` (POST `/transactions`): un gasto de 50000 en `idCuentaEfectivo` con esa
    categoría → 201.
  - `modificar-saldo-inicial-con-movimientos`: `{"initialBalance": 300000}` → 200, `currentBalance`
    250000.
  - `modificar-moneda-con-movimientos`: `{"currencyCode": "USD", "name": "Otro"}` → 409
    `RESOURCE_IN_USE` en `currencyCode`.
  - `modificar-misma-moneda-con-movimientos`: `{"currencyCode": "cop"}` → 200 `COP`, `name` sigue
    `Bolsillo`.
  - `modificar-cuenta-campos-credito-en-efectivo`: `{"creditLimit": 1000000, "statementDay": 3}` →
    400 en `creditLimit` y `statementDay`.
  - `modificar-cuenta-campos-invalidos`: `{"name": " ", "currencyCode": "us", "initialBalance": 1.23456, "creditLimit": 0, "statementDay": 32, "paymentDueDay": 0}`
    → 400 con los seis campos, comparados por contenido.
  - `modificar-cuenta-campos-del-sistema`: `{"currentBalance": 1, "type": "CASH", "isActive": false}`
    → 400 en los tres.
  - `modificar-cuenta-parche-vacio`: `{}` → 400 en `body`.
  - `modificar-cuenta-nombre-repetido`: `{"name": "MASTERCARD"}` → 409 `DUPLICATE_RESOURCE` en `name`.
  - `modificar-cuenta-mismo-nombre`: `{"name": "BOLSILLO"}` → 200 con `BOLSILLO`.
  - `cuentas-tras-modificar` (GET `?includeInactive=true`): `BOLSILLO` con `initialBalance` 300000,
    `currentBalance` 250000 y `COP`; `Mastercard` con `USD`, `creditLimit` 4000000, `statementDay`
    25 y `paymentDueDay` 12.
  - `modificar-cuenta-id-mal-formado`: `PATCH /accounts/abc` → 400 en `id`.
  - `modificar-cuenta-inexistente`: un UUID v7 que no existe → 404 `NOT_FOUND` en `id`.
  - `modificar-cuenta-borrada`: con `{{accessToken}}`, la de Davivienda → 404 en `id`.
  - `modificar-cuenta-desactivada`: con `{{accessToken}}`, Nequi
    (`20000000-0000-7000-8000-000000000005`) con `{"name": "Nequi"}` → 200, `isActive` `false`.
  - `modificar-cuenta-ajena`: Efectivo del escenario (`20000000-0000-7000-8000-000000000001`) con
    `{"name": "Robada"}` → 404 en `id`.
  - `cuenta-ajena-intacta` (GET con `{{accessToken}}`): sigue `Efectivo`, no hay `Robada`.
  - `modificar-cuenta-sin-credenciales` (sin `auth`) y `modificar-cuenta-con-basic` → 401
    `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Agregar a `crear-cuenta-credito.yml` y `listar-cuentas.yml` las aserciones de los campos nuevos de
  la respuesta (`initialBalance`, `creditLimit`, `statementDay`, `paymentDueDay`; en `null` para una
  `CASH`). Verificar que los nuevos y esas aserciones hoy fallan con `verificar-bruno.ps1 -RecargarDatos`.

## 2. Dominio y puertos

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 `Account` gana `initialBalance`, `statementDay` y `paymentDueDay`; ajustar
  `AccountMother`, `TransactionMother` y `AccountTest`. Crear `UpdateAccountCommand` y
  `UpdateAccountPort`; sumar a `AccountRepositoryPort` `findActiveByIdAndUser`, `hasTransactions` y
  `update`. Verificar con `.\gradlew.bat test` en verde (sin comportamiento nuevo todavía).

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `UpdateAccountUseCaseTest` en RED, con `StepVerifier` y los puertos mockeados: parche de
  nombre y saldo inicial normalizados conserva el resto; `null` no cambia; cuenta no encontrada da
  404 `NOT_FOUND` en `id` sin `update`; campos de crédito sobre una `CASH` dan 400 con un error por
  campo sin consultar la base; campos de crédito sobre una `CREDIT` se aplican; moneda distinta e
  inactiva da 400 en `currencyCode` sin consultar movimientos; moneda distinta con movimientos da
  409 `RESOURCE_IN_USE` en `currencyCode` sin `update`; moneda distinta sin movimientos se aplica en
  mayúsculas; la misma moneda en otra caja no consulta catálogo ni movimientos; el 409 de nombre del
  puerto se propaga; `update` vacío da 404.
- [x] 3.2 Implementar `UpdateAccountUseCase`. Verificar con `UpdateAccountUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `UpdateAccountRequestTest` en RED: todo opcional; `name` y `currencyCode` en blanco dan
  violación en su campo; nombre de 81, moneda `us`, saldo de 5 decimales, cupo 0, días 0 y 32 también;
  `currentBalance`, `type` e `isActive` con valor dan violación en su campo; `sinCambios()` con todo
  `null`. Implementar `UpdateAccountRequest`. Verificar con la clase en verde.
- [x] 4.2 `AccountResponse` con `initialBalance`, `creditLimit`, `statementDay` y `paymentDueDay`;
  `AccountControllerTest` en RED para esos campos en el listado. Verificar con la clase en verde.
- [x] 4.3 `AccountControllerTest` en RED: PATCH válido llega al puerto con el usuario del token, el
  id y el cuerpo, y responde 200; parche vacío 400 en `body` sin llamar al puerto; id mal formado
  400 en `id`; el 404, el 400 de crédito y los dos 409 del puerto se propagan con su código y campo.
  Implementar el `@PatchMapping` en `AccountController`. Verificar con la clase en verde.
- [x] 4.4 `AccountsIT`: PATCH sin credencial y con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 `AccountR2dbcAdapter`: el listado y el `RETURNING` del alta devuelven `initial_balance`,
  `statement_day` y `payment_due_day`; el `SELECT` por id y usuario entre las no borradas; el
  `EXISTS` sobre `finance.transactions` por `account_id` o `destination_account_id`; el `UPDATE ...
  RETURNING` de `name`, `currency_code`, `initial_balance`, `credit_limit`, `statement_day` y
  `payment_due_day` filtrado por `id`, `user_id` y `deleted_at IS NULL`, con el choque de nombre
  traducido a 409. Todo con bind variables. Sin test de suite (adapter excluido de cobertura): lo
  verifican los requests del grupo 1.3 en verde.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `PATCH /api/accounts/{id}` en el índice y su sección (cuerpo,
  semántica de `null`, campos rechazados, crédito según el tipo, saldo inicial que corre el vigente,
  moneda con movimientos, 404 y los dos 409); los campos nuevos en la respuesta de `GET` y `POST`; y
  quitar "editar cuentas" de lo que el API todavía no tiene, dejando "borrar cuentas". Verificar
  leyendo las secciones.
- [x] 6.2 `bruno-personal/cuentas/modificar.yml`: el PATCH con valores de ejemplo y `docs` con la
  regla de `null`, los campos rechazados y los 409. Comparar ruta, cuerpo y autenticación con
  `bruno/accounts/modificar-cuenta.yml`. No se ejecuta.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
