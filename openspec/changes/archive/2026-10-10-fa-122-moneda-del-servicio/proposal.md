# Proposal

Origen: FA-122, del tablero (Feature, etapa 9). Surgió el 10-10-2026 al tomar FA-51. El usuario
redefinió el modelo de monedas en tres niveles:

- **Servicio (USD):** la base común. Todo movimiento guarda su equivalente en dólares.
- **Cuenta:** el movimiento se registra en la moneda de su cuenta. Es lo de FA-51.
- **Persona** (`base_currency_code`): solo la moneda en que ve el resumen de inicio y los gráficos.
  Si la cambia, no se reescribe nada.

## Why

Hoy `amount_base` está en la moneda del usuario: todo es COP, con `exchange_rate = 1`. Los reportes
lo suman tal cual. FA-51 va a registrar movimientos en cuentas de otras monedas, y para sumarlos
hace falta una base común que no dependa de la moneda que elija cada persona. Si FA-51 escribiera en
USD mientras los reportes suman pensando en COP, los totales quedarían mezclados. Por eso esta tarea
va antes.

## What Changes

- **Equivalente en USD de cada movimiento.** Lo calcula la base, no la app: un trigger
  `BEFORE INSERT OR UPDATE` sobre `transactions` fija las dos columnas.
  - `exchange_rate` es la tasa `USD→moneda del movimiento` en su fecha, en la zona del usuario.
  - `amount_base = amount / exchange_rate`, a 4 decimales, en USD. Un movimiento en USD queda con
    tasa 1.
  - Solo se recalcula si cambia el monto, la moneda o la fecha. Así queda congelado, como hasta hoy.
  - Ningún adapter de escritura cambia: el alta, el asistente, el PATCH, las series y las cuotas
    pasan todos por el trigger.
- **La tasa más antigua cuando no hay una anterior** (decisión del 10-10-2026). La tasa de una fecha
  es la fila `USD→X` más reciente con `rate_date` ≤ la fecha. Si no hay ninguna, se usa la más
  antigua guardada.
  - La regla vive en una función SQL, `finance.usd_rate`, que usan el trigger, los reportes y
    `GET /api/exchange-rates`.
  - Ese endpoint deja de responder 404 cuando hay tasas posteriores. Solo responde 404 si no existe
    ninguna fila del par y el proveedor no la da.
- **Totales en la moneda de la persona** (decisión del 10-10-2026). En `monthly-spending`,
  `reports/transactions` y `reports/balance`, cada movimiento se convierte así:
  - si su moneda es la de la persona, cuenta su `amount` tal cual;
  - si no, cuenta su `amount_base` en USD por la tasa `USD→moneda de la persona` de su fecha.

  Los resúmenes del asistente salen de esos mismos casos de uso. El contrato no cambia: `amountBase`
  y `currencyCode` del reporte siguen en la moneda de la persona.
- **Tasa del día al registrar.** El alta en lote pide la tasa de hoy al proveedor, una vez y solo si
  falta, cuando un elemento tiene fecha de hoy o posterior. Así el equivalente en USD de lo reciente
  no se queda con una tasa vieja. Si el proveedor falla, se usa la tasa anterior.
- **Sin ninguna tasa de la moneda,** el trigger rechaza la escritura. La app responde 502
  `EXTERNAL_SERVICE_ERROR` en `server`, sin detalle interno.
- **Migración.** `update/20261010_01_moneda_del_servicio.sql` crea las funciones y el trigger,
  recalcula `exchange_rate` y `amount_base` de los movimientos existentes con la misma regla y
  redefine las vistas. Si hay movimientos y ninguna tasa `USD→COP`, aborta y dice cómo obtenerla.
  La reversión va comentada al final del script.

## Fuera de alcance

- Registrar movimientos en cuentas de otras monedas, la conversión a pendiente y la transferencia
  entre monedas: es FA-51. La regla de solo COP sigue.
- Cambiar la moneda de la persona: es FA-91. Con este modelo ya no reescribe nada.
- Exponer `exchange_rate` o el equivalente en USD en las respuestas.
- Las metas (`budgets`): siguen en la moneda de la persona. Solo cambia el comentario de la columna.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- **`tasas-de-cambio`:** sin tasa ≤ la fecha se usa la más antigua guardada, y el 404 queda solo
  para un par sin ninguna fila.
- **`transacciones`:** cada movimiento guarda su equivalente en USD, y el alta pide la tasa del día.
- **`reportes`:** los montos y totales se calculan en la moneda de la persona con la regla de arriba.

## Impact

- **Base de datos:**
  - `schema.sql`: dos funciones, un trigger, las dos vistas y comentarios;
  - el update nuevo, que en local reinicia los datos y en Neon se aplica después de desplegar la
    app;
  - `test-data.sql`: las tasas de `USD→COP` pasan a hoy 4100, hace 35 días 4100 y hace 45 días 3900,
    para que el movimiento en USD del mes siga valiendo 410.000 COP sea cual sea el día del mes.
- **Código:**
  - `TransactionReportR2dbcAdapter`, `BalanceR2dbcAdapter` y `ExchangeRateR2dbcAdapter` usan las
    funciones;
  - `CreateTransactionsUseCase` pide la tasa del día;
  - los adapters de escritura traducen el error de la falta de tasa;
  - los comentarios que decían «solo COP: `amount_base = amount`».
- **Bruno:**
  - `bruno/exchange-rates/` ajusta la tasa anterior y el caso sin tasa;
  - en `bruno/reports/`, un usuario nuevo con moneda USD registra gastos en una cuenta COP y ve sus
    totales en dólares.
- **Docs:** `modelo-datos.md` (multi-moneda y vistas), `contrato-api.md` (`amountBase` y la tasa más
  antigua) y `despliegue.md` (el orden del update).
