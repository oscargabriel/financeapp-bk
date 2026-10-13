# Proposal

Origen: FIN-63. Surgió el 13-10-2026 al tomar FIN-34 (Frontend), que queda bloqueada por esta.

## Why

La edición de una transferencia entre monedas distintas tiene que mostrar el monto que entró al
destino, y desde Mis Gastos el front la abre con los datos de `GET /api/reports/transactions`, que no
lo trae. No hay un `GET /api/transactions/{id}` para pedirlo aparte, y `contrato-api.md` afirma que
`destinationAmount` va "en toda respuesta de un movimiento", lo que hoy no es cierto en el reporte.

## What Changes

- Cada elemento de `transactions` en `GET /api/reports/transactions` trae `destinationAmount`: lo que
  entró al destino, en la moneda del destino, en una `TRANSFER` entre cuentas de monedas distintas, y
  `null` en todo lo demás (gastos, ingresos y transferencias en la misma moneda). Es el valor guardado
  en el movimiento, sin convertir.
- `docs/api/contrato-api.md` lo muestra en el ejemplo del reporte, lo explica en su tabla de campos y
  precisa qué campos de "toda respuesta de un movimiento" trae el reporte.
- Campo nuevo en la respuesta, sin quitar ni renombrar nada: no rompe a los clientes actuales.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: el requisito *Cada movimiento del reporte* suma `destinationAmount` a los campos de cada
  elemento de `transactions`.

## Impact

- `core`: `ReportedTransaction`, la consulta de `TransactionReportR2dbcAdapter` y el `Item` de
  `TransactionReportResponse`. El caso de uso y los totales no cambian: `destinationAmount` no suma en
  ningún total, que siguen saliendo de `amountBase`.
- `bruno/reports/`: asserts nuevos sobre el escenario de `test-data.sql`, que ya tiene una
  transferencia entre monedas (100 USD → 410.000 COP) y una en la misma moneda.
- `docs/api/contrato-api.md`.
- Sin cambios de esquema ni de datos.

## Fuera de alcance

- `originalAmount` y `originalCurrencyCode` en el reporte: el issue no los pide y el front no los
  necesita para la edición. Si hacen falta, va otro issue.
- El `report` del asistente (`POST /api/assistant/messages`, intent `LIST_TRANSACTIONS`): tiene su
  propia forma y su propia consulta en el módulo `ia`, y no se toca. El contrato dice que tiene la
  forma de `GET /api/reports/transactions`, y no es así: lo corrige FIN-64.
- Un `GET /api/transactions/{id}`.
