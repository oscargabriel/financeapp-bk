# Tasks

## 1. Esquema y Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 `docs/database/schema.sql`: agregar a `finance.accounts` la columna
  `charges_single_installment_interest BOOLEAN NOT NULL DEFAULT FALSE`. En
  `ck_accounts_credit_fields`, la rama de las cuentas que no son `CREDIT` suma
  `AND charges_single_installment_interest = FALSE`. Agregar también un `COMMENT ON COLUMN`.
  - Emitir lo mismo en `docs/database/update/20261011_01_interes_una_cuota.sql`. El encabezado dice
    que va ANTES de desplegar la app y que no reinicia datos. La reversión va comentada al final.
  - Documentar la columna en `docs/database/modelo-datos.md`: diagrama y párrafo de los campos de
    crédito.
  - Aplicarlo a la base local con psql.
  - Verificar con la comparación de esquemas de `modelo-datos.md`, que tiene que salir limpia.
- [x] 1.2 En `bruno/accounts/`, con `tokenCuentas`:
  - `crear-cuenta-credito.yml`: la respuesta trae `chargesSingleInstallmentInterest` `false`.
  - `crear-cuenta-efectivo.yml` y `modificar-cuenta.yml`: agregar el campo a la lista exacta de
    claves, en `null` para la `CASH`.
  - `listar-cuentas.yml`: `Visa` con `false` y la `CASH` con `null`.
  - `modificar-cuenta-credito.yml`: el cuerpo suma `"chargesSingleInstallmentInterest": true`, y la
    respuesta lo trae en `true`.
  - `modificar-cuenta-campo-en-null.yml`: el cuerpo suma `"chargesSingleInstallmentInterest": null`,
    y la opción sigue en `true`.
  - `crear-cuenta-campos-credito-en-debito.yml`: el cuerpo suma
    `"chargesSingleInstallmentInterest": false`, y el campo entra en la lista de errores esperados.
  - `modificar-cuenta-campos-credito-en-efectivo.yml`: el cuerpo suma
    `"chargesSingleInstallmentInterest": true`, con un error más.
  - `cuentas-tras-modificar.yml`: `Mastercard` con `true` y `Bolsillo` con `null`.
  - Request nuevo `crear-cuenta-credito-interes-una-cuota`: una `CREDIT` con la opción en `true`
    responde 201 con `true`. Va al final de la carpeta, después de todos los listados que cuentan
    las cuentas del usuario.
- [x] 1.3 Al final de `bruno/installments/` (seq 55 en adelante), con `tokenCuotas`, crear estos
  requests en orden:
  - `crear-tarjeta-interes-una-cuota`: `POST /api/accounts` de una `CREDIT` `Visa una cuota` con cupo
    1000000, corte 20, pago 5, tasa 2 y la opción en `true`. Responde 201 con `true` y guarda
    `idVisaUnaCuota`.
  - `simular-una-cuota-con-interes`: 333333 a 1 cuota. Responde 200 con `interest` 6667, `amount`
    340000 y `totalInterest` 6667.
  - `simular-varias-cuotas-con-opcion`: 1200000 a 3 cuotas. Los intereses son 24000, 16000 y 8000.
  - `alta-una-cuota-con-interes`: 300000 a 1 cuota. Responde 201 con `interest` 6000, `amount`
    306000 y `remainingAmount` 306000, y guarda `idCompraUnaConInteres`.
  - `quitar-interes-una-cuota`: `PATCH /api/accounts/{{idVisaUnaCuota}}` con
    `{"chargesSingleInstallmentInterest": false}`. Responde 200 con `false`.
  - `compra-una-cuota-intacta`: `GET /api/installment-purchases`. La compra `idCompraUnaConInteres`
    sigue con `remainingAmount` 306000.
  - `simular-una-cuota-sin-opcion`: 300000 a 1 cuota sobre la misma tarjeta. Responde 200 con
    `interest` 0.

  Actualizar el `docs` de `installments/folder.yml` con la tarjeta nueva. Verificar con
  `verificar-bruno.ps1 -RecargarDatos` que los requests y las aserciones nuevas fallan hoy. Los 401 de
  `POST`, `PATCH` y `GET /api/accounts` y de las rutas de cuotas ya los cubren requests existentes.
  Las rutas y la autenticación no cambian.

## 2. Dominio

Skills: `java-architect`.

- [x] 2.1 `InstallmentPlanTest` en RED:
  - 333333 a 1 cuota con tasa 2.15 y la opción da `interest` 7167;
  - con la opción y tasa 0 da 0;
  - sin la opción da 0, como hoy;
  - 1200000 a 3 cuotas con tasa 2 da los mismos intereses con y sin la opción.

  Agregar `chargesSingleInstallmentInterest` a `InstallmentPlan` y ajustar `interes()`. Ajustar
  `InstallmentMother` y los tests que construyen el plan. Verificar con la clase en verde.
