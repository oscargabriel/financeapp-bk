# Design

## Context

FA-122 dejó la moneda base como pura presentación: cada consulta de totales (`MontoDeLaPersona`, las
vistas de gasto mensual, `Asistente*R2dbcAdapter`) lee `users.base_currency_code` y convierte con
`finance.in_currency`. Cambiar la columna basta para los movimientos. Dos huecos lo impiden hoy (ver
proposal.md, *Why*):

- `finance.in_currency` multiplica por `finance.usd_rate(destino, fecha)`, que cae a la fila más
  antigua si no hay anterior, pero devuelve `NULL` si la moneda no tiene ninguna fila.
- `finance.budgets` guarda `currency_code`, y las vistas comparan `budgets.amount` sin convertirlo.

No hay endpoint que cree metas: entran por `test-data.sql` y `demo-data.sql`, todas en COP para
personas en COP.

## Goals / Non-Goals

**Goals:**
- El cambio reusa la resolución de tasas de `GET /api/exchange-rates`, con su límite de consultas.
- La conversión de la meta vive en la base, junto a la de los movimientos.

**Non-Goals:**
- Que las consultas de totales manejen una moneda sin tasa: el cambio lo impide antes de guardar.

## Decisions

### 1. Orden de las validaciones

Formato (Bean Validation, todos los campos a la vez) → parche vacío (controlador) → usuario activo →
moneda activa → contraseña y unicidad del correo → tasa → `UPDATE`.

- La moneda va antes que la contraseña por la misma razón que en el alta: los 400 de lo que se decide
  con el catálogo salen antes que los de credenciales, y no consultan nada sensible.
- La tasa va al final porque es la única que puede salir a la red. Consultar al proveedor para un
  parche que después se rechaza por la contraseña gastaría la consulta del día (FA-121).

Descartado: resolver la tasa antes de la contraseña. Daría un 502 a quien solo se equivocó de clave
mientras el proveedor está caído, y un proveedor caído no debería ocultar un error del usuario.

### 2. La tasa se resuelve con `ResolveExchangeRatePort`

`UpdateUserProfileUseCase` pide `resolve("USD", nueva, hoy)`, con hoy del `Clock` de la app, y
traduce el 404 `NOT_FOUND` del par sin tasa a 502 `EXTERNAL_SERVICE_ERROR` en `server`, como ya hace
`ConversorDeMontos` en el alta de movimientos. El resultado se descarta: solo importa que exista una
fila, porque `usd_rate` usará la de cada fecha.

- `resolve` ya consulta al proveedor solo si falta la fila de hoy, guarda todas las monedas activas y
  respeta `ConsultasAlProveedor`. Para `USD` devuelve 1 sin tocar la base.
- `resolve` vuelve a validar que la moneda esté activa (en `to`): es una consulta de más, a cambio de
  no abrir una segunda forma de resolver tasas.

Descartado: `refreshToday` más `ExchangeRateRepositoryPort.findLatestFromUsd` desde el caso de uso.
Duplica la regla de `cruzar` y acopla el perfil al puerto de salida de otro caso de uso.

### 3. La misma moneda no consulta nada

Si la moneda normalizada es la actual, el caso de uso la trata como ausente: no valida contra el
catálogo ni busca la tasa. Es la misma regla del correo: el mismo correo en otra caja no exige la
contraseña. Un cliente que reenvía el perfil completo no paga una consulta por un campo que no cambió.

### 4. `finance.amount_in_currency` para las metas

Función nueva, `(p_target, p_currency, p_amount, p_on)`:

- `p_currency = p_target` → `p_amount`, sin redondeo de ida y vuelta.
- si no → `round(p_amount / usd_rate(p_currency, p_on) * usd_rate(p_target, p_on), 4)`, con un solo
  redondeo al final. `usd_rate('USD', …)` ya vale 1.

Las dos vistas la llaman con `b.period_month`, el día 1 del mes de la meta. `period_month` ya es una
fecha local: no lleva zona.

Descartado:
- **Reusar `in_currency`** pasándole `amount / usd_rate(...)` como `amount_base`: redondearía dos
  veces y escondería la tasa de origen en el llamador.
- **Reescribir las metas al cambiar la moneda**: el criterio del issue lo prohíbe, y además
  reconvertiría con la tasa de hoy metas de meses pasados.
- **Convertir en Java**: `MonthlySpendingR2dbcAdapter` lee la vista; partir el cálculo entre la vista
  y el adapter dejaría `remaining` y `percentUsed` de la vista mal para quien la consulte por psql.

### 5. La verificación de la meta en otra moneda

Ningún endpoint crea metas, así que `test-data.sql` es el que pone una en otra moneda: la meta
global del mes siguiente de `prueba@` pasa de 2.000.000 COP a 500 USD, y el tope de Viajes del mes en
curso de 400.000 COP a 100 USD. El día 1 del mes siguiente la tasa vigente es la de hoy (4100) y el
día 1 del mes en curso es la de hace 35 días (4100): los dos resultados son fijos sea cual sea el día
de la carga, 2.050.000 y 410.000.

El mes siguiente lo verifica `bruno/monthly-spending/gasto-mensual-mes-con-meta-sin-movimientos`. El
desglose por categoría no tiene endpoint: lo verifica una consulta a la vista con psql en el grupo de
verificación.

## Risks / Trade-offs

- [Una moneda queda con tasas muy viejas: si el proveedor deja de traerla, `usd_rate` usa la última
  que hubo] → Es la regla vigente de `tasas-de-cambio` para cualquier fecha; el cambio de moneda no la
  empeora.
- [Una meta en una moneda sin ninguna tasa da `budgetAmount` `null`] → Solo pasa con una meta cargada
  a mano en una moneda que nunca tuvo tasa. Las del escenario y la demo están en COP o USD.
- [El cambio de moneda a la vez que el proveedor responde lento alarga el `PATCH` hasta el timeout del
  proveedor] → El mismo costo que ya tiene el alta de movimientos en otra moneda.

## Migration Plan

1. Aplicar `docs/database/update/20261012_02_metas_en_moneda_de_la_persona.sql` en local y en Neon.
   Solo crea la función y reemplaza las dos vistas con las mismas columnas: la app vieja con la vista
   nueva sigue igual, y la nueva no nombra nada nuevo. Puede ir antes o después del despliegue.
2. En Neon las metas de la demo están en la moneda de su persona: los números no cambian.
3. Reversión: el bloque comentado al final del update restaura las vistas anteriores y borra la
   función. La app no la necesita para arrancar.
