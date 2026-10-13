# Tasks

## 1. Bruno en rojo

Skills: `bruno-cli`.

- [x] 1.1 En `bruno/reports/reporte-mes.yml`, sumar `destinationAmount` a las claves de cada movimiento y agregar los tests: el cambio de dólares trae `destinationAmount` 410000, el retiro de cajero `null`, y todos los gastos e ingresos `null`. Verificar que falla contra la app de `dev` (`bru run reports/reporte-mes.yml --env local`, con la app levantada a mano) por la clave que falta
- [x] 1.2 Revisar en `bruno-personal/` el request del reporte de movimientos: el cambio solo agrega asserts, sin tocar ruta, cuerpo ni autenticación, así que no debería requerir cambios. Verificar leyéndolo, sin ejecutarlo

## 2. El campo en el reporte

Skills: `java-architect`.

- [x] 2.1 `ReportedTransaction` gana `destinationAmount`; `ReportMother` lo pone en `unaTransferenciaEnDolares` (410000) y en `null` en el resto. Test en rojo primero: `GetTransactionReportUseCaseTest` comprueba que el reporte conserva el `destinationAmount` que trae el puerto
- [x] 2.2 `TransactionReportResponse.Item` expone `destinationAmount`. Test en rojo primero: `TransactionReportControllerTest.devuelveElReporteConElFormatoDelContrato` exige el campo (410000 en la transferencia entre monedas y `null` en el gasto)
- [x] 2.3 `TransactionReportR2dbcAdapter` lee `t.destination_amount` en la consulta y lo mapea. Se verifica con `bruno/reports/reporte-mes.yml` del grupo 1 (la suite no tiene base)
- [x] 2.4 `docs/api/contrato-api.md`: `destinationAmount` en el ejemplo del reporte (410000 en el cambio de dólares, `null` en el gasto), una fila en su tabla de campos, y la frase de "toda respuesta de un movimiento" precisada para el reporte, que trae `destinationAmount` pero no `originalAmount` ni `originalCurrencyCode`. Verificar releyendo la sección contra la respuesta real de 1.1

## 3. Verificación

- [x] 3.1 `.\gradlew.bat build` en verde, con el conteo real de tests y la cobertura por encima del umbral
- [x] 3.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (colección completa y `check-saldos.sql`), con los conteos reales
