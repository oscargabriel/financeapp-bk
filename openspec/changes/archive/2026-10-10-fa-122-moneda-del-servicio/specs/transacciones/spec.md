## ADDED Requirements

### Requirement: Equivalente en USD de cada movimiento
Todo movimiento SHALL guardar `exchange_rate` y `amount_base`:
- `exchange_rate` es la tasa `USD→moneda del movimiento` de su fecha. La fecha es la de
  `occurredAt` en la zona del usuario, y la tasa se elige con la regla de `tasas-de-cambio`: la más
  reciente ≤ la fecha o, si no hay, la más antigua.
- `amount_base` es `amount / exchange_rate`, en USD, con 4 decimales y un mínimo de 0.0001.

Un movimiento en USD SHALL tener `exchange_rate` 1. El cálculo SHALL rehacerse solo cuando cambian el
monto, la moneda o la fecha. Aprobar un pendiente o modificar otros campos MUST NOT cambiar su tasa.
La regla vale para todo camino que escribe movimientos: alta en lote, asistente, modificación, series
y cuotas.

#### Scenario: Gasto en COP
- **WHEN** la tasa `USD→COP` de la fecha del movimiento es 4100 y se registra un gasto de 41000 COP
- **THEN** el movimiento queda con `exchange_rate` 4100 y `amount_base` 10

#### Scenario: Fecha anterior a todas las tasas
- **WHEN** la tasa `USD→COP` más antigua es la de hace 45 días, en 3900, y se registra un gasto de 39000 COP con fecha de hace 50 días
- **THEN** el movimiento queda con `exchange_rate` 3900 y `amount_base` 10

#### Scenario: Modificar la descripción
- **WHEN** después de registrar un movimiento se guarda una tasa nueva para su fecha y se le cambia solo la descripción
- **THEN** su `exchange_rate` y su `amount_base` no cambian

#### Scenario: Modificar el monto
- **WHEN** se le cambia el monto a un movimiento
- **THEN** `amount_base` se recalcula con la tasa de su fecha

#### Scenario: Moneda sin ninguna tasa
- **WHEN** la moneda del movimiento no tiene ninguna fila en `exchange_rates`
- **THEN** la escritura se rechaza con 502 `EXTERNAL_SERVICE_ERROR` en el campo `server`, sin detalle interno, y no se guarda nada

### Requirement: Tasa del día al registrar
El alta en lote SHALL pedir al proveedor la tasa de hoy, una vez y solo si falta, cuando algún
elemento tiene fecha de hoy o posterior en la zona de la app y su moneda no tiene fila de hoy. Un
fallo del proveedor MUST NOT rechazar el alta: el movimiento usa la última tasa guardada.

#### Scenario: Falta la tasa de hoy
- **WHEN** no hay `USD→COP` de hoy y se registra un gasto con fecha de hoy
- **THEN** se consulta al proveedor una vez antes de guardar, y el movimiento usa la tasa de hoy

#### Scenario: Proveedor caído
- **WHEN** no hay `USD→COP` de hoy, existe la de ayer, el proveedor falla y se registra un gasto de hoy
- **THEN** el alta responde 201 y el movimiento usa la tasa de ayer

#### Scenario: Solo fechas pasadas
- **WHEN** todos los elementos del lote tienen fecha anterior a hoy
- **THEN** no se consulta al proveedor
