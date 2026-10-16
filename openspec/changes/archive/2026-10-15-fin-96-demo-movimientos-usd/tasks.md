# Tasks

Ningún grupo toca la tabla del paso 5 de `tareas-jira`: no se cargan skills de dominio. No hay
requests de `bruno/` nuevos ni cambiados, así que nada que replicar en `bruno-personal/`.

## 1. Movimientos en USD en `demo-data.sql`

- [x] 1.1 Antes de tocar el script, con la demo cargada (`cargar-datos-local.sql`) y la app local, guardar en el scratchpad las respuestas de `GET /api/accounts`, `GET /api/recurrences` y `GET /api/installment-purchases` de `demo@`: son la línea base de 2.1
- [x] 1.2 Agregar la tarjeta «Visa en dólares» y los ingresos y gastos en USD de la cuenta en dólares de *Escenario* en `design.md`; verificar cargando `cargar-datos-local.sql` sin errores y con `check-saldos.sql` sin descuadres
- [x] 1.3 Agregar las tres transferencias entre monedas, la serie en USD, la compra en cuotas en USD y el gasto convertido y aprobado en la Mastercard, con los montos convertidos calculados con `finance.amount_in_currency`; verificar cargando `cargar-datos-local.sql` dos veces seguidas sin errores
- [x] 1.4 Con una consulta sobre `finance.transactions`, comprobar que el saldo de la cuenta en dólares, acumulado por fecha, no baja de 1.000 USD en ningún momento

## 2. Comparación con el API

- [x] 2.1 Con la app local y `demo@`: `GET /api/accounts` trae la cuenta en dólares con su saldo nuevo y la «Visa en dólares» en USD con la deuda de las cuotas vencidas, y las demás cuentas iguales a la línea base salvo la nómina (transferencias) y la Mastercard (gasto convertido); `GET /api/recurrences` trae la serie en USD con `currencyCode` `USD` y su próxima ocurrencia el 12; `GET /api/installment-purchases` trae el celular con `currencyCode` `USD` y sus cuotas por venir
- [x] 2.2 `GET /api/reports/transactions` sobre los meses de cada caso trae las transferencias con `destinationAmount` y el gasto convertido con su `amount` en COP; su `original_amount` 45.99 y `original_currency_code` `USD` se comprueban en `finance.transactions`, porque el reporte no expone esos dos campos (ver *Fuera de alcance* en `proposal.md`); `GET /api/monthly-spending` de un mes con gastos en USD los suma convertidos a COP
- [x] 2.3 Lo mismo como `dev@`, con los ids de `u = 1`
- [x] 2.4 Base sin `test-data.sql` (FIN-45): crear una base desechable en el PostgreSQL local, cargar `schema.sql`, `seed.sql` y `demo-data.sql`, correr `check-saldos.sql` sin descuadres, y borrarla

## 3. Documentación

- [x] 3.1 Encabezado de `demo-data.sql`: los casos en USD (cuenta en dólares, tarjeta en USD con su compra en cuotas, serie, transferencias entre monedas y el gasto convertido) y que los montos convertidos salen de la tasa de la base; el comentario de *Cuentas* ya no dice que la cuenta en dólares no tiene movimientos ni que el API solo acepta COP

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con el conteo real de requests y `check-saldos` sin descuadres
- [x] 4.3 `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql` corre de punta a punta

## Workflow follow-up

- Archivar el change en la rama y entregar según `tareas-jira`.
