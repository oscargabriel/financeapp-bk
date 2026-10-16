# Tasks

Ningún grupo toca la tabla del paso 5 de `tareas-jira`: no se cargan skills de dominio. No hay
requests de `bruno/` nuevos ni cambiados, así que nada que replicar en `bruno-personal/`.

Cada carga se hace con `psql -U postgres -d financeapp -f docs/database/cargar-datos-local.sql`.

## 1. Doce meses en `demo-data.sql`

- [x] 1.1 Rutina por plantilla: las diez filas de *La rutina sale de una plantilla* en `design.md`, cruzadas con los meses `0..11` sin el 3 y con la variación fija; borrar las filas a mano de los meses 0 a 5 que son rutina, y aplicar la misma variación a «Luz, agua y gas». Se verifica cargando sin errores
- [x] 1.2 Fijos, transferencias fijas y comisión en USD en los meses `0..11` sin el 3, con el salario y el arriendo viejos en los meses 8 a 11 y el retiro de cajero en 150.000; los extraordinarios de los meses 6 a 11 y los USD nuevos, incluida la compra de dólares del mes 9. Se verifica cargando sin errores y con `check-saldos.sql` sin descuadres
- [x] 1.3 Nube desde hace once meses, la nevera en cuotas con la categoría por compra, metas globales de los meses 6 a 11, usuario y tasa de respaldo fechados el día 1 de hace doce meses. Se verifica cargando sin errores
- [x] 1.4 Lo compartido: G7 y G8 con sus partes pagadas y sus movimientos, y las fechas nuevas de creación y afiliación de Carlos y Valentina. Se verifica con el resumen de personas que imprime el script: los saldos pendientes por persona iguales a los de antes del cambio
- [x] 1.5 Invariantes de *Invariantes de saldo* en `design.md`, con una consulta sobre `finance.transactions` que acumule por fecha el saldo de cada cuenta de `demo@`; ajustar lo que falle y anotarlo en `design.md`
- [x] 1.6 El resumen por mes que imprime el script: los meses 2 a 11 sin el 3 entre 26 y 36 movimientos, el 1 con 40 (la excepción de *Extraordinarios de los meses 6 a 11* en `design.md`), el 3 con los de hoy, y nada antes del mes 11

## 2. Recarga y API

- [x] 2.1 Cargar dos veces seguidas y comparar un `md5` del estado de `demo@` y `dev@` (cuenta, tipo, monto, moneda, fecha, descripción, estado, de todos sus movimientos, partes, series, cuotas y metas, sin ids ni `created_at` de las filas): el mismo en las dos cargas
- [x] 2.2 Con la app local y `demo@`: `GET /api/accounts` trae los saldos de la base; `GET /api/reports/transactions` del mes 11 trae sus movimientos; `GET /api/recurrences` trae la nube con su primera ocurrencia hace once meses; `GET /api/installment-purchases` no trae la nevera, porque solo lista las compras con cuotas por venir (`docs/api/cuotas.md`), y sus seis cuotas salen en `GET /api/reports/transactions`; `GET /api/affiliations/balances` trae lo mismo que antes del cambio
- [x] 2.3 Base sin `test-data.sql` (FIN-45): crear una base desechable en el PostgreSQL local, cargar `schema.sql`, `seed.sql` y `demo-data.sql`, comprobar que la tasa de respaldo queda antes del movimiento más antiguo y que `check-saldos.sql` no lista descuadres, y borrarla

## 3. Documentación

- [x] 3.1 Encabezado de `demo-data.sql`: doce meses con unos 30 movimientos por mes, la rutina por plantilla con variación fija, el mes 3 de pocos movimientos y la tasa de respaldo de hace doce meses; los comentarios de cada bloque con los meses y las fechas nuevas
- [x] 3.2 `AGENTS.md` (*Base de datos*, párrafo de `demo-data.sql`) y `docs/despliegue.md` (tasa de respaldo de la demo) dicen doce meses en vez de seis

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo real de tests
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (corre `bru run . -r --env local`), con el conteo real de requests y `check-saldos` sin descuadres
- [x] 4.3 `cargar-datos-local.sql` corre de punta a punta

## Workflow follow-up

- Archivar el change en la rama y entregar según `tareas-jira`.
