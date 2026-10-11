## Context

`GET /api/monthly-spending` lee `finance.v_monthly_spending`: un CTE `spent` suma los `EXPENSE`
confirmados y ya ocurridos por mes local con `finance.in_currency`, y un `FULL OUTER JOIN` con las
metas globales hace aparecer los meses que solo tienen meta. El adapter R2DBC mapea la fila al
record `MonthlySpending`, y el DTO la copia campo por campo. El puerto, el caso de uso y el
controlador no saben qué columnas trae la fila.

## Decisions

### 1. Ampliar `GET /api/monthly-spending` en vez de crear un endpoint

Se agrega `totalIncome` a cada mes del endpoint que el front ya usa.

- **Descartado: un endpoint nuevo** (`GET /api/reports/monthly`, por ejemplo) con ingresos y gastos.
  Repetiría el rango, la validación, el corte por zona horaria y la puesta al día de las series que
  ya tiene este, y el front tendría que mezclar dos respuestas para dibujar las dos columnas.
- **Descartado: renombrar el endpoint.** El nombre ya no describe todo, pero cambiarlo rompe al
  front actual a cambio de nada.

El campo se llama `totalIncome`, en paralelo con `totalSpent`. No se agrega un conteo de ingresos:
nadie lo pidió.

### 2. Un solo CTE con agregación condicional

`spent` pasa a filtrar `type IN ('EXPENSE', 'INCOME')` y saca las dos sumas con `FILTER`:

```sql
SUM(finance.in_currency(...)) FILTER (WHERE t.type = 'EXPENSE') AS total_spent,
SUM(finance.in_currency(...)) FILTER (WHERE t.type = 'INCOME')  AS total_income,
COUNT(*) FILTER (WHERE t.type = 'EXPENSE')                      AS transaction_count
```

- **Descartado: un segundo CTE para los ingresos** con su propio `FULL OUTER JOIN`. Duplica el
  `WHERE` de estado, fecha y zona horaria, y dos copias de esa regla terminan separándose. Con un
  solo `WHERE`, un ingreso pendiente o programado queda fuera por la misma línea que ya deja fuera
  al gasto pendiente o programado.

Un mes con solo ingresos sale del CTE con `total_spent` en `NULL`, y el `COALESCE(…, 0)` que ya
existe lo deja en 0, así que la meta, `remaining` y `percent_used` siguen igual que hoy.
`total_income` se agrega como **última columna** de la vista: `CREATE OR REPLACE VIEW` solo admite
columnas nuevas al final, y así el update no necesita `DROP VIEW` ni tocar
`v_monthly_spending_by_category`.

### 3. Verificación de los ingresos en Bruno con la persona en USD

El escenario de `test-data.sql` ya tiene salarios en los tres meses: alcanza para comprobar
`totalIncome` en COP sin cambiar el escenario. Los casos que faltan (la conversión desde otra
moneda, el mes con solo ingresos y el ingreso programado) se montan sobre la persona en USD de
`bruno/reports/`, que la colección crea en cada corrida, con requests nuevos después de los que hoy
verifican sus totales. Agregar un mes con solo ingresos al usuario de `test-data.sql` cambiaría el
histórico y los totales que verifican muchos otros requests.

El ingreso **pendiente** no tiene request propio: el API solo crea pendientes desde el asistente, y
agregar uno a `pendientes@` cambiaría las listas que verifica `bruno/pending/`. Lo cubre la
decisión 2: es el mismo filtro de estado que ya prueba «Gasto mensual con un pendiente».

## Risks / Trade-offs

- **Orden de despliegue en Neon.** La app nueva lee `total_income` y falla si la vista no la tiene;
  la vieja ignora una columna de más. Por eso el update va antes del despliegue, y su encabezado lo
  dice.
- **Un mes nuevo en la respuesta.** Un mes con solo ingresos antes no venía y ahora viene con
  `totalSpent` 0. El front actual ya rellena con cero los meses ausentes, así que lo dibuja igual.
