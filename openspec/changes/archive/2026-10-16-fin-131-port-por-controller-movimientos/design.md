# Design

## Context

Ver proposal.md, *Why*. `TransactionController` tiene nueve endpoints sobre diez ports: uno por
endpoint más `DescribeSharingPort`, que su `responder(...)` usa para agregar lo compartido a cada
respuesta. Dos de esos ports tienen otro consumidor:

| Port | Otro consumidor |
|---|---|
| `CreateTransactionsPort` | `PendienteDelAsistenteAdapter` (`puente/`), que registra los movimientos del asistente con origen y estado propios |
| `DescribeSharingPort` | `TransactionReportController`, que migra FIN-132 |

`CreateTransactionsUseCase` tiene lógica propia (`TransactionBatchValidator`, la conversión de
moneda, las partes de un gasto compartido) y usa `ResolveExchangeRatePort`, igual que
`ApprovePendingTransactionUseCase`.

Los otros ocho use cases comparten tres auxiliares de paquete: `PendienteDelUsuario` (aprobar,
rechazar, actualizar y borrar), `GrupoDelUsuario` (confirmar y descartar un grupo) y
`AprobacionDePendientes` (aprobar uno y aprobar un grupo). `noEncontrado()` es de paquete en
`UpdateTransactionUseCase` porque lo usan `DeleteTransactionUseCase` y `PendienteDelUsuario`. Ninguno
de los tres auxiliares lo usa nadie fuera de estos use cases; solo `AprobacionDePendientes` tiene
test propio.

Dos de los ocho tests de origen (`UpdateTransactionUseCaseTest` y `DeleteTransactionUseCaseTest`)
corren con `@MockitoSettings(strictness = LENIENT)`. En los dos, el mock llamado `cuentas` es un
`AccountQueryPort`; en `ApprovePendingTransactionUseCaseTest` y `ApprovePendingGroupUseCaseTest`,
`cuentas` es un `AccountRepositoryPort` y el `AccountQueryPort` se llama `cuentasDelUsuario`.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 en `TransactionController`.
- Resolver los dos ports compartidos según la regla de `AGENTS.md`.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.
- Tocar `TransactionReportController` (FIN-132) o los ports de salida.

## Decisions

### `CreateTransactionsPort` sigue aparte y `TransactionUseCase` lo consume

`TransactionPort.create` tiene la firma de hoy (`userId`, origen, estado y lote) y
`TransactionUseCase.create` delega en `CreateTransactionsPort` sin agregar nada. El controller sigue
pasando `WEB` y `CONFIRMED`, y `PendienteDelAsistenteAdapter` sigue con `CreateTransactionsPort`. Es
lo que dice `AGENTS.md` del use case con lógica propia que usan otros adapters: el use case del
controller lo consume en vez de absorberlo.

- Descartado: absorber el alta en `TransactionUseCase` y que `PendienteDelAsistenteAdapter` dependa
  de `TransactionPort`. El puente tendría acceso a borrar, aprobar y rechazar para usar solo
  `create`, el mismo argumento que FIN-127 dio para `CatalogUseCase`.
- Descartado: que el controller dependa directo de `CreateTransactionsPort`. `TransactionPort`
  quedaría sin la operación de un endpoint, y la regla pide una por endpoint.
- Descartado: que `create` fije `WEB` y `CONFIRMED` y reciba solo el usuario y el lote. Mueve una
  decisión del adapter web al use case sin que lo pida nada, y el refactor no cambia firmas.

### `DescribeSharingPort` sigue aparte y el controller lo consume

`TransactionController` depende de `TransactionPort` y `DescribeSharingPort`. Lo compartido se arma
en el `responder(...)` del controller, que hoy también usa `TransactionReportController`, y la regla
deja aparte el port que usan otros adapters. FIN-132 parte de aquí.

- Descartado: que las operaciones de `TransactionPort` devuelvan el movimiento con lo compartido.
  Cambia los retornos del dominio, que el refactor no toca, y `TransactionReportController` seguiría
  necesitando el port.
- Descartado: una operación `describe` en `TransactionPort`. No es un endpoint, y
  `TransactionReportController` tendría que depender del port de otro controller.

### Los auxiliares de un solo dueño pasan a métodos privados