- [x] 2.2 `Account`, `NewAccount`, `CreateAccountCommand` y `UpdateAccountCommand` ganan
  `chargesSingleInstallmentInterest` (`Boolean`). Ajustar `AccountMother`, `TransactionMother`,
  `InstallmentMother` y los tests que construyen esos records. Verificar con `.\gradlew.bat test` en
  verde, todavía sin comportamiento nuevo.

## 3. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `CreateAccountUseCaseTest` en RED:
  - una `CREDIT` sin la opción llega al `NewAccount` con `false`;
  - con `true` llega con `true`;
  - una `CASH` llega con `null`.

  Implementar en `CreateAccountUseCase`. Verificar con la clase en verde.
- [x] 3.2 `UpdateAccountUseCaseTest` en RED:
  - la opción sobre una `CREDIT` se aplica;
  - en `null` conserva la guardada;
  - sobre una `CASH` da 400 `VALIDATION_ERROR` en `chargesSingleInstallmentInterest` sin llamar a
    `update`.

  Implementar en `UpdateAccountUseCase` (`aplicar` y `camposDeCreditoSegunElTipo`). Verificar con la
  clase en verde.
- [x] 3.3 `PreviewInstallmentPurchaseUseCaseTest` y `CreateInstallmentPurchaseUseCaseTest` en RED: con
  una tarjeta con tasa 2 y la opción, una compra de 300000 a 1 cuota sale con `interest` 6000. Con la
  opción en `false`, sale con 0. Una `CREDIT` nunca llega con la opción en `null`: el adapter y el
  constructor corto de `Account` la leen como `false`. Pasar la opción desde `PlanDeCompra`, sin que
  un `null` cobre interés. Verificar con las dos clases en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `CreateAccountRequestTest` en RED:
  - una `CREDIT` con la opción en `true`, en `false` o sin ella no da violaciones;
  - una `DEBIT` con `false` o `true` da una violación en `chargesSingleInstallmentInterest`.

  Agregar el campo a `CreateAccountRequest` y a `CamposDeCredito.ausentes`. Verificar con la clase en
  verde.
- [x] 4.2 `UpdateAccountRequestTest` en RED: un parche que solo trae la opción no es `sinCambios()`, y
  `toCommand()` la pasa. Agregar el campo a `UpdateAccountRequest`. Verificar con la clase en verde.
- [x] 4.3 `AccountControllerTest` en RED:
  - el listado devuelve la opción de una `CREDIT` y `null` en una `CASH`;
  - el POST y el PATCH pasan la opción del cuerpo al puerto.

  Agregar el campo a `AccountResponse`. Verificar con la clase en verde.

## 5. Persistencia

- [x] 5.1 En `AccountR2dbcAdapter`, agregar `charges_single_installment_interest` a `COLUMNAS`, al
  `INSERT` y al `UPDATE`, enlazada como `Boolean.TRUE.equals(valor)`. En `toDomain`, leerla y
  devolver `null` si el tipo no es `CREDIT`. Todo con bind variables. No lleva test de suite, porque el
  adapter está excluido de la cobertura: lo verifican en verde los requests de 1.2 y 1.3.

## 6. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 6.1 En `docs/api/contrato-api.md`, documentar `chargesSingleInstallmentInterest` en:
  - el ejemplo y la tabla de la cuenta de `GET /api/accounts`;
  - la tabla y el ejemplo del alta;
  - el parche;
  - la lista de campos de crédito que dan 400 fuera de una `CREDIT`.

  En «Compras en cuotas», cambiar «Con 1 cuota … no hay interés» por la regla nueva, con el ejemplo de
  300000 a 1 cuota con tasa 2. Verificar leyendo las secciones.
- [x] 6.2 En `bruno-personal/cuentas/crear.yml` y `modificar.yml`, agregar el campo y unos `docs` que
  digan qué hace y su valor por defecto.
  - En `crear.yml` va en el JSON de ejemplo de `docs`, no en el cuerpo, que es una tarjeta real.
  - En `modificar.yml` va en el cuerpo, que es de ejemplo.

  Comparar ruta, cuerpo y autenticación con `bruno/accounts/crear-cuenta-credito.yml` y
  `modificar-cuenta-credito.yml`. No se ejecutan. `bruno-personal/cuotas/` no cambia: su cuerpo es el
  mismo.

## 7. Verificación

- [x] 7.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 7.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  los conteos reales de requests, tests y aserciones.
