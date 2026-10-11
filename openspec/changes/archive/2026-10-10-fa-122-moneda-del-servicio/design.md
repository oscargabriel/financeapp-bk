# Design

## 1. La conversión a USD la hace la base, con un trigger

`finance.set_usd_equivalent()` es un trigger `BEFORE INSERT OR UPDATE` sobre `transactions`:

- Busca la zona del usuario, saca la fecha local de `occurred_at` y pide
  `finance.usd_rate(currency_code, fecha)`.
- Fija `exchange_rate` y `amount_base`.
- En un `UPDATE` solo recalcula si cambió `amount`, `currency_code` u `occurred_at`. Si no, conserva
  los valores viejos, aunque la sentencia los traiga en el `SET`. Así aprobar un pendiente o cambiar
  la descripción no le cambia la tasa a un movimiento.

**Descartado:** resolver la tasa en Java en cada camino de escritura.
- Son cuatro adapters con SQL propio: el alta, el PATCH, las series y las cuotas. A cada uno habría
  que pasarle la tasa, y uno nuevo podría olvidarla.
- La migración necesita la misma regla en SQL de todos modos.
- El proyecto ya deja en la base lo que no debe depender de que la app se acuerde: los saldos los
  mantiene `trg_transactions_sync_balance`.

**Lo que no puede hacer la base es llamar al proveedor.** Eso queda en la app (decisión 5). Si el
proveedor no se consultó, el trigger usa la última tasa guardada.

El INSERT y los UPDATE de la app siguen mandando `amount_base = amount`. Es un valor de relleno que
el trigger reemplaza. Se conserva para que la app nueva funcione mientras el update todavía no está
aplicado en Neon (decisión 7).

## 2. `exchange_rate` guarda la tasa USD→moneda, y `amount_base` divide

`exchange_rate` toma la misma convención que `exchange_rates.rate`: unidades de la moneda por un
dólar. Por ejemplo, 4100 para COP. `amount_base = round(amount / exchange_rate, 4)`.

**Descartado:** guardar la inversa (USD por unidad, 0.0002439024) y multiplicar. A escala 10 la
inversa de una moneda débil conserva unos 7 dígitos significativos. Con 1.000 millones de COP el
error llegaría a centavos de dólar. Dividir por la tasa original no pierde nada.

El criterio de la tarea decía «`amount × exchange_rate`». Es la misma conversión expresada con la
tasa en el otro sentido.

`ck_transactions_amount_base` exige `amount_base > 0`, y un monto menor de 0,5 COP redondearía a
cero. Por eso el trigger aplica `GREATEST(..., 0.0001)`.

## 3. Una sola función decide la tasa de una fecha

`finance.usd_rate(currency, on_date)` devuelve `(rate, rate_date)`:

- USD da `(1, on_date)`.
- Si no, la fila `USD→currency` con la `rate_date` más reciente ≤ `on_date`.
- Si no hay ninguna, la más antigua guardada.
- Si no hay ninguna fila, devuelve vacío.

La usan el trigger, la función de conversión de los reportes y `ExchangeRateR2dbcAdapter`. Así
`GET /api/exchange-rates` y lo que se guarda no pueden aplicar reglas distintas.

## 4. Los totales se convierten fila a fila, en la moneda de la persona

`finance.in_currency(target, currency, amount, amount_base, on_date)`:

- si `currency = target`, devuelve `amount`;
- si `target` es USD, devuelve `amount_base`;
- si no, `round(amount_base × usd_rate(target, on_date).rate, 4)`.

Las vistas, el balance y el reporte suman eso, con `on_date` como la fecha local del movimiento.

Quien solo usa COP ve exactamente los mismos números que hoy, sin el redondeo de ida y vuelta por el
dólar. Un movimiento en otra moneda se muestra con la tasa de su día, que es lo que pedían las notas
originales de la etapa 9 («cuánto valían en dólares ese día»).

**Descartado:** convertir todo desde USD, también lo que ya está en la moneda de la persona.
50.000 COP saldrían como 49.999,91. **Descartado también:** convertir con la tasa de hoy. Un mes
cerrado cambiaría de valor según el día en que se mire.

Si la moneda de la persona no tiene ninguna tasa, `in_currency` da `NULL` para los movimientos en
otra moneda. No pasa mientras todo sea COP. FA-51 lo tiene que considerar.

## 5. El alta en lote pide la tasa del día

Sin esto, nada refresca `USD→COP` salvo `GET /api/exchange-rates`, y el equivalente en USD de lo
nuevo se quedaría con la tasa vieja.

`ResolveExchangeRatePort` gana `refreshToday(currencies)`. Reutiliza `alDia` del caso de uso de
FA-120: una llamada como máximo, y solo si a alguna moneda le falta la fila de hoy. Un fallo del
proveedor se absorbe.

`CreateTransactionsUseCase` lo llama antes de guardar, con las monedas de los elementos fechados hoy
o después en la zona de la app. El asistente pasa por ese mismo caso de uso.

Las series y las cuotas no lo llaman: sus ocurrencias son futuras o se generan al leer, y usan la
última tasa guardada.

## 6. Sin ninguna tasa, 502

El trigger lanza `SQLSTATE 'FX001'` si la moneda no tiene ni una fila. Los adapters de escritura lo
traducen a `BadRequestException(502, EXTERNAL_SERVICE_ERROR, «No hay tasa de cambio para registrar
el movimiento; intenta de nuevo», "server")`.

Solo puede pasar en una base sin tasas y con el proveedor caído. 502 es el código del proyecto para
un servicio externo que no respondió.

## 7. Orden de despliegue: primero la app, después el update

- **La app nueva sin el update funciona.** Sigue mandando `amount_base = amount`, y sus reportes
  usan `amount` para lo que está en la moneda de la persona, que hoy es todo. Las funciones no
  existirían todavía, así que el reporte y el balance fallarían. Por eso el update se aplica
  **inmediatamente después** del despliegue. Con la app vieja y la base nueva, en cambio, los
  reportes sumarían dólares como si fueran pesos.
- **Antes de aplicarlo en Neon** tiene que existir una fila `USD→COP`. Se obtiene con
  `GET /api/exchange-rates?from=USD&to=COP&date=<hoy>` contra el servicio ya desplegado.
- **El script aborta** si hay movimientos de una moneda sin ninguna tasa. En la base vacía de la
  comparación de esquemas no hay movimientos, así que pasa.
- **El recálculo de los existentes va antes de crear el trigger.** Durante el `UPDATE` se
  deshabilitan `trg_transactions_sync_balance` y `trg_transactions_updated_at`: el monto no cambia,
  así que no hay saldo que mover, y la fecha de modificación no debe moverse.

## 8. Las tasas de `test-data.sql`

`USD→COP` queda en hoy 4100, hace 35 días 4100 y hace 45 días 3900.

- El movimiento en USD del mes en curso cae siempre después de hace 35 días, así que en el reporte
  sigue valiendo 100 × 4100 = 410.000 COP, cualquiera sea el día del mes.
- `bruno/exchange-rates/tasa-anterior.yml` pasa a pedir hace 40 días: 3900, de hace 45.
- `sin-tasa.yml` pasa a ser «más antigua»: 2000-01-01 da 3900 con `rateDate` de hace 45 días.
