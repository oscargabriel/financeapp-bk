# Proposal

Issue **FIN-29** del tablero (migrada de FA-91), epic FIN-7 (Etapa 9 — Tasas de cambio automáticas).
Se pidió el 07-10-2026 al tomar FA-89, el perfil del usuario: «dejemos en pendiente el cambio de
moneda en otra tarea que será todo el flujo de monedas y conversiones». Se refinó desde la raíz de
coordinación el 12-10-2026. Su contraparte en el front es FIN-56, que elige la moneda en «Edita tu
perfil» y está bloqueada por esta.

## Why

La persona elige su moneda base solo al registrarse: `PATCH /api/users/me` descarta
`baseCurrencyCode` sin decir nada. FA-122 ya dejó el modelo listo para cambiarla sin reescribir
movimientos, pero faltan dos piezas para que el cambio no deje números falsos: una moneda sin
ninguna tasa `USD→X` deja los totales en `null`, y las metas de gasto se comparan sin convertir, como
si estuvieran siempre en la moneda actual de la persona.

## What Changes

- **`PATCH /api/users/me` acepta `baseCurrencyCode`.** Código de tres letras, sin distinguir
  mayúsculas y con espacios en el borde tolerados; se guarda en mayúsculas. Tiene que ser una moneda
  activa del catálogo. El 200 trae la moneda nueva.
- `baseCurrencyCode` **cuenta como campo modificable**: `{"baseCurrencyCode": "USD"}` solo deja de ser
  400 en `body`. En blanco es 400 `VALIDATION_ERROR` en `baseCurrencyCode`, como `firstName`.
- **Mandar la moneda que ya tiene es 200 sin efecto**: no se consulta el catálogo ni las tasas.
- **Antes de guardar, el cambio asegura una tasa `USD→<moneda nueva>`** con las reglas de
  `GET /api/exchange-rates` para hoy: consulta al proveedor si a la moneda le falta la fila de hoy y
  el límite de consultas lo permite. Si al final no hay ninguna tasa guardada de esa moneda, responde
  **502 `EXTERNAL_SERVICE_ERROR` en `server`**, sin detalle del proveedor, y no aplica nada del parche.
- **Orden de las validaciones del parche**: formato (todos los campos a la vez) → parche vacío →
  moneda activa → contraseña y unicidad del correo → tasa de la moneda nueva → guardar. Ningún error
  aplica nada.
- **Las metas de gasto se comparan en la moneda de la persona.** El monto de cada meta, global o por
  categoría, se convierte de su `currency_code` a la moneda base con la tasa del primer día del mes
  de la meta. `GET /api/monthly-spending` devuelve `budgetAmount`, `remaining` y `percentUsed` en esa
  moneda. Las metas guardadas no se reescriben.
- Esquema: función nueva `finance.amount_in_currency` y las dos vistas de gasto mensual la usan para
  la meta. Update `docs/database/update/20261012_02_metas_en_moneda_de_la_persona.sql` con su
  reversión, `schema.sql` al día y el comentario de `budgets.amount` corregido. No modifica tablas.
- No hay código de error nuevo.

Los reportes, el saldo y el asistente ya calculan sus totales con la moneda que lee cada consulta en
`users.base_currency_code` (FA-122): el cambio de moneda se refleja ahí sin tocar sus consultas. Este
change solo lo verifica.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `usuarios`: el requisito «La moneda base no cambia desde el perfil» se reemplaza por el cambio de
  moneda base desde el perfil, con su validación, la tasa previa y el orden de los errores del parche.
- `reportes`: «Montos en la moneda de la persona» suma la meta de `monthly-spending`, convertida
  desde la moneda de la meta, y el cambio de moneda base sin reescribir movimientos.

## Fuera de alcance

- **Crear o editar metas desde el API.** No existe todavía («Lo que el API todavía no tiene» del
  contrato); las metas entran por scripts. La moneda con la que se guarde una meta nueva es de la
  tarea que la cree.
- **Desglose por categoría en el API.** `v_monthly_spending_by_category` se corrige igual que la
  global, pero ningún endpoint la expone: la verifica `docs/database/test-checks.sql`, no Bruno.
- **Convertir saldos de cuentas.** Cada cuenta sigue en su propia moneda; solo cambian los totales.
- **Avisar al front de la tasa usada.** El 200 trae el perfil, no la tasa.
- **El alta con una moneda sin tasas.** `POST /api/auth/register` sigue aceptando cualquier moneda
  activa sin asegurar su tasa; si hace falta, va a un issue nuevo.

## Impact

- `core`: `UpdateUserProfileRequest` (campo con su validación y `sinCambios`),
  `UpdateUserProfileCommand`, `UpdateUserProfileUseCase` (catálogo, tasa, orden) y el `UPDATE` de
  `UserR2dbcAdapter`. El caso de uso usa los puertos que ya existen: `CurrencyQueryPort`,
  `ResolveExchangeRatePort` y el `Clock`.
- Esquema: función y vistas, en `schema.sql` y en el update. El update no toca tablas: no reinicia los
  datos locales y en producción puede aplicarse antes o después del despliegue.
- Datos: `test-data.sql` pasa la meta global del mes siguiente de `prueba@` a 500 USD, para que Bruno
  vea una meta en otra moneda que la persona.
- Documentación: `docs/api/contrato-api.md` (`PATCH /api/users/me`, `GET /api/monthly-spending`, «Lo
  que el API todavía no tiene») y `docs/database/modelo-datos.md` (multi-moneda, metas y vistas).
- Bruno: `bruno/users/` (cambio válido, moneda inactiva, solo la moneda, la misma moneda, el saldo
  tras el cambio) y `bruno/monthly-spending/` (la meta en USD). Se replican en `bruno-personal/`.
