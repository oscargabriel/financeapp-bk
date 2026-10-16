# Proposal

FIN-96, epic FIN-74 (Calidad y mantenimiento). Refinada desde la raíz el 15-10-2026, junto con
FIN-30, FIN-95 y FIN-97.

## Why

La «Cuenta en dólares» de la demo se crea con saldo inicial y sin ningún movimiento, y el comentario
de *Cuentas* de `demo-data.sql` todavía dice que el API solo acepta movimientos en COP. Eso dejó de
ser cierto con el epic FIN-7: el alta acepta cuentas en cualquier moneda, convierte a la moneda de
la cuenta lo que llega en otra guardando el monto original, las transferencias entre monedas llevan
`destinationAmount`, y las series y las compras en cuotas toman la moneda de su cuenta (FIN-37,
FIN-79). El front no tiene en la demo ningún caso para mostrar montos en USD, su conversión a la
moneda de la persona en los reportes, ni los campos `destinationAmount`, `originalAmount` y
`originalCurrencyCode`.

Decisión del epic que aplica:

- La demo (`demo-data.sql`) cubre personas afiliadas y lo compartido, pendientes del asistente,
  movimientos en USD y 12 meses con unos 30 movimientos por mes (15-10-2026). Este change es la
  parte de los movimientos en USD; los 12 meses son FIN-97, que va después.

## What Changes

- **Ingresos y gastos en USD** en la cuenta en dólares, repartidos en los seis meses de la demo
  (el mes 3 sigue siendo el de pocos movimientos): pagos de un cliente del exterior, intereses,
  compras en línea y una comisión de manejo.
- **Transferencias entre monedas en los dos sentidos**, con `destination_amount`: de la nómina en COP
  a la cuenta en dólares (dos meses) y de la cuenta en dólares a la nómina (un mes). El monto de
  destino sale de la tasa de la base para la fecha, con un 2 % menos, como lo deja el banco.
- **Serie recurrente en USD**: almacenamiento en la nube, mensual, sobre la cuenta en dólares, con
  las ocurrencias hasta hoy y la siguiente programada, como la del club de lectura.
- **Compra en cuotas en USD**: una tarjeta de crédito nueva en USD, «Visa en dólares», porque una
  compra en cuotas solo va sobre una tarjeta y queda en su moneda; sobre ella, un celular a 6 cuotas
  con su interés.
- **Un gasto que llegó en USD a una cuenta en COP**, ya aprobado: una compra en línea en la
  Mastercard, con `original_amount` y `original_currency_code`, el monto convertido con la tasa de
  la base para su fecha y el `group_id` con que entró pendiente.
- El encabezado y el comentario de *Cuentas* de `demo-data.sql` dejan de decir que la cuenta en
  dólares no tiene movimientos, y nombran los casos en USD.

Todo sigue cargando sobre una base con solo `schema.sql` y `seed.sql` (FIN-45): la conversión usa la
tasa USD→COP que haya, o la de respaldo que el script ya inserta.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

Ninguna: cambia un script de datos, no el comportamiento del sistema (`skip_specs: true`). Las filas
siguen lo que ya describen `transacciones` (*Movimiento en la moneda de su cuenta*, *Conversión a la
moneda de la cuenta*, *Transferencia entre cuentas de monedas distintas*), `recurrentes` (*Alta de
una serie*) y `cuotas` (*Alta de una compra en cuotas*, *Fechas de las cuotas*, *Capital e interés de
las cuotas*).

## Fuera de alcance

- Más meses o más volumen de movimientos: FIN-97.
- Pendientes en USD o convertidos sin aprobar: el criterio pide uno ya aprobado, y los pendientes del
  asistente son FIN-95.
- Compartidos en USD con las personas afiliadas.
- Otras monedas además de USD, y cambiar la moneda base del usuario de la demo.
- Cambios en `test-data.sql`, en `bruno/` o en el API.
- Mostrar lo recibido del gasto convertido una vez aprobado. `GET /api/reports/transactions` no trae
  `originalAmount` ni `originalCurrencyCode` (`docs/api/transacciones.md`), así que el front solo lo
  ve en las respuestas de un movimiento. Salió al aplicar este change y quedó como issue aparte.

## Impact

- `docs/database/demo-data.sql`
- Sin cambios en `src/`, `bruno/`, el esquema ni el contrato del API. En Neon, la próxima recarga de
  la demo agrega la tarjeta en USD y los movimientos en dólares, con la tasa real que haya allí.
