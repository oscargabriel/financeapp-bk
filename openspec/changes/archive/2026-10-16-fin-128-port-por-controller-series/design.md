# Design

## Context

Ver proposal.md, *Why*. Los use cases de series se llaman entre sí: `CancelRecurrenceUseCase` toma
`noEncontrada()` de `UpdateRecurrenceUseCase`, y este toma `proxima()` e `invalido()` de
`CreateRecurrenceUseCase`. En cuotas, `CancelInstallmentPurchaseUseCase` toma `noEncontrada()` de
`UpdateInstallmentPurchaseUseCase`, y `CreateInstallmentPurchaseUseCase` y
`PreviewInstallmentPurchaseUseCase` comparten `PlanDeCompra`, un record de paquete sin test propio
que valida la compra contra el usuario y calcula el plan (FA-108). Ningún otro consumidor usa estos
use cases ni `PlanDeCompra`.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 tal cual en los dos controllers.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.
- Tocar `CatchUpSeriesUseCase`, `SeriesAlDia`, `ReferenciasDelUsuario` o `ZonaDelUsuario`.

## Decisions

### Un use case por controller, como `AccountUseCase`

`RecurrenceUseCase` e `InstallmentPurchaseUseCase` reúnen las operaciones públicas tal como están, y
sus auxiliares pasan a privados. `noEncontrada()`, `proxima()` e `invalido()` dejan de ser de
paquete: sus únicos consumidores externos quedan dentro de la misma clase. Los dos use cases reciben
la unión de las dependencias de los que reemplazan; ninguna se repite con otro tipo.

### `PlanDeCompra` pasa a un record privado del use case

Su razón de existir era compartir validación y plan entre dos use cases, que ahora son uno. Pasa a
`InstallmentPurchaseUseCase` como record privado anidado, con el mismo `de(...)`, `tasa(...)` y
`cuotas(...)`, y su javadoc conserva el porqué de FA-108.

- Descartado: dejarlo como archivo aparte. Conserva una clase que ya no comparte nada con nadie,
  lo contrario de lo que busca el epic.
- Descartado: deshacer el record en métodos privados sueltos. `cuotas(...)` depende de la tarjeta,
  el plan y la zona juntos; el record los mantiene juntos sin pasar cinco argumentos por método.

### Tests: un `@Nested` por operación y la estrictez de cada origen

`RecurrenceUseCaseTest` une los cuatro tests de series con `@Nested` `Crear`, `Listar`,
`Actualizar` y `Cancelar`; `InstallmentPurchaseUseCaseTest` une los cinco de cuotas con
`Simular`, `Crear`, `Listar`, `Actualizar` y `Cancelar`. Los mocks y `useCase()` van en la clase
externa, como `AccountUseCaseTest`.

Cinco de los nueve tests de origen corren con `@MockitoSettings(strictness = LENIENT)` porque su
`@BeforeEach` stubea un escenario completo que no todos los casos consumen. En el test unido, la
clase externa queda estricta, y los stubs del `@BeforeEach` de esos `@Nested` llevan `lenient()`:
los casos de `Cancelar` y del listado de compras, hoy estrictos, no pierden el aviso de stubs
sobrantes. Es el criterio de FIN-126.

- Descartado: `LENIENT` en la clase externa. Es el cambio más corto, pero relaja los tests que hoy
  son estrictos.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → se toma el conteo de `dev` antes de empezar y se compara al
  final: los tests de use case unidos suman lo mismo que sus orígenes, y el total no cambia.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
