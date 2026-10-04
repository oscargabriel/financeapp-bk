# Proposal

Origen: FA-60. El encargo llegó por chat el 04-10-2026, en el mismo mensaje que los catálogos
(FA-59): *"y "occurredAt": "2026-10-03T12:00:00-05:00" si no viene una fecha usar la fecha del
momento de ejecucion del endpoint"*. Se registró como tarea aparte porque es otro resultado.

## Why

Hoy `occurredAt` es obligatorio en cada elemento de `POST /api/transactions`: un elemento sin fecha
sale como 400. Para el caso más común, anotar un gasto en el momento en que ocurre, el cliente tiene
que construir una fecha ISO-8601 con offset que el servidor podría poner por su cuenta.

## What Changes

- `occurredAt` deja de ser obligatorio. Si un elemento llega sin fecha (ausente, `null`, vacío o
  solo espacios), se guarda con el instante en que el servidor atiende la petición.
- Todos los elementos sin fecha de un mismo lote reciben el mismo instante.
- Un `occurredAt` presente se valida igual que hoy: ISO-8601 con offset, o 400 en `[i].occurredAt`.
- La respuesta no cambia de forma: cada movimiento devuelve su `occurredAt` en UTC, también el
  asignado.
- El escenario de JUnit que esperaba "La fecha es obligatoria" cambia de sentido.

No es un cambio incompatible: todo cuerpo que hoy se acepta se sigue aceptando con el mismo
resultado. Solo pasa a aceptarse lo que antes era un 400.

## Capabilities

### New Capabilities

- `transacciones`: el alta de movimientos en lote. Este change crea la spec solo con lo que toca
  (la fecha del movimiento y la autenticación del endpoint); el resto del contrato del alta (FA-26,
  FA-27) se incorpora cuando un change lo modifique.

### Modified Capabilities

Ninguna: `openspec/specs/` todavía no tiene specs.

## Fuera de alcance

- Aceptar fechas sin offset: siguen siendo un 400.
- Rechazar fechas futuras o demasiado antiguas: hoy no hay regla y este change no la introduce.
- Cualquier otro cambio del alta: monedas distintas de COP (FA-51), idempotencia, tope del lote.

## Impact

- `CreateTransactionRequest`: `occurredAt` pierde `@NotBlank`; `@FechaConOffset` ya acepta vacío.
- `CreateTransactionsUseCase` y `TransactionBatchValidator`: toman un instante del `Clock` por lote y
  lo usan para los elementos sin fecha.
- Tests: `CreateTransactionRequestTest`, `CreateTransactionsUseCaseTest`, `TransactionControllerTest`.
- `bruno/transactions/`: un request nuevo para el lote sin fecha; replicado en
  `bruno-personal/movimientos/`.
- Sin cambios en la base ni en la configuración.
