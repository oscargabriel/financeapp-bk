# Tasks

## 1. Esquema

- [x] 1.1 `docs/database/update/20261013_02_grupo_de_pendientes.sql` (antes del despliegue): columna
  `transactions.group_id UUID NULL`, su `COMMENT` e índice `ix_transactions_user_group`
  `(user_id, group_id) WHERE group_id IS NOT NULL`. El encabezado dice que va antes del despliegue y
  que reinicia los datos locales.
- [x] 1.2 `docs/database/update/20261013_03_grupo_de_pendientes_check.sql` (después del despliegue):
  un grupo propio para cada pendiente sin grupo y `ck_transactions_group`.
- [x] 1.3 `schema.sql` con la columna, el índice y el `CHECK`; `test-data.sql`: `\set` fijos para dos
  grupos de `pendientes@` (`pendiente_traslado` y `pendiente_gasto` en uno, `pendiente_rechazo` en
  otro). Revisar si `demo-data.sql` crea pendientes y darles grupo.
- [x] 1.4 Aplicar los dos updates en local, cargar `cargar-datos-local.sql` y correr la comparación
  de esquemas de `modelo-datos.md`. `modelo-datos.md`, *Movimientos pendientes*: el grupo, el `CHECK`
  y que el estado no depende del origen.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 `bruno/assistant/`: los requests que leen `transaction` pasan a `group` (`gasto-pendiente`,
  `transferencia`, los de aclaración con `group` en `null`); `aprobar-gasto.yml` toma el id de
  `group.transactions[0]`. `gasto-pendiente` comprueba que `group` trae solo `id` y `transactions`.
- [x] 2.2 `bruno/assistant/`, el flujo del grupo con el stub (una llamada por línea):
  - `dos-gastos.yml`: dos `crear_movimiento` de 20000 y 15000 sobre la misma cuenta; 200, dos
    `PENDING` en orden; guarda `grupoAsistente`;
  - `pendientes-con-grupo.yml`: los dos con ese `groupId`; `saldos-sin-confirmar.yml`;
  - `confirmar-grupo.yml`: 200, dos `CONFIRMED`; `saldos-tras-confirmar.yml`: baja 35000;
  - `confirmar-de-nuevo.yml` y `descartar-confirmado.yml`: 409 `INVALID_STATE` en `status`;
  - `dos-gastos-para-descartar.yml`, `descartar-grupo.yml` (204), `pendientes-tras-descartar.yml`,
    `descartar-de-nuevo.yml` (404 `NOT_FOUND` en `groupId`);
  - `grupo-parcial.yml`: dos gastos, aprobar uno a uno el primero, confirmar el grupo → 200 con solo
    el segundo;
  - `grupo-sin-saldo.yml`: dos gastos que juntos superan el saldo de una cuenta `CASH`; confirmar →
    409 `INSUFFICIENT_FUNDS` en `[1].amount`, los dos siguen pendientes; luego descartar;
  - `grupo-categoria-borrada.yml`: categoría nueva, gasto con ella, borrarla; aprobar uno a uno → 409
    `INVALID_STATE` en `categoryId`; confirmar el grupo → 409 en `[0].categoryId`; descartar;
  - `segundo-sin-resolver.yml`: dos llamadas, la segunda con una cuenta inexistente →
    `NEEDS_CLARIFICATION`, `message` con «Movimiento 2», pendientes sin cambios;
  - `crear-y-consultar.yml`: `crear_movimiento` y `consultar_saldo` → `CREATE_TRANSACTION`,
    `balance` `null`; descartarlo;
  - `grupo-ajeno.yml`: confirmar y descartar el grupo sembrado de `pendientes@` → 404 en `groupId`;
    `grupo-inexistente.yml` (404) y `grupo-mal-formado.yml` (400 `VALIDATION_ERROR` en `groupId`);
  - `confirmar-sin-credenciales.yml`, `confirmar-con-basic.yml`, `descartar-sin-credenciales.yml`,
    `descartar-con-basic.yml`: 401 `UNAUTHENTICATED` en `authorization`.
- [x] 2.3 `bruno/pending/listar-pendientes.yml`: cada pendiente trae `groupId`; la transferencia y el
  gasto comparten el del `\set`. `bruno/transactions/`: `lote-de-uno.yml` con `"groupId": null`;
  `multimoneda-gasto-convertido.yml` con `groupId` no nulo y `"origin": "WEB"`; un lote con dos
  convertidos y uno sin convertir (`multimoneda-lote-agrupado.yml`): mismo grupo los dos, `null` el
  tercero.
- [x] 2.4 Verificar que fallan por la razón correcta con `verificar-bruno.ps1 -RecargarDatos` sobre la
  app de `dev` y la base con el update 1.1: 404 de ruta inexistente en los grupos, `group` ausente en
  el asistente, `groupId` ausente en las respuestas.

## 3. Core: estado inicial y grupo en el alta

Skills: `java-architect`.

