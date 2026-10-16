# Design

## Context

Ver proposal.md, *Why*. Hoy `AccountController` inyecta cuatro ports; los cuatro use cases son
`@Service` con `@AllArgsConstructor`. Entre todos usan `AccountQueryPort`, `SeriesAlDia`,
`AccountRepositoryPort`, `CurrencyQueryPort`, `RecurrenceRepositoryPort` y `Clock`.
`DeleteAccountUseCase` toma `noEncontrada()` y `seriesActivas()` de `UpdateAccountUseCase`, y
`DeleteCategoryUseCase` toma `seriesActivas()`.

## Goals / Non-Goals

**Goals:**

- Un patrón que las tareas siguientes de la migración puedan copiar tal cual: nombres, ubicación de
  la lógica auxiliar y forma de los tests.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.

## Decisions

### `AccountUseCase` es una sola clase con métodos privados por operación

Las cuatro operaciones públicas quedan como están; sus auxiliares (`nuevaCuenta`, `monedaActiva`,
`aplicar`, `saldoSuficiente`, `camposDeCreditoSegunElTipo`, `monedaCambiable`,
`sinSeriesSiSeDesactiva`, `impedimentos`, `enUsoPor`, `enUso`, `invalido`) pasan a privados.
`monedaActiva` del alta y la primera mitad de `monedaCambiable` del parche lanzan el mismo error:
se reutiliza `monedaActiva` dentro de `monedaCambiable`, sin cambiar el mensaje ni el campo.

- Descartado: dejar clases auxiliares por operación detrás de `AccountUseCase` (un facade). Sería
  el mismo número de clases que hoy y la navegación no mejora, que es lo que pide el epic.

### `seriesActivas` sigue estático de paquete, ahora en `AccountUseCase`

`DeleteCategoryUseCase` lo necesita para el mismo mensaje. `noEncontrada()` pasa a privado: ya nadie
fuera de cuentas lo usa.

- Descartado: moverlo a una clase propia (`MensajesDeSeries`). Es un formateo de una línea con un
  solo consumidor externo; FIN-127 migra categorías y puede decidir ahí si conviene.

### `AccountUseCaseTest` con un `@Nested` por operación

Los 50 tests de los cuatro archivos se mueven sin reescribir su cuerpo a `Listar`, `Crear`,
`Actualizar` y `Borrar`, con los `@Mock` y el `useCase()` en la clase externa. El proyecto ya usa
`@Nested` (`WebExceptionHandlerTest`, `SaldoNoNegativoTest`). `CreateAccountUseCaseTest` corre con
`Strictness.LENIENT` porque su `altaPosible()` stubea moneda y alta aunque el caso falle antes de
crear. En el test unido esos dos stubs llevan `lenient()` y la clase queda estricta: los otros tres
grupos no pierden el aviso de stubs sobrantes.

- Descartado: `@MockitoSettings(strictness = LENIENT)` en la clase externa. Es el cambio más corto,
  pero relaja también los tests de listar, actualizar y borrar, que hoy son estrictos.
- Descartado: un archivo de test por operación apuntando a `AccountUseCase`. Mantiene el ruido de
  navegación en los tests y deja cuatro copias de los mocks y del constructor.

### La regla en `AGENTS.md`

Reemplaza el párrafo *El cableado que importa* de *Capas dentro de cada módulo*, con
`AccountController` → `AccountPort` → `AccountUseCase` como ejemplo en lugar de `MonthlySpending`. La
excepción del enum (`GET /api/catalogs/account-types`) se conserva. Para un endpoint nuevo en un
controller que todavía no se migró, se sigue el patrón que ese controller ya tiene: migrarlo es su
propia tarea, y mezclar los dos en un controller lo dejaría a medias. El texto no lleva claves de
tablero.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → la suite de la rama tiene que correr exactamente los mismos
  tests que la de `dev` (los parametrizados cuentan por caso): se toma el conteo de `dev` antes de
  empezar y se compara al final.
- [`AccountUseCase` con seis dependencias] → aceptado: es la suma de las de hoy y el epic prioriza
  menos clases. Si una operación crece con lógica propia que otros usan, sale aparte según la misma
  regla.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige
  los umbrales.
