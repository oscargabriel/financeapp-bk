## Context

FA-122 dejó el modelo de tres niveles:

- `amount_base` en USD, que congela el trigger `trg_transactions_usd_equivalent`;
- los totales en la moneda de la persona.

FA-120 dejó `ResolveExchangeRatePort.resolve(from, to, date)`. Devuelve la tasa cruzada contra USD,
la de la fila más reciente igual o anterior a la fecha, o la más antigua si no hay anterior. Si es
para hoy y falta la fila, le pide las tasas al proveedor.

`transactions` ya tiene `destination_amount`, y `trg_transactions_sync_balance` ya mueve el destino
con `COALESCE(destination_amount, amount)`. Lo que falta:

- dejar entrar cuentas que no son COP;
- convertir;
- guardar lo recibido;
- poder ajustarlo antes de aprobar.

## Goals / Non-Goals

**Goals**
- Registrar cada movimiento en la moneda de su cuenta.
- Convertir lo que llegue en otra moneda y dejarlo pendiente.
- Transferencias entre monedas.

**Non-Goals**
- Series y cuotas en otras monedas.
- El monto original en los reportes.
- Conversión en el asistente.

## Decisions

### 1. La conversión se hace en la aplicación, no en un trigger

El caso de uso pide `resolve(monedaRecibida, monedaDeLaCuenta, fecha)` y multiplica.

**Descartado:** un trigger que convierta a partir de `original_amount`.
- `amount` es NOT NULL y tendría que llegar con un valor de relleno.
- La base no puede pedirle la tasa del día al proveedor.
- La respuesta del alta no tendría el monto convertido sin volver a leer la fila.

En la aplicación, la conversión usa el mismo puerto que `GET /api/exchange-rates`, y el monto que
devuelve el alta es el que se guarda.

### 2. Redondeo a los decimales de la moneda de la cuenta

El monto convertido se redondea a `currencies.decimal_places` de la moneda de destino, con HALF_EVEN.
Si queda en cero, pasa a la unidad mínima de esa escala, porque `ck_transactions_amount` exige que sea
mayor que cero. Para eso `Currency` gana `decimalPlaces`, y el caso de uso lee las monedas activas una
vez por lote. Con esa misma lectura valida que `currencyCode` sea una moneda activa, con el error en
el índice del elemento.

**Descartado:** los 4 decimales de la columna. Darían montos como `41000.0000` COP que no corresponden
a ningún cargo real.

### 3. La fecha de la tasa es la del movimiento en la zona de la app

Es la misma zona con la que `CreateTransactionsUseCase` ya decide qué es «hoy» para pedir la tasa del
día. El trigger de FA-122 usa la zona de la persona. Las dos solo difieren cerca de medianoche, entre
zonas distintas, y el resultado queda pendiente y ajustable.

**Descartado:** leer la zona del usuario. Agrega una consulta y una dependencia al caso de uso por un
caso de borde.

### 4. Convertido significa pendiente, sea cual sea el origen

Una conversión deja el movimiento `PENDING` aunque venga de la web, que normalmente entra
`CONFIRMED`. La tasa interna es una aproximación del cargo real, y el pedido del usuario es confirmarla.

### 5. Transferencias entre monedas

Sean A la moneda de la cuenta origen, B la del destino y C la recibida (`currencyCode`, por defecto A).

| Caso | `amount` | `destination_amount` | Estado |
|---|---|---|---|
| C = A, A = B | el recibido | null; con `destinationAmount`, 400 | el del origen |
| C ≠ A | convertido C→A | según la fila de abajo | `PENDING` |
| A ≠ B, con `destinationAmount` | — | el recibido | el del origen, salvo C ≠ A |
| A ≠ B, sin `destinationAmount` | — | `amount` convertido A→B | `PENDING` |

`destinationAmount` siempre está en la moneda del destino, y no se convierte.

### 6. Monto original: en columnas propias, inmutable

`original_amount` y `original_currency_code` solo se llenan cuando C ≠ A. Un CHECK exige que vayan
las dos o ninguna, con el monto mayor que cero. Si un PATCH ajusta `amount`, el original no cambia:
registra lo que llegó, no lo que se aprobó. En el dominio van juntas en el record `OriginalAmount`, y
`Transaction` lo lleva nulable.

### 7. Reglas de moneda en el PATCH

`currency_code` no es modificable, y el saldo se mueve en la moneda de cada cuenta. Por eso:

- **Cuenta origen nueva:** tiene que estar en la moneda del movimiento. Si no, 400 en `accountId`.
- **Destino resultante en la moneda del movimiento:** `destination_amount` pasa a null. Un
  `destinationAmount` en el parche es 400.
- **Destino resultante en otra moneda:** vale el `destinationAmount` del parche. Si el parche no lo
  trae, se conserva el guardado, siempre que el destino guardado estuviera en esa misma moneda. Si no
  hay ninguno aplicable, es 400 en `destinationAmount`.
- **Tipo resultante que no es transferencia:** `destination_amount` pasa a null. Un
  `destinationAmount` en el parche es 400.

El UPDATE pasa a escribir `destination_amount`. Hoy no lo toca, y pasar una transferencia entre
monedas a gasto rompería `ck_transactions_destination_amount`.

### 8. Sin ninguna tasa: 502

Si `resolve` responde 404 porque el par no tiene ninguna tasa, el alta responde 502
`EXTERNAL_SERVICE_ERROR` en `server`. Es lo mismo que FA-122 hace con FX001. Solo pasa con la base
sin tasas de una de las dos monedas y el proveedor caído.

**Descartado:** el 404 de `resolve` tal cual. Su campo es `date`, que en un lote no dice a qué
elemento se refiere.

### 9. Las tasas de un lote se piden una vez por par y fecha

Un lote de 500 elementos convertidos no hace 500 lecturas iguales: el caso de uso memoriza cada
`resolve` por (from, to, fecha) dentro del lote.

### 10. Series y cuotas siguen en COP

`InstallmentPlan` redondea a pesos enteros, y una serie en otra moneda necesita reglas propias para
cambiar de cuenta. `ReferenciasDelUsuario.cuentaPropia` deja de mirar la moneda. La regla de COP pasa
a un método aparte que usan las series y las cuotas, con un mensaje que nombra la limitación.

### 11. Base local reconstruida desde cero

Por pedido del usuario, la base local no recibe el update: se reconstruye desde cero.

```powershell
psql -c "DROP SCHEMA finance CASCADE"
psql -f schema.sql
psql -f seed.sql
psql -f cargar-datos-local.sql
```

Así no arrastra datos viejos. El update se escribe igual, para Neon, y se valida con la comparación
de esquemas de `modelo-datos.md`. Es aditivo: dos columnas nulables y un CHECK. Va antes del
despliegue de la app, porque la app nueva escribe y lee las columnas.

## Risks / Trade-offs

- **La tasa interna no coincide con el cargo del banco.** Para eso queda pendiente y se ajusta con
  PATCH.
- **El PATCH de un convertido cambia `amount` pero no el original.** Es intencional: el original es
  el registro de lo recibido.
- **Los montos convertidos dependen de `decimal_places`.** Una moneda mal configurada en el catálogo
  daría redondeos raros.