- [x] 3.1 `Transaction` gana `groupId`; actualizar cada `new Transaction(` del código y de los tests.
  `TransactionR2dbcAdapter` lo inserta y lo lee; el `UPDATE` del PATCH no lo toca. Verificación:
  compila y la suite sigue en verde.
- [x] 3.2 `CreateTransactionsPort.create` recibe el `TransactionStatus` inicial;
  `TransactionOrigin.estadoInicial()` se borra; `TransactionController` pide `CONFIRMED`.
  `CreateTransactionsUseCaseTest`: con `PENDING` todos entran pendientes; con `CONFIRMED` solo los
  convertidos; los pendientes de un lote comparten un `groupId` y un lote sin pendientes no tiene.
- [x] 3.3 Ids crecientes en el orden del lote. `CreateTransactionsUseCaseTest`: con el reloj fijo,
  los ids del lote devuelto están ordenados.
- [x] 3.4 `TransactionResponse` con `groupId`. `TransactionControllerTest`: el alta, el PATCH, la
  aprobación y la lista lo devuelven; un `groupId` en el cuerpo del alta se ignora.

## 4. Core: validación de aprobación compartida

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 Extraer la validación de `ApprovePendingTransactionUseCase` a una clase del paquete
  `usecase` (cuentas desactivadas, categoría viva que aplica al tipo, saldo), con el prefijo del
  campo como parámetro. Su test cubre los tres errores, que `INVALID_STATE` sale sin los de saldo, y
  el prefijo `[i].`.
- [x] 4.2 `ApprovePendingTransactionUseCaseTest`: categoría borrada → 409 `INVALID_STATE` en
  `categoryId` sin confirmar; los casos actuales siguen pasando.

## 5. Core: confirmar y descartar un grupo

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `TransactionRepositoryPort`: `findByGroup(groupId, userId)` (por `id`),
  `confirmAll(ids, userId)` (transaccional, `true` solo si actualizó todas) y
  `deletePendingIn(ids, userId)` (filas borradas). Implementación en `TransactionR2dbcAdapter`.
- [x] 5.2 `ApprovePendingGroupPort` y su caso de uso. Test: sin filas → 404 `NOT_FOUND` en `groupId`;
  sin pendientes → 409 `INVALID_STATE` en `status`; confirma solo los pendientes, en orden; un error
  de validación no escribe; `confirmAll` en `false` → 404.
- [x] 5.3 `RejectPendingGroupPort` y su caso de uso. Test: los mismos 404 y 409; borra solo los
  pendientes; cero filas borradas → 404.
- [x] 5.4 Rutas en `TransactionController`, con `groupId` parseado a mano (400 en `groupId`).
  `TransactionControllerTest`: 200 con la lista, 204, 400. `TransactionsIT`: 401 sin credencial y con
  Basic en las dos rutas.

## 6. Asistente: varios movimientos en un grupo

Skills: `java-architect`.

- [x] 6.1 `FuncionesDelAsistente` decide sobre la lista de llamadas: todas las `crear_movimiento` en
  orden, o la primera. `GeminiAssistantAdapterTest`: dos `functionCall` de crear, crear junto a
  consultar, dos consultas. `StubAssistantAdapterTest`: una llamada por línea.
- [x] 6.2 `instrucciones.txt`, regla 2: una llamada a `crear_movimiento` por movimiento y una sola
  para consultar. `GeminiAssistantAdapterTest` comprueba el texto enviado.
- [x] 6.3 `PendingMovementPort.register` recibe la lista y devuelve el grupo;
  `PendienteDelAsistenteAdapter` pide `PENDING`. `PendienteDelAsistenteAdapterTest`.
- [x] 6.4 `AssistantDecision.CrearMovimientos`, `AssistantReply` con el grupo y `AssistantUseCase`:
  resuelve todos antes de registrar, «Movimiento N:» con más de uno, `message` que pide confirmar o
  descartar. `AssistantUseCaseTest`: dos movimientos, el segundo sin resolver, uno solo sin prefijo.
- [x] 6.5 `AssistantMessageResponse`: `group` en lugar de `transaction`. `AssistantControllerTest`.

## 7. Documentación

- [x] 7.1 `contrato-api.md`: las dos rutas en la tabla y en *Movimientos pendientes* con sus errores;
  `groupId` en la respuesta del movimiento y en *Origen* (el estado ya no depende de él); la respuesta
  del asistente con `group` y varios movimientos; la tabla de `INVALID_STATE` con `categoryId`; sacar
  «Aprobar o rechazar pendientes en lote» de *Lo que el API todavía no tiene*.
- [x] 7.2 Replicar en `bruno-personal/` los requests nuevos y cambiados de las rutas de grupo y del
  asistente (ruta, cuerpo, autenticación), sin ejecutarlos.
- [x] 7.3 `docs/despliegue.md`: el orden de los dos updates respecto al despliegue.

## 8. Verificación

- [x] 8.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura.
- [x] 8.2 `verificar-bruno.ps1 -RecargarDatos` en verde (incluye `bru run . -r --env local` y
  `check-saldos.sql`), con los conteos.
