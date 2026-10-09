# Tasks

## 1. Esquema, datos y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`: columna `monthly_interest_rate NUMERIC(6,4)` en
  `finance.accounts`, `ck_accounts_monthly_interest_rate` (`IS NULL OR BETWEEN 0 AND 10`),
  `ck_accounts_credit_fields` con la columna nueva entre las que van en `null` fuera de `CREDIT`, y un
  `COMMENT ON COLUMN` que diga la unidad (porcentaje mensual). Emitir lo mismo en
  `docs/database/update/20261009_02_cuentas_tasa_interes.sql`, con encabezado (va ANTES de desplegar la
  app; en local hay que recargar los datos con `cargar-datos-local.sql`) y la reversión comentada al
  final. Documentar la columna en `docs/database/modelo-datos.md` (diagrama y párrafo de los campos de
  crédito). Aplicarlo a la base local con psql y correr la comparación de esquemas de
  `modelo-datos.md`: tiene que salir limpia.
- [x] 1.2 `docs/database/test-data.sql`: la tarjeta `Visa` del escenario con `monthly_interest_rate`
  2.1. `docs/database/demo-data.sql`: la `Mastercard Oro` con 1.89. Recargar con
  `cargar-datos-local.sql` y verificar que la colección sigue en verde contra la app de `dev` con
  `verificar-bruno.ps1 -RecargarDatos`.