Con los ocho use cases en una clase, `PendienteDelUsuario` y `GrupoDelUsuario` ya no comparten nada
con nadie: pasan a métodos privados de `TransactionUseCase` y sus archivos se borran, como
`PlanDeCompra` en FIN-128. `noEncontrado()` pasa a privado. Los nombres que chocarían se renombran
sin cambiar mensaje, código ni campo:

| Origen | En `TransactionUseCase` |
|---|---|
| `PendienteDelUsuario.buscar` | `pendienteDelUsuario` |
| `PendienteDelUsuario.exigirEscritura`, `noCompartido` | igual |
| `PendienteDelUsuario.yaConfirmado` | `movimientoYaConfirmado` |
| `GrupoDelUsuario.pendientes` | `pendientesDelGrupo` |
| `GrupoDelUsuario.noEncontrado`, `yaConfirmado` | `grupoNoEncontrado`, `grupoYaConfirmado` |
| `RejectPendingBatchUseCase.errores`, `cambiado` | `erroresPorIndice`, `loteCambiado` |
| `ApprovePendingTransactionUseCase.rechazarSiHay` (400) | igual: el de `AprobacionDePendientes` (409) es de otra clase |

`ParcheDeMovimiento` sigue como clase privada anidada, como hoy en `UpdateTransactionUseCase`.

`AprobacionDePendientes` sigue en su archivo: guarda estado (cuentas, categorías, instante) y tiene su
propio test, que se perdería al volverla privada.

- Descartado: dejar `PendienteDelUsuario` y `GrupoDelUsuario` como archivos aparte para achicar
  `TransactionUseCase`. Conservan clases que no comparten nada, lo contrario de lo que busca el
  epic.

### Dependencias de `TransactionUseCase`

La unión de las de los use cases que reemplaza, con los nombres de `ApprovePendingTransactionUseCase`
para que ningún nombre se repita con otro tipo: `repositorio` (`TransactionRepositoryPort`), `cuentas`
(`AccountRepositoryPort`), `categorias` (`CategoryRepositoryPort`), `cuentasDelUsuario`
(`AccountQueryPort`), `categoriasDelUsuario` (`CategoryQueryPort`), `monedas`, `tasas`, `alta`
(`CreateTransactionsPort`) y `clock`. Actualizar y borrar, que hoy llaman `cuentas` y `categorias` a
los `*QueryPort`, pasan a usar `cuentasDelUsuario` y `categoriasDelUsuario`.

### Tests: un `@Nested` por operación y la estrictez de cada origen

`TransactionUseCaseTest` une los ocho tests con `@Nested` `Actualizar`, `Borrar`, `Pendientes`,
`Aprobar`, `Rechazar`, `RechazarLote`, `AprobarGrupo` y `RechazarGrupo`, más `Crear` con un caso
nuevo: `create` llama a `CreateTransactionsPort` con los mismos argumentos y devuelve lo que este
devuelve. Los mocks, con los nombres de la sección anterior, y `useCase()` van en la clase externa,
como `AccountUseCaseTest`.

La clase externa queda estricta, y los stubs del `@BeforeEach` de `Actualizar` y `Borrar` llevan
`lenient()`, el criterio de FIN-126 y FIN-128. Si un caso de esos dos stubea dentro del método algo
que no consume, ese stub también lleva `lenient()`, y se anota en `tasks.md`.

- Descartado: `LENIENT` en la clase externa. Relaja los seis tests que hoy son estrictos.

`TransactionControllerTest` pasa de diez `@MockitoBean` a dos (`TransactionPort` y
`DescribeSharingPort`), con los mismos casos.

## Risks / Trade-offs

- [`TransactionUseCase` queda grande, unas 650 líneas] → es la consecuencia de la regla en el
  controller con más endpoints; cada operación pública conserva su javadoc y sus auxiliares quedan
  junto a ella.
- [Un caso perdido al mover los tests] → se toma el conteo de la rama antes de empezar y se compara
  al final: el test unido suma lo mismo que sus orígenes más el caso de `Crear`, y el total sube en
  uno.
- [Un error renombrado que cambie de mensaje o campo] → los tests unidos comparan los errores igual
  que hoy, y `bruno/transactions/`, `bruno/pending/` y `bruno/pending-batch/` los comprueban contra la app real.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
