# Tasks

## 1. Metas en la moneda de la persona (base de datos)

Skills: `bruno-cli`.

- [x] 1.1 RED: `test-data.sql` pasa la meta global del mes siguiente de `prueba@` a 500 USD y el tope
  de Viajes del mes en curso a 100 USD (decisión 5 de design.md), con el porqué en el comentario de
  *Metas*. `bruno/monthly-spending/gasto-mensual-mes-con-meta-sin-movimientos.yml` espera
  `budgetAmount` y `remaining` 2050000 y `percentUsed` 0, con su `docs` al día. Verificar que falla
  contra la vista actual: `verificar-bruno.ps1 -RecargarDatos` con ese request en rojo (2000000 COP
  leídos como 500).
- [x] 1.2 `docs/database/update/20261012_02_metas_en_moneda_de_la_persona.sql`: función
  `finance.amount_in_currency` de la decisión 4 y las dos vistas de gasto mensual convirtiendo la
  meta con ella en `b.period_month`, mismas columnas en el mismo orden. Encabezado: no toca tablas,
  no reinicia los datos locales, va antes o después del despliegue. Reversión comentada al final con
  las vistas actuales y el `DROP FUNCTION`. Verificar aplicándolo a la base local con
  `psql -v ON_ERROR_STOP=1`, dos veces seguidas.
- [x] 1.3 Lo mismo en `schema.sql` (función junto a `in_currency`, regla nueva en el comentario de
  las vistas) y el comentario de `budgets.amount` corregido: la meta está en su `currency_code` y las
  vistas la convierten. Verificar con la comparación de esquemas de `docs/database/modelo-datos.md`:
  sin diferencias.
- [x] 1.4 `docs/database/modelo-datos.md`: *Multi-moneda* (la meta y la función), `v_monthly_spending`
  (quitar «igual que la meta») y *Metas* (`currency_code` y la conversión con la tasa del día 1).
  Verificar releyendo que ninguna frase siga diciendo que la meta está en la moneda de la persona.
- [x] 1.5 GREEN: `gasto-mensual-mes-con-meta-sin-movimientos` en verde con
  `verificar-bruno.ps1 -RecargarDatos`, y
  `SELECT category_budget FROM finance.v_monthly_spending_by_category` de Viajes del mes en curso de
  `prueba@` da 410000 por psql.
- [x] 1.6 `bruno-personal/reportes/gasto-mensual.yml`: revisar que no asuma la meta en la moneda de
  la persona; sin cambios de ruta, cuerpo ni autenticación. Sin ejecutar.

## 2. Contrato del parche y Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 `bruno/users/`, en orden y renumerando los `seq` siguientes:
  - `modificar-perfil.yml` manda `" usd "` y espera `baseCurrencyCode` `USD` (cambio válido); su
    `docs` deja de hablar de FA-91;
  - `perfil-tras-modificar.yml` espera `USD`;
  - `modificar-perfil-solo-moneda.yml` manda `"EUR"` y espera 200 con `EUR`;
  - nuevo `modificar-perfil-moneda-inactiva.yml`: `{"firstName": "Otro", "baseCurrencyCode": "XTS"}`
    da 400 `VALIDATION_ERROR` en `baseCurrencyCode`;
  - nuevo `modificar-perfil-misma-moneda.yml`: `" eur "` da 200 con `EUR` y `firstName` `Ana Maria`
    (el de la inactiva no se aplicó);
  - nuevo `saldo-tras-cambiar-moneda.yml`: `GET /api/reports/balance` con `tokenPerfil` trae
    `currencyCode` `EUR`;
  - `modificar-perfil-campos-invalidos.yml` suma `"baseCurrencyCode": "US"` y su error en
    `baseCurrencyCode`.
  Verificar con `bru run users -r --env local` contra la app de `dev`: fallan esos requests, por la
  moneda que no cambia.
- [x] 2.2 `docs/api/contrato-api.md`: en `PATCH /api/users/me`, la fila de `baseCurrencyCode`, el
  parche vacío, el orden de las validaciones, la misma moneda y los errores nuevos (400 en
  `baseCurrencyCode`, 502 en `server`); en `GET /api/monthly-spending`, `budgetAmount` en la moneda
  base convertido desde la moneda de la meta con la tasa del día 1; y el punto quitado de «Lo que el
  API todavía no tiene». Verificar con `grep -n "FA-91" docs/api/contrato-api.md` vacío.
- [x] 2.3 `bruno-personal/perfil/modificar.yml`: quitar la nota de que `baseCurrencyCode` se ignora y
  dejar el campo fuera del cuerpo por defecto, para no cambiar la moneda real al probar otro campo.
  Sin ejecutar.

## 3. El cambio de moneda en el perfil

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `UpdateUserProfileRequest`: componente `baseCurrencyCode` con `@Pattern(NO_EN_BLANCO)` y
  `@Pattern(CODIGO_MONEDA)`, incluido en `sinCambios()` y en `toCommand()`; javadoc sin FA-91.
  `UpdateUserProfileCommand` con el campo. Verificar con `UserControllerTest`: moneda en blanco,
  `"US"` y `"U5D"` dan 400 en `baseCurrencyCode`; solo la moneda deja de ser 400 en `body`; solo
  `currentPassword` sigue siéndolo.
- [x] 3.2 `UpdateUserProfileUseCase` con `CurrencyQueryPort`, `ResolveExchangeRatePort` y `Clock`,
  en el orden de la decisión 1: normaliza (`trim`, mayúsculas); igual a la actual no consulta nada;
  inactiva o inexistente da 400 en `baseCurrencyCode`; después la contraseña y el correo; después
  `resolve("USD", nueva, hoy)` con el 404 traducido a 502 `EXTERNAL_SERVICE_ERROR` en `server`; al
  final `updateProfile` con la moneda nueva. Javadoc del puerto y del caso de uso sin FA-91.
  Verificar con `UpdateUserProfileUseCaseTest`: cambio válido en minúsculas, misma moneda sin tocar
  catálogo ni tasas, inactiva, sin tasa (502 sin guardar), cambio a USD, moneda inactiva con
  contraseña equivocada (400 en moneda), contraseña equivocada con moneda válida (sin `resolve`).
- [x] 3.3 `UserR2dbcAdapter.ACTUALIZAR_PERFIL` guarda `base_currency_code`. Verificar con
  `bru run users` del grupo 2 en verde (la suite no ejercita el adapter).
- [x] 3.4 `UsersIT`: el `PATCH` con la moneda llega al caso de uso y el 502 sale con su forma de
  error. Verificar con `gradlew test --tests "*UsersIT"`.
- [x] 3.5 GREEN: `bru run users -r --env local` contra la app de la rama, con `-RecargarDatos`, en
  verde.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde: conteo de tests y cobertura reales.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde:
  requests, asserts y tests reales, y `check-saldos.sql` sin cuentas descuadradas. Incluye
  `bruno/assistant/` y `bruno/reports/`, que comparan los totales del asistente y de los reportes.
- [x] 4.3 `openspec validate fin-29-cambiar-moneda-base` en verde.
