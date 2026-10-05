# Proposal

Origen: FA-28, "Filtro por cuenta en el reporte de movimientos". La tarea nació como
`GET /api/transactions — consulta con filtros`. El 04-10-2026 se recortó a lo que FA-63 no cubrió:
FA-63 ya entregó `GET /api/reports/transactions` con rango de días por la zona del usuario, filtros
de categoría y tipo, orden descendente y aislamiento por usuario. La paginación quedó fuera de esta
etapa por decisión del usuario, y se registró como FA-64 para evaluarla con el front.

## Why

El reporte deja ver los movimientos por categoría o por tipo, pero no los de una cuenta. Hoy no se
puede revisar qué pasó en un mes en la tarjeta de crédito o en el efectivo, ni conciliar una cuenta
contra su extracto, sin descargar todo el mes y filtrar a mano.

## What Changes

- `GET /api/reports/transactions` acepta un parámetro opcional nuevo, `accountId`, con la misma forma
  que `categoryId`: uno o varios UUID, repitiendo el parámetro o separados por coma.
- Con el filtro, el reporte trae los movimientos en los que la cuenta es el **origen o el destino**:
  una transferencia que entra a la cuenta también aparece. Sale una sola vez aunque las dos cuentas
  estén en el filtro.
- Los totales no cambian de regla: se calculan sobre la lista filtrada, con el `amountBase` de cada
  movimiento y sin signo según el sentido de la transferencia.
- Un `accountId` que no es UUID responde 400 `VALIDATION_ERROR` en el campo `accountId`. Uno bien
  formado que no es del usuario no encuentra nada: 200 con la lista vacía.
- Sin `accountId`, el reporte se comporta exactamente igual que hoy.
- `docs/api/contrato-api.md` documenta el parámetro.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `reportes`: se agrega el requisito del filtro opcional por cuentas, y el de validación suma el
  escenario del `accountId` mal formado.

## Impact

- **Código:** `TransactionReportController`, `GetTransactionReportPort` y `GetTransactionReportUseCase`
  (un conjunto más de ids), `TransactionReportFilter`, y el SQL de `TransactionReportR2dbcAdapter`.
  No cambia el esquema ni la respuesta.
- **API:** `GET /api/reports/transactions` gana un query param opcional. Es compatible con los
  clientes actuales.
- **Bruno:** requests nuevos en `bruno/reports/` y el parámetro agregado en
  `bruno-personal/reportes/movimientos.yml`.

## Fuera de alcance

- **Paginación del reporte:** se evalúa con el front, en FA-64.
- **Un endpoint `GET /api/transactions` aparte:** el filtro va sobre el reporte existente.
- **Extracto por cuenta**, con signo según entre o salga dinero y saldo acumulado: es otro reporte,
  no un filtro. Si hace falta, va a una tarea nueva.