- [x] 1.3 En `bruno/accounts/`:
  - `crear-cuenta-credito.yml`: el cuerpo lleva `"monthlyInterestRate": 2.15`, y la respuesta la trae
    en 2.15.
  - `listar-cuentas.yml`: `Visa` con `monthlyInterestRate` 2.1, y la `CASH` con `null`.
  - `modificar-cuenta-credito.yml`: el cuerpo suma `"monthlyInterestRate": 1.9`, y la respuesta la trae
    en 1.9.
  - `modificar-cuenta-campo-en-null.yml`: el cuerpo suma `"monthlyInterestRate": null`, y la tasa
    sigue en 1.9.
  - `crear-cuenta-campos-credito-en-debito.yml`: el cuerpo suma `"monthlyInterestRate": 50`, fuera de
    rango a propósito, y los campos esperados son `creditLimit`, `monthlyInterestRate` y
    `statementDay`: un solo error por campo, el de "solo CREDIT".
  - `modificar-cuenta-campos-credito-en-efectivo.yml`: el cuerpo suma `"monthlyInterestRate": 2`, con
    un error más.
  - `cuentas-tras-modificar.yml`: `Mastercard` con `monthlyInterestRate` 1.9 y `BOLSILLO` con `null`.
  - `crear-cuenta-efectivo.yml` y `modificar-cuenta.yml`: el campo en la lista exacta de claves de la
    cuenta, y en `null` para la `CASH`. No fallaban en el RED porque el campo todavía no existía; se
    vieron en el primer `bru run` con el adapter terminado.
  - Nuevos, de la seq 79 en adelante —después de todos los listados que cuentan las cuentas del
    usuario (seq 7, 14, 32 y 66)—, con `token: {{tokenCuentas}}`:
    - `crear-cuenta-credito-sin-tasa`: `CREDIT` `Amex` sin tasa → 201 con `null`.
    - `crear-cuenta-credito-sin-interes`: `CREDIT` `Falabella` con 0 → 201 con 0.
    - `crear-cuenta-tasa-negativa`: `CREDIT` con -1 → 400 `VALIDATION_ERROR` en
      `monthlyInterestRate`.
    - `modificar-tasa-anual`: `{"monthlyInterestRate": 28.5}` sobre `idCuentaCredito` → 400 en
      `monthlyInterestRate`.
    - `modificar-tasa-decimales`: `{"monthlyInterestRate": 1.23456}` → 400 en `monthlyInterestRate`.
    - `modificar-tasa-limite`: `{"monthlyInterestRate": 10}` → 200 con 10.
    - `modificar-tasa-ceros-a-la-derecha`: `{"monthlyInterestRate": 1.50000}` → 200 con 1.5. Al ir
      al final, ningún request posterior lee la tasa de `Mastercard`.
    - `crear-cuenta-con-basic` y `listar-cuentas-con-basic`: con la credencial Basic compartida → 401
      `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Verificar que los requests y las
  aserciones nuevas fallan hoy con `verificar-bruno.ps1 -RecargarDatos`. Los de Basic pasan desde ya:
  documentan un comportamiento que existía sin spec.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `Account`, `NewAccount`, `CreateAccountCommand` y `UpdateAccountCommand` ganan
  `monthlyInterestRate` (`BigDecimal`). Ajustar `AccountMother`, `TransactionMother` y los tests que
  construyen esos records. Verificar con `.\gradlew.bat test` en verde, todavía sin comportamiento
  nuevo.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `CreateAccountUseCaseTest` en RED: la tasa del comando llega al `NewAccount`. Implementar en
  `CreateAccountUseCase`. Verificar con la clase en verde.
- [x] 3.2 `UpdateAccountUseCaseTest` en RED: la tasa sobre una `CREDIT` se aplica; en `null` conserva la
  guardada; sobre una `CASH` da 400 `VALIDATION_ERROR` en `monthlyInterestRate` sin llamar a `update`,
  y junto con `creditLimit` da los dos errores. Implementar en `UpdateAccountUseCase`. Verificar con la
  clase en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 Test de `Tasas` en RED (0, 10, 2.15 y 1.50000 caben; -0.0001, 10.0001 y 1.23456 no).
  Crear `Tasas` en `dto/validation/` y `@TasaMensual`, con el mismo patrón de `Montos` y
  `MontoNumeric`. Verificar con el test en verde.
- [x] 4.2 `CreateAccountRequestTest` en RED: una `CREDIT` con -1, 10.5 o 1.23456 da violación en
  `monthlyInterestRate`; con 0, 10 o sin tasa no da ninguna; una `DEBIT` con tasa da una sola violación
  en `monthlyInterestRate`. Sumar la tasa a `CreateAccountRequest` y a `CamposDeCredito`
  (`enRango` y `ausentes`). Verificar con la clase en verde.
- [x] 4.3 `UpdateAccountRequestTest` en RED: -1, 28.5 y 1.23456 dan violación en
  `monthlyInterestRate`; 1.50000 no; un parche que solo trae la tasa no es `sinCambios()`. Sumar el
  campo con `@TasaMensual` a `UpdateAccountRequest`, a `sinCambios()` y a `toCommand()`. Verificar con
  la clase en verde.
- [x] 4.4 `AccountControllerTest` en RED: el listado devuelve `monthlyInterestRate` de una `CREDIT`
  y `null` en una `CASH`; el POST y el PATCH pasan la tasa del cuerpo al puerto. Sumar el campo a
  `AccountResponse`. Verificar con la clase en verde.
- [x] 4.5 `AccountsIT`: POST y GET de `/api/accounts` con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer` (el caso sin credencial ya existe). Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 `AccountR2dbcAdapter`: `monthly_interest_rate` en `COLUMNAS`, en el `INSERT`, en el `UPDATE`
  y en `toDomain`, enlazada con `bindNull(..., BigDecimal.class)` cuando es `null`. Todo con bind
  variables. Sin test de suite (el adapter está excluido de la cobertura): lo verifican en verde los
  requests del grupo 1.3.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 `docs/api/contrato-api.md`: `monthlyInterestRate` en el ejemplo y en la tabla de la cuenta
  de `GET /api/accounts`, en la tabla y el ejemplo del alta, en el parche y en la lista de campos de
  crédito que dan 400 fuera de una `CREDIT`, con la unidad, el rango y la precisión. Verificar leyendo
  las secciones.
- [x] 6.2 `bruno-personal/cuentas/crear.yml` y `modificar.yml`: el campo con un valor de ejemplo y
  `docs` que digan la unidad y el rango. En `modificar.yml` va en el cuerpo, que es de ejemplo. En
  `crear.yml` va en el JSON de ejemplo de `docs`, porque el cuerpo es una tarjeta real del usuario y
  una tasa inventada quedaría guardada en Neon. Comparar ruta, cuerpo y autenticación con
  `bruno/accounts/crear-cuenta-credito.yml` y `modificar-cuenta-credito.yml`. No se ejecutan.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
