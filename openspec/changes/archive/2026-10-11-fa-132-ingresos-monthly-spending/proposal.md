## Why

FA-132, pedida desde la raíz de coordinación el 11-10-2026 con este texto: «en la grafica que no solo
sea gastos sino 2 columnas, ingresos y egresos». La gráfica de Inicio del front
(`src/app/inicio/inicio-api.ts`) pide `GET /api/monthly-spending` para una ventana de 6 meses, y ese
endpoint devuelve por mes solo `totalSpent`: no hay de dónde sacar los ingresos de cada mes. La
bloquea FA-140, del front.

## What Changes

- `GET /api/monthly-spending` agrega a cada mes `totalIncome`: la suma de los ingresos (`INCOME`)
  confirmados del mes que ya ocurrieron, en la moneda de la persona, con las mismas reglas que
  `totalSpent` (mes cortado en la zona horaria del usuario, conversión de `amountBase`, sin
  transferencias, pendientes ni programados).
- Un mes que tiene ingresos y ningún gasto ni meta pasa a venir, con `totalSpent` 0,
  `transactionCount` 0 y la meta en `null`. Hoy ese mes no aparece.
- Los demás campos no cambian de nombre ni de significado. `transactionCount` sigue contando solo
  los gastos. No es un cambio incompatible: el front actual lee `periodMonth` y `totalSpent`, ya
  rellena con cero los meses que faltan, y un campo nuevo en el JSON no lo afecta.
- La vista `finance.v_monthly_spending` agrega la columna `total_income`, con su update en
  `docs/database/update/`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: el gasto mensual trae los ingresos de cada mes y lista los meses que solo tienen
  ingresos; los pendientes, los programados y la regla de moneda alcanzan también a `totalIncome`.

## Impact

- Base: `finance.v_monthly_spending` en `schema.sql` y un update nuevo que solo reemplaza la vista.
  No toca tablas, así que no reinicia los datos locales. En Neon va **antes** del despliegue: la
  app nueva lee la columna, y la vieja ignora una columna de más.
- Dominio y web: `MonthlySpending`, `MonthlySpendingResponse` y `MonthlySpendingR2dbcAdapter`
  llevan el campo nuevo. El puerto, el caso de uso y el controlador no cambian.
- Bruno: `bruno/monthly-spending/gasto-mensual.yml` comprueba los ingresos del escenario, y el flujo
  de la persona en USD de `bruno/reports/` agrega ingresos en pesos para probar la conversión, el mes
  con solo ingresos y el ingreso programado. Se replica en `bruno-personal/reportes/gasto-mensual.yml`.
- Documentación: `docs/api/contrato-api.md` (sección `GET /api/monthly-spending`) y
  `docs/database/modelo-datos.md` (vista `v_monthly_spending`).

## Fuera de alcance

- Un conteo de ingresos por mes: el front solo pidió los totales. Si hace falta, va a una tarea
  nueva.
- Renombrar el endpoint (`monthly-spending` ya no describe todo lo que trae): rompería al front
  actual a cambio de nada.
- Las metas de ingreso: las metas siguen siendo de gasto.
- La gráfica del front: es FA-140.
