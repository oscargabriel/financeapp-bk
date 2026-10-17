# Tasks

## 1. Línea base

- [x] 1.1 En la rama, antes del primer cambio de código, correr `.\gradlew.bat test` y anotar aquí el
  total de tests y, según `build/test-results/test/*.xml`, los de `UpdateTransactionUseCaseTest`,
  `DeleteTransactionUseCaseTest`, `ListPendingTransactionsUseCaseTest`,
  `ApprovePendingTransactionUseCaseTest`, `RejectPendingTransactionUseCaseTest`,
  `RejectPendingBatchUseCaseTest`, `ApprovePendingGroupUseCaseTest`,
  `RejectPendingGroupUseCaseTest`, `TransactionControllerTest`, `TransactionsIT`,
  `CreateTransactionsUseCaseTest`, `DescribeSharingUseCaseTest`, `AprobacionDePendientesTest` y
  `PendienteDelAsistenteAdapterTest`; verificación: los números quedan escritos en esta casilla
  - Total 1738, sin fallos. Update 41, Delete 7, ListPending 2, ApprovePending 22, RejectPending 4,
    RejectPendingBatch 6, ApprovePendingGroup 7, RejectPendingGroup 4 (los ocho suman 93: el unido
    tiene que dar 94). TransactionControllerTest 64, TransactionsIT 21, CreateTransactionsUseCaseTest
    58, DescribeSharingUseCaseTest 2, AprobacionDePendientesTest 11, PendienteDelAsistenteAdapterTest 4

## 2. Port y use case

Skills: `java-architect`, `java-exceptions`, `java-testing`.

- [x] 2.1 Unir los ocho tests de use case en `TransactionUseCaseTest` con `@Nested` `Actualizar`,
  `Borrar`, `Pendientes`, `Aprobar`, `Rechazar`, `RechazarLote`, `AprobarGrupo` y `RechazarGrupo`;
  mocks (con los nombres de `design.md`), `Clock` y `useCase()` en la clase externa, estricta, con
  `lenient()` en los stubs del `@BeforeEach` de `Actualizar` y `Borrar`; cada caso llama a la
  operación nueva (`pending`, `rejectBatch`, `approveGroup`, `rejectGroup`…); agregar `Crear` con el
  caso de la delegación en `CreateTransactionsPort`. Apuntar a `TransactionUseCase` y verlo fallar por
  no compilar
  - Un solo stub dentro de un método lleva `lenient()`: el de `hasPendingShares` en
    `Actualizar.laDescripcionDeUnGastoConPartesPendientesSiCambia`, porque el parche no cambia el monto
    y el use case no lo consulta. Los ocho usan el reloj de `Aprobar` (`America/Bogota`); los que
    venían en UTC no dependen de la zona y siguen en verde.
- [x] 2.2 Crear `TransactionPort` con `create`, `update`, `delete`, `pending`, `approve`, `reject`,
  `rejectBatch`, `approveGroup` y `rejectGroup` (mismos parámetros y retornos que hoy, javadoc de cada
  origen) y `TransactionUseCase` con la lógica de los ocho use cases y `create` delegando en
  `CreateTransactionsPort`; `PendienteDelUsuario`, `GrupoDelUsuario` y `noEncontrado()` pasan a
  métodos privados con los nombres de la tabla de `design.md` y los mismos mensajes, códigos y
  campos; `ParcheDeMovimiento` sigue anidada; verificación: `TransactionUseCaseTest` en verde con los
  casos de la línea base más el de `Crear`

## 3. Controller

Skills: `java-architect`, `java-testing`.

- [x] 3.1 `TransactionControllerTest` con `@MockitoBean` de `TransactionPort` y `DescribeSharingPort`
  en lugar de los diez, mismos casos; verlo fallar por no compilar
  - Compiló, porque el test no nombra los campos del controller: falló al levantar el contexto con
    `NoSuchBeanDefinitionException`. `rechazaElLoteYRespondeSinContenido` comprobaba que no se tocara
    el rechazo individual con `verifyNoInteractions(rejectPending)`; con un solo mock pasa a
    `verify(movimientos, never()).reject(any(), any())`
- [x] 3.2 `TransactionController` depende solo de `TransactionPort`, `DescribeSharingPort` y el
  `Clock`; verificación: `TransactionControllerTest` y `TransactionsIT` en verde con los mismos casos

## 4. Limpieza

- [x] 4.1 Borrar los ocho ports por operación, los ocho use cases, `PendienteDelUsuario`,
  `GrupoDelUsuario` y los ocho tests de origen; verificación:
  `grep -rnE "(UpdateTransaction|DeleteTransaction|ListPendingTransactions|ApprovePendingTransaction|RejectPendingTransaction|RejectPendingBatch|ApprovePendingGroup|RejectPendingGroup)(Port|UseCase)\b|PendienteDelUsuario|GrupoDelUsuario" src docs`
  sin resultados, y `CreateTransactionsUseCaseTest`, `DescribeSharingUseCaseTest`,
  `AprobacionDePendientesTest` y `PendienteDelAsistenteAdapterTest` en verde con los mismos casos
- [x] 4.2 En `docs/database/demo-data.sql`, el comentario de *Lo compartido (FIN-30)* nombra
  `TransactionUseCase` en lugar de `ApprovePendingTransactionUseCase`; verificación: el grep de 4.1
  sin resultados en `docs/`

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el total de tests igual al de la línea base más uno y
  los umbrales de JaCoCo sin tocar
  - 1739 tests, sin fallos ni omitidos. `TransactionUseCaseTest` 94, `TransactionControllerTest` 64,
    `TransactionsIT` 21, `CreateTransactionsUseCaseTest` 58, `DescribeSharingUseCaseTest` 2,
    `AprobacionDePendientesTest` 11, `PendienteDelAsistenteAdapterTest` 4. Cobertura: línea 99,02 %,
    rama 93,63 %
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde (incluye
  `bru run . -r --env local` y `check-saldos.sql`), con los conteos reales; `git diff --stat dev`
  sin archivos de `docs/api/` ni de `bruno/`
  - 1043 requests pasados, 695/695 tests, 2078/2078 aserciones; las 83 cuentas cuadran. El diff contra
    `dev` no tiene archivos de `docs/api/` ni de `bruno/`
