# Design

## Context

`exchange_rate` y `amount_base` los fija `trg_transactions_usd_equivalent` con la tasa USD→moneda
del movimiento en su fecha: en USD la tasa es 1 sin necesitar ninguna fila, así que los movimientos
en dólares no agregan requisitos de tasa a los de COP. `current_balance` lo mantiene
`trg_transactions_sync_balance`, que en una transferencia suma al destino `destination_amount` si
viene y `amount` si no; `check-saldos.sql` hace la misma cuenta.

Lo que deja el API en cada caso, y que la demo copia:

- Un gasto o ingreso en la moneda de su cuenta: `currency_code` de la cuenta, confirmado, sin
  `original_*`.
- Un gasto que llega en otra moneda: `amount` convertido a la moneda de la cuenta y redondeado a sus
  decimales (0 en COP), `original_amount` y `original_currency_code` con lo recibido, y entra
  `PENDING` con su `group_id`. Aprobarlo lo pasa a `CONFIRMED` y conserva el `group_id`.
- Una transferencia entre monedas con `destinationAmount`: `amount` en la moneda del origen,
  `destination_amount` en la del destino, confirmada.
- Series y compras en cuotas en la moneda de su cuenta. Una compra en cuotas solo va sobre una
  tarjeta (`CREDIT`): las cuentas en USD de la demo son una de ahorros.

En la base local hay hoy tasas USD→COP de 3900 y 4100 (`test-data.sql`); en Neon, las reales; en
una base sin ninguna, la de respaldo de 4000 que ya inserta el script.

## Escenario

`mes` = meses hacia atrás desde el mes en curso, con el mismo `date_trunc('month', …) + desfase` en
hora de Bogotá y el mismo filtro `occurred_at <= now()` que los demás confirmados.

**Cuenta en dólares** (`SAVINGS`, USD, saldo inicial 1.500,50):

| mes | Tipo | Categoría | Monto USD | Descripción |
|---|---|---|---|---|
| 0, 1, 2, 4, 5 | Gasto | Otros gastos | 3,50 | Comisión de manejo (día 1) |
| 5 | Ingreso | Freelance | 650,00 | Pago de cliente del exterior |
| 5 | Gasto | Educación | 89,00 | Curso en línea |
| 4 | Ingreso | Freelance | 720,00 | Pago de cliente del exterior |
| 4 | Gasto | Tecnología | 59,99 | Dominio y hosting |
| 3 | Gasto | Viajes | 145,30 | Hotel en Cartagena reservado en línea |
| 2 | Ingreso | Freelance | 650,00 | Pago de cliente del exterior |
| 2 | Gasto | Ropa | 72,45 | Compra en tienda en línea |
| 1 | Ingreso | Intereses | 3,12 | Intereses de la cuenta en dólares |
| 1 | Gasto | Entretenimiento | 24,99 | Videojuego en línea |
| 0 | Ingreso | Freelance | 800,00 | Pago de cliente del exterior |
| 0 | Gasto | Tecnología | 34,50 | Accesorios en línea |

El mes 3 sigue siendo el de pocos movimientos: solo el hotel, que acompaña los tiquetes a Cartagena
que ya tiene ese mes.

**Transferencias entre monedas**, confirmadas, con `destination_amount`:

| mes | Origen → destino | `amount` | `destination_amount` | Descripción |
|---|---|---|---|---|
| 4 | Nómina (COP) → Cuenta en dólares | 1.200.000 COP | convertido, −2 %, a centavos | Compra de dólares |
| 2 | Cuenta en dólares → Nómina (COP) | 300,00 USD | convertido, −2 %, a pesos | Venta de dólares |
| 1 | Nómina (COP) → Cuenta en dólares | 800.000 COP | convertido, −2 %, a centavos | Compra de dólares |

**Serie en USD**: «Almacenamiento en la nube», 9,99 USD, `MONTHLY` el día 12, sobre la cuenta en
dólares, categoría Suscripciones, sin fin, desde el 12 de hace cinco meses. Ocurrencias hasta hoy y
la siguiente programada, con el contador igual a las creadas, como el club de lectura.

