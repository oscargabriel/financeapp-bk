# Design

## Context

`occurredAt` viaja como texto desde `CreateTransactionRequest` hasta `TransactionBatchValidator`,
que lo convierte con `OffsetDateTime.parse(...).toInstant()`. Hoy es obligatorio por `@NotBlank`;
`@FechaConOffset` ya da por válido el vacío y solo revisa el formato cuando hay contenido.
`CreateTransactionsUseCase` ya recibe el bean `Clock` y lo usa para generar los UUID v7.

## Goals / Non-Goals

**Goals:**
- Que la hora por defecto se pueda probar con un reloj fijo, sin depender de la hora real.

**Non-Goals:**
- Cambiar el tipo de `occurredAt` en el comando o en la respuesta.

## Decisions

### El caso de uso pone la fecha por defecto, no el DTO ni la base

El caso de uso toma el instante del `Clock` y se lo pasa al validador, que lo usa para los elementos
sin fecha.

- **Descartado: completarlo en `CreateTransactionRequest.toCommand()`.** El DTO no tiene el `Clock`,
  así que usaría `Instant.now()`, que no se puede fijar en un test. Además, poner un valor por
  defecto es una regla de negocio, y en este proyecto esas viven en el caso de uso.
- **Descartado: `DEFAULT now()` en `transactions.occurred_at`.** Obliga a tocar `schema.sql` y a
  emitir un update. El adapter tendría que omitir la columna o leerla de vuelta para que la respuesta
  devuelva la fecha. Y `now()` es la hora de la base, no la del `Clock` de la app.

### Un instante por lote, no uno por elemento

Se lee `clock.instant()` una sola vez por petición. El lote es una sola acción del usuario: si los
movimientos sin fecha tuvieran microsegundos distintos, el orden entre ellos dependería de cuánto
tardó el bucle, no de lo que hizo el usuario. Además, la prueba con reloj fijo no cambia.

### El comando sigue llevando la fecha como texto

`CreateTransactionCommand.occurredAt` sigue siendo `String` y ahora admite `null`. El validador
decide: si está en blanco usa el instante del lote, y si no, lo convierte. La alternativa,
`Optional<Instant>` en el comando, movería la conversión al controlador y rompería la regla del
proyecto de reportar un valor mal formado como error de su campo con índice.

## Risks / Trade-offs

- **Bruno no puede fijar el reloj de la app.** → El request comprueba que la fecha asignada quede
  dentro de una ventana alrededor de la hora en que se envió, y que los elementos sin fecha del lote
  compartan exactamente el mismo valor.
- **El request nuevo crea movimientos con la fecha de hoy.** Los asserts de saldo de la carpeta y
  los de `monthly-spending` podrían cambiar. → Va al final de `bruno/transactions/`, después de los
  checks de saldo, y usa el usuario que la carpeta crea para sí misma, no el del escenario de
  `test-data.sql`.
