# Design

## Context

Ver proposal.md, *Why*. `AffiliationController` tiene ocho endpoints sobre cinco ports: cuatro de
ellos (`accept`, `reject`, `cancel`, `unlink`) llaman al mismo `ChangeAffiliationPort.change` con la
`AffiliationAction` que corresponde a la ruta. `AffiliationAction` es un enum de dominio que dice el
estado de origen, el de destino y quién puede aplicarla; tiene su propio `AffiliationActionTest`.

Dos de los use cases usan los mismos nombres para errores distintos:

| Use case | Fábrica | Error |
|---|---|---|
| `ChangeAffiliationUseCase` | `noEncontrada()` | 404, «La solicitud de afiliacion no existe» |
| `ListSharesWithAffiliateUseCase` | `noEncontrada()` | 404, «La afiliacion no existe» |
| `ChangeAffiliationUseCase` | `estadoInvalido(action)` | 409, un mensaje por acción |
| `ListSharesWithAffiliateUseCase` | `estadoInvalido()` | 409, «Solo hay partes con una afiliacion aceptada o desvinculada» |

Ninguno de los cinco tests de use case corre con `@MockitoSettings(strictness = LENIENT)`.

## Goals / Non-Goals

**Goals:**

- Aplicar el patrón de FIN-126 en `AffiliationController`.

**Non-Goals:**

- Cambiar comportamiento, mensajes o el orden de las validaciones de cualquier operación.
- Tocar `AffiliationAction` o los ports de salida.

## Decisions

### Una operación por endpoint, también para las cuatro acciones

`AffiliationPort` tiene `accept`, `reject`, `cancel` y `unlink`, cada una con `(UUID userId, UUID
affiliationId)` y `Mono<AffiliationView>`. En `AffiliationUseCase` las cuatro llaman a un método
privado `cambiar(userId, affiliationId, action)` con la lógica de hoy de `ChangeAffiliationUseCase`,
y el javadoc de `change` pasa a ese método. El controller deja de conocer `AffiliationAction`.

Es lo que pide la regla de `AGENTS.md` (una operación por endpoint) y el criterio de FIN-130, y el
port dice qué se puede hacer con una afiliación sin leer el enum. El controller conserva sus
respuestas: `accept` mapea la vista a `AffiliationResponse` y las otras tres responden 204 sin
cuerpo.

- Descartado: una sola operación `change(userId, affiliationId, action)` para las cuatro rutas. Es
  el cambio más corto y deja intactos los tests que pasan la acción, pero el port tendría cinco
  operaciones para ocho endpoints, y saber qué hace cada ruta pediría leer el controller.

### Nombres de las operaciones

Cada operación toma el nombre del método del controller que la llama, lo que además resuelve el
choque de los dos `list(UUID userId)`:

| Port de origen | Operación nueva |
|---|---|
| `ListAffiliationsPort.list` | `AffiliationPort.list` |
| `ListAffiliateBalancesPort.list` | `AffiliationPort.balances` |
| `ListSharesWithAffiliatePort.list` | `AffiliationPort.shares` |
| `SendAffiliationRequestPort.send` | `AffiliationPort.send` |
| `ChangeAffiliationPort.change` con `ACCEPT`, `REJECT`, `CANCEL`, `UNLINK` | `AffiliationPort.accept`, `reject`, `cancel`, `unlink` |

### Los errores con el mismo nombre se renombran

Con los cinco use cases en una clase, las fábricas de la tabla de *Context* chocan. Pasan a
métodos `private static` con el mismo mensaje, código y campo, y nombres que dicen cuál es cuál:
`solicitudNoEncontrada()` y `accionInvalida(action)` (las acciones), `afiliacionNoEncontrada()` y
`sinPartes()` (el detalle). `duplicada()` y `otraPersona(...)` no chocan y conservan el nombre, igual
que la constante `CON_PARTES`.

### Tests: un `@Nested` por operación de origen

`AffiliationUseCaseTest` une los cinco tests con `@Nested` `Enviar`, `Listar`, `CambiarEstado`,
`Saldos` y `Partes`. Los mocks (`AffiliationRepositoryPort`, `UserRepositoryPort`,
`AffiliateBalanceQueryPort`), el `Clock` y la construcción del use case van en la clase externa, como
`AccountUseCaseTest`. Los casos de `CambiarEstado` llaman a la operación de la acción que prueban:
`accept` donde hoy pasan `ACCEPT`, `unlink` y `cancel` igual. Todos los orígenes son estrictos, así
que la clase unida también.

En `AffiliationControllerTest`, el `@ParameterizedTest` de `reject`, `cancel` y `unlink` pasa de
`@CsvSource` con la acción a `@MethodSource` con la ruta, el estado de destino y la operación del port
como función, para seguir comprobando que cada ruta llama a la suya. El caso del id que no es UUID
verifica `verifyNoInteractions` sobre el port.

## Risks / Trade-offs

- [Un caso perdido al mover los tests] → se toma el conteo de la rama antes de empezar y se compara
  al final: el test unido suma lo mismo que sus orígenes, y el total no cambia.
- [Una ruta conectada a la acción equivocada] → la cubren el `@ParameterizedTest` del controller,
  que verifica qué operación se llamó, y `bruno/affiliations/`, que comprueba el estado resultante
  contra la base.
- [Bajar la cobertura de rama] → el código es el mismo y los tests también; `gradlew build` exige los
  umbrales.
