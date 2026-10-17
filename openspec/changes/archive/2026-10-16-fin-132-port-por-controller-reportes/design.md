# Design

## Context

Ver proposal.md, *Why*. `TransactionReportController` tiene dos endpoints sobre tres ports. El
de `/reports/transactions` llama a `GetTransactionReportPort` y después a `DescribeSharingPort`
para agregar lo compartido a cada fila; el de `/reports/balance` llama solo a `GetBalancePort`.
`DescribeSharingPort` también lo usa `TransactionController`, y FIN-131 lo dejó aparte.

Los dos use cases comparten `SeriesAlDia` y `Clock`, y cada uno tiene un port de salida que hoy se
llama `query`: `TransactionReportQueryPort` en uno, `BalanceQueryPort` en el otro.
`GetBalanceUseCase` usa además `AccountQueryPort` (`accounts`).

Sus tests usan relojes distintos, y los dos importan:

| Test | Reloj | Qué depende de él |
|---|---|---|
| `GetTransactionReportUseCaseTest` (6 casos) | `2026-10-01T00:00Z`, UTC | `asOf` del reporte y que ningún movimiento de `ReportMother` salga programado |
| `GetBalanceUseCaseTest` (8 casos) | `2026-10-07T03:00Z`, `America/Bogota` | el mes en curso por la zona: en UTC ya es el 7, en Bogotá todavía el 6 |

Los dos son estrictos, con `lenient()` en el stub de `alDia` del `@BeforeEach`.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 en `TransactionReportController`.
- Dejar `TransactionReportPort` como el punto donde FIN-98 agrega el monto original.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.
- Tocar `DescribeSharingPort`, `TransactionController` o los ports de salida.

## Decisions

### `DescribeSharingPort` sigue aparte y el controller lo consume

`TransactionReportController` depende de `TransactionReportPort` y `DescribeSharingPort`, igual que
`TransactionController` tras FIN-131. Es el port que usan otros adapters, que la regla de
`AGENTS.md` deja aparte.

- Descartado: que `TransactionReportUseCase` consuma `DescribeSharingPort` y `transactions` devuelva
  el reporte con lo compartido. Cambia el retorno del dominio, que el refactor no toca, y lo
  compartido de un movimiento se armaría distinto en los dos controllers de movimientos.
- Descartado: una operación `describe` en `TransactionReportPort`. No es un endpoint, y
  duplicaría la de `DescribeSharingPort`.

### Operaciones con el nombre del método del controller

`transactions(...)` y `balance(...)`, como el criterio de FIN-131 (`pending`, `rejectBatch`). Los
dos ports de hoy se llaman `get`, que en un port con dos operaciones ya no dice cuál es.

- Descartado: `getTransactionReport` y `getBalance`. Repiten el nombre del port borrado y no siguen
  el criterio de los controllers ya migrados.

### Dependencias de `TransactionReportUseCase`

La unión de las de los dos use cases. Los dos `query` pasan a `reportes`
(`TransactionReportQueryPort`) y `saldos` (`BalanceQueryPort`); siguen `accounts`
(`AccountQueryPort`), `clock` y `alDia` (`SeriesAlDia`).

### Tests: un `@Nested` por operación, cada uno con su reloj

`TransactionReportUseCaseTest` une los dos tests con `@Nested` `Transacciones` y `Balance`. Los
mocks (`reportes`, `saldos`, `accounts`, `alDia`), el `@Captor` y el stub `lenient()` de `alDia` van
en la clase externa, estricta. Como los relojes de la tabla no se pueden unificar sin cambiar lo
que prueba cada caso, la clase externa expone `useCase(Clock)` y cada `@Nested` lo construye en su
`@BeforeEach` con su propio reloj, que conserva el nombre y el valor de su origen (`AHORA` y
`RELOJ`).

- Descartado: un solo reloj para los dos. El de `Balance` haría que `asOf` dejara de ser el instante
  que comprueban los casos del reporte, y el del reporte (UTC, día 1) borraría el corte por zona que
  prueba `Balance`.
- Descartado: dejar los dos tests separados apuntando a `TransactionReportUseCase`. El criterio de
  la tarea pide los mismos casos, no los mismos archivos, y FIN-126 a FIN-131 unieron los suyos.

`TransactionReportControllerTest` pasa de tres `@MockitoBean` a dos (`TransactionReportPort` y
`DescribeSharingPort`), con los mismos casos.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → se toma el conteo de la rama antes de empezar y se compara
  al final: el test unido suma 14, lo mismo que sus orígenes, y el total de la suite no cambia.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
- [Un error que cambie de mensaje o campo] → los tests unidos comparan los errores igual que hoy, y
  `bruno/reports/` los comprueba contra la app real.