**Tarjeta en USD**: «Visa en dólares», `CREDIT`, USD, saldo inicial 0, cupo 2.000, corte 25, pago 10,
tasa 1,5 % mensual. Sobre ella, la **compra en cuotas** «Celular» de 600 USD a 6 cuotas, del 5 de
hace tres meses, categoría Tecnología. Compra antes del corte y pago menor que el corte: la cuota k
vence el 10 del mes k después de la compra, a las 00:00 de Bogotá. Capital 100 por cuota e interés
del 1,5 % sobre el pendiente, 100 × (7 − k): 9,00, 7,50, 6,00, 4,50, 3,00 y 1,50. Las tres primeras
ya pasaron; las otras quedan confirmadas con fecha futura, como las del televisor.

**Gasto que llegó en USD a una cuenta en COP**: «Compra en tienda en línea», 45,99 USD recibidos,
en la Mastercard Oro, categoría Tecnología, el día 9 del mes 1. `amount` = 45,99 USD llevado a COP
con la tasa de la base de su fecha, redondeado a pesos. `CONFIRMED` (ya aprobado), con un `group_id`
propio y origen `WEB`.

Ids nuevos: la tarjeta `5000000<u>-…-008`, la serie `9000000<u>-…-002` y la compra
`a000000<u>-…-002`, con los prefijos que ya usan cuentas, series y compras.

## Decisions

### Una tarjeta nueva en USD para la compra en cuotas

La compra en cuotas solo va sobre una tarjeta y queda en su moneda. Se descartó pasar a USD la
Mastercard Oro, que perdería sus casos en COP (el televisor, los pagos fijos desde la nómina), y
poner la compra en la Mastercard con el monto en USD, que el API no admite: la compra no se
convierte. La tarjeta nueva queda con la deuda de sus cuotas y sin pagos, que la demo no necesita
para mostrar la compra.

### La serie va sobre la cuenta en dólares, no sobre la tarjeta

El criterio pide «la cuenta que cada una necesite». La serie no necesita una tarjeta, y sobre la
cuenta en dólares le da a esa cuenta un débito mensual más; la tarjeta nueva queda dedicada a la
compra en cuotas, que es lo que la justifica.

### Los montos convertidos salen de la tasa de la base, no fijos

El gasto convertido y los `destination_amount` se calculan al cargar con
`finance.amount_in_currency` para la fecha del movimiento. Se descartó escribirlos fijos: en Neon,
con la tasa real, un monto calculado con 4000 se vería incoherente con lo que muestra
`GET /api/exchange-rates`. El 2 % menos en las transferencias imita el diferencial del banco, que es
lo que hace que el usuario mande `destinationAmount` en vez de dejar que el API lo calcule.

### Fechas por mes, como los demás confirmados

A diferencia de los pendientes de FIN-95 y de lo compartido de FIN-30, todo lo de este change entra
confirmado: va por mes, con el desfase y el filtro `occurred_at <= now()` de los gastos e ingresos,
para que FIN-97 lo extienda a 12 meses con el mismo mecanismo.

## Risks / Trade-offs

- Las filas copian lo que escribe el API. Si cambia cómo guarda la conversión, las transferencias
  entre monedas, las series o las cuotas, la demo se queda vieja sin que nada falle; el mismo riesgo
  aceptado en FIN-30 y FIN-95.
- Los gastos en USD suben el gasto mensual en COP de los reportes y de las metas. Las metas no se
  tocan: ninguna spec ni assert fija los montos de la demo, y hoy ya hay meses sobre la meta global
  además del 4.
- La cuenta en dólares no puede quedar en negativo en ningún mes, o aprobar desde el front un gasto
  sobre ella fallaría por saldo. Con los montos del escenario el saldo mínimo queda por encima de
  1.000 USD; la tarea 1.4 lo comprueba.
