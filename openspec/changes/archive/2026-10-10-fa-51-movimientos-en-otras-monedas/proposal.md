## Why

FA-51, surgida en FA-27 y redefinida el 10-10-2026 con el modelo de tres niveles de moneda: servicio
(USD), cuenta y persona. FA-122 resolvió el de servicio y el de persona; esta tarea resuelve el de la
cuenta. Hoy el alta en lote solo admite COP: una cuenta en otra moneda, un `currencyCode` distinto de
COP o un `destinationAmount` dan 400. El usuario lo pidió así:

- Cada movimiento se registra **en la moneda de su cuenta**, porque es el cargo real. Con una tarjeta
  en pesos se compra algo en soles: el banco cobra en pesos, y eso es lo que se registra.
- Si un movimiento llega en una moneda distinta a la de su cuenta, se convierte con las tasas
  internas y **queda pendiente** para que la persona lo confirme o ajuste al cargo real.
- El monto recibido se guarda en columnas propias, y la respuesta lo muestra.

Al tomarla, el usuario indicó que la base local y los datos de prueba se reinician para no arrastrar
datos viejos: en local la base se reconstruye desde `schema.sql` en vez de aplicar el update sobre lo
que haya.

## What Changes

- **Alta en lote** (`POST /api/transactions`):
  - Admite cuentas en cualquier moneda. El movimiento se registra en la moneda de su cuenta.
  - `currencyCode` es opcional. Ausente o igual a la moneda de la cuenta, el alta es la de siempre.
  - Si es distinto, el monto se convierte a la moneda de la cuenta con la tasa de la fecha del
    movimiento y el movimiento queda `PENDING`.
  - Una transferencia entre cuentas de monedas distintas:
    - con `destinationAmount`, queda en el estado de siempre y el destino recibe ese monto;
    - sin él, el destino se calcula con la tasa interna y la transferencia queda `PENDING`.
  - `destinationAmount` en una transferencia entre cuentas de la misma moneda, o en un gasto o
    ingreso, es 400.
- **Columnas nuevas** en `transactions`: `original_amount` y `original_currency_code`, con el monto y
  la moneda recibidos cuando hubo conversión. No son modificables.
- **Respuestas**: el alta, el PATCH, la aprobación y la lista de pendientes devuelven
  `destinationAmount`, `originalAmount` y `originalCurrencyCode`, en null cuando no aplican.
- **Modificación** (`PATCH /api/transactions/{id}`):
  - Una cuenta nueva tiene que estar en la moneda del movimiento.
  - Admite `destinationAmount` solo en una transferencia entre monedas distintas: es como se ajusta el
    destino convertido al monto real.
  - Una transferencia que pasa a ser entre monedas distintas sin un `destinationAmount` aplicable es
    400.
- **Sin ninguna tasa** para convertir, la respuesta es 502 `EXTERNAL_SERVICE_ERROR`, como el FX001 de
  FA-122.
- **Series y cuotas** siguen solo en COP. El mensaje lo dice con esas palabras: «por ahora las series
  solo admiten cuentas en COP», y lo mismo para las cuotas. El PATCH de un movimiento que es ocurrencia
  o cuota no puede moverlo a una cuenta de otra moneda, por la regla general del PATCH.
- **Base de datos**:
  - un update que agrega las dos columnas, nulables y sin migrar datos;
  - en local la base se reconstruye desde cero y se recargan todos los datos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`:
  - movimientos en la moneda de su cuenta;
  - conversión a pendiente;
  - transferencias entre monedas;
  - campos nuevos en la respuesta;
  - reglas de moneda en el PATCH.

Las specs de `recurrentes` y `cuotas` ya dicen que una cuenta en otra moneda que COP es 400 en
`accountId`. Eso no cambia: solo cambia el texto del mensaje, que la spec no fija.

## Impact

- Base de datos:
  - `docs/database/schema.sql`, con las dos columnas, el CHECK que las ata y sus comentarios;
  - el update `docs/database/update/20261010_02_monto_original.sql`, con la reversión comentada;
  - `docs/database/modelo-datos.md`.
- Dominio:
  - `Transaction` gana `destinationAmount` y `original`;
  - `Currency` gana `decimalPlaces`;
  - `ExchangeRate` gana la conversión de un monto.
- Aplicación:
  - `TransactionBatchValidator`;
  - `CreateTransactionsUseCase`;
  - `UpdateTransactionUseCase`;
  - `ReferenciasDelUsuario`;
  - `CreateRecurrenceUseCase` y `UpdateRecurrenceUseCase`;
  - la regla de cuotas.
- Infraestructura:
  - `CreateTransactionRequest`;
  - `UpdateTransactionRequest`;
  - `TransactionResponse`;
  - `TransactionR2dbcAdapter`;
  - `CurrencyR2dbcAdapter`.
- Bruno: `bruno/transactions/` con una cuenta en USD, replicado en `bruno-personal/`.
- Documentación: `docs/contrato-api.md`.
- El front recibe tres campos nuevos en las respuestas de movimientos, todos aditivos.

## Fuera de alcance

- Series y cuotas en otras monedas. `InstallmentPlan` redondea a pesos enteros, y las series necesitan
  reglas para cambiar de cuenta. Va a una tarea nueva del tablero.
- Mostrar el monto original en `GET /api/reports/transactions`: el reporte solo trae confirmados, en
  la moneda de la cuenta.
- El asistente sigue registrando en la moneda de la cuenta que elige, sin convertir.
- La atribución «Rates By Exchange Rate API» en el front: se crea su tarea de Frontend al cerrar esta.
- Reiniciar Neon. Allí el update es aditivo y no toca datos.
