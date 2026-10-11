# Tasks

## 1. Esquema y datos

- [x] 1.1 `docs/database/update/20261008_01_transacciones_estado_aprobacion.sql`. Contenido:
  - la columna `status` con su default y `ck_transactions_status`;
  - `ix_transactions_pending (user_id, occurred_at DESC) WHERE status = 'PENDING'`;
  - `sync_account_balances` con la condición de estado (design.md, decisión 2);
  - las dos vistas de gasto mensual con `AND t.status = 'CONFIRMED'`;
  - el comentario de la columna;
  - el bloque de reversión comentado.

  El encabezado dice que va **antes** del despliegue y que en local reinicia los datos (correr
  `cargar-datos-local.sql` después).
- [x] 1.2 Los mismos cambios en `schema.sql`. Verificar con la comparación de esquemas de
  `docs/database/modelo-datos.md`, que tiene que salir sin diferencias.
- [x] 1.3 `test-data.sql`: usuario `pendientes@financeapp.local` (id fijo, misma clave). Lleva sus
  categorías de la semilla y dos cuentas COP: «Cuenta pendientes» con 1.000.000 y «Bolsillo
  pendientes» con 0. Movimientos del mes en curso:
  - un gasto confirmado de 30000;
  - un gasto pendiente de 45000, con id fijo;
  - una transferencia pendiente de 100000 de la primera cuenta a la segunda, con id fijo;
  - un gasto pendiente de 20000 para rechazar, con id fijo.

  El borrado inicial del script también lo incluye. Verificar recargando y consultando con psql:
  los saldos tienen que ser 970000 y 0 (solo el confirmado).
- [x] 1.4 Aplicar el update a la base local y correr `cargar-datos-local.sql`. Verificar que
  `demo@` y `dev@` cargan igual que antes (7 cuentas, 107 movimientos ± los del día) y que todos
  sus movimientos quedan `CONFIRMED`.

## 2. Bruno en RED

Skills: `bruno-cli`.

- [x] 2.1 Carpeta `bruno/pending/` (`seq` 10). El usuario es `pendientes@financeapp.local`, con login
  propio y token `tokenPendientes`. El `docs` dice que la carpeta necesita `test-data.sql` recargado
  antes de cada corrida. Requests, en orden:
  - login;
  - saldos antes: `GET /accounts`, con 970000 y 0;
  - `GET /reports/balance` del mes: `period.expense` 30000;
  - `GET /reports/transactions` del mes: un solo movimiento;
  - `GET /transactions/pending`: tres, en orden por `occurredAt` descendente y todos `PENDING`;
  - `PATCH` del gasto pendiente a 50000: 200, sigue `PENDING`, y el saldo no cambia;
  - aprobar el gasto: 200 `CONFIRMED`, y la cuenta queda en 920000;
  - aprobar la transferencia: las cuentas quedan en 820000 y 100000;
  - `GET /reports/balance`: `period.expense` 80000;
  - rechazar el de 20000: 204, y la lista de pendientes queda vacía;
  - saldos después: sin cambios por el rechazo;
  - aprobar el ya aprobado: 409 `INVALID_STATE` en `status`;
  - rechazar el ya aprobado: 409;
  - rechazar dos veces: 404 en `id`;
  - aprobar y rechazar con el `accessToken` de `prueba@`: 404;
  - aprobar con un UUID inexistente: 404;
  - `abc/approve` y `abc/reject`: 400 en `id`;
  - los tres endpoints sin credencial y con Basic: 401.

  Para el caso «ajeno», los requests de ajeno van antes de aprobar: si no, darían 409 o 404 por otro
  motivo.
- [x] 2.2 En `bruno/transactions/lote-de-uno.yml`: `status` = `CONFIRMED` en la respuesta.
- [x] 2.3 Verificar que fallan hoy por la razón correcta. Con la base del grupo 1 y la app de `dev`,
  correr `bru run pending -r --env local` después de `auth/`. Las rutas nuevas dan 404 o 405, y el
  saldo de 970000 ya pasa porque lo pone el trigger. Anotar cuáles pasan desde ya.

  Hecho en el 8081 (el 8080 lo ocupaba la app del usuario). Pasan desde ya los saldos antes y tras
  corregir (los pone el trigger), los seis 401, y el código `NOT_FOUND` de los ajenos (el campo no,
  porque la ruta no existe). Fallan por la razón correcta:
  - las rutas nuevas, con 404 o 405;
  - la consulta de saldo y el reporte, que suman los pendientes;
  - `status`, que no está en la respuesta, en `Lote de uno` y `Corregir pendiente`.

## 3. Dominio

Skills: `java-architect`.

- [x] 3.1 Enum `TransactionStatus` (`PENDING`, `CONFIRMED`) y el campo `status` en `Transaction`.
  Actualizar los Object Mother y los tests que construyen `Transaction`. `CreateTransactionsUseCaseTest`
  en RED: todo lo que se crea sale `CONFIRMED`. Verificar con la suite en verde.
- [x] 3.2 `TransactionRepositoryPort` gana `findPendingByUser(userId)`, `confirm(id, userId)` y
  `deletePending(id, userId)`. Las dos últimas devuelven `Mono<Boolean>`, que es `false` si no había
  un pendiente que tocar.

## 4. Casos de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `INVALID_STATE` en `ErrorCodes`. `WebExceptionHandlerTest` no cambia: el handler no
  distingue códigos.
- [x] 4.2 `ApprovePendingTransactionUseCaseTest` en RED:
  - pendiente propio → `confirm` y devuelve el movimiento `CONFIRMED`;
  - no encontrado → 404 `NOT_FOUND` en `id`, sin llamar a `confirm`;
  - confirmado → 409 `INVALID_STATE` en `status`, sin llamar a `confirm`;
  - `confirm` devuelve `false` → 404.
- [x] 4.3 `RejectPendingTransactionUseCaseTest` en RED, con los mismos cuatro casos sobre
  `deletePending`.
- [x] 4.4 `ListPendingTransactionsUseCaseTest` en RED: delega en `findPendingByUser`.
- [x] 4.5 Crear los tres puertos de entrada y sus casos de uso. Verificar con las tres clases en verde.

## 5. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 5.1 `TransactionControllerTest` en RED:
  - `GET /transactions/pending` devuelve la lista con `status`;
  - `approve` responde 200 con el cuerpo, y `reject` 204 sin cuerpo;
  - `abc` → 400 en `id`, sin llamar al puerto;
  - el alta devuelve `status`.
- [x] 5.2 Test de integración en RED: los tres endpoints responden 401 sin credencial y con el Basic
  compartido. Va en la clase IT de transacciones que ya cubre `PATCH` y `DELETE`, o en una nueva si
  no existe.
- [x] 5.3 Los tres endpoints en `TransactionController` y `status` en `TransactionResponse`.
  Verificar con las dos clases en verde.

## 6. Persistencia

- [x] 6.1 `TransactionR2dbcAdapter`:
  - el `INSERT` y el `BUSCAR` llevan `status`;
  - `findPendingByUser` ordena por `occurred_at DESC, id DESC`;
  - `confirm` y `deletePending` repiten `status = 'PENDING'` en el `WHERE` (design.md, decisión 6).
- [x] 6.2 `TransactionReportR2dbcAdapter`: `AND t.status = 'CONFIRMED'` en el `WHERE`.
  `BalanceR2dbcAdapter`: el mismo filtro en el `ON` del `LEFT JOIN`.

  Todo el grupo queda fuera de la cobertura por el patrón `*R2dbcAdapter`. Se verifica con los
  requests del grupo 2 en verde.

## 7. Documentación y colección personal

Skills: `bruno-cli`.

- [x] 7.1 `docs/api/contrato-api.md`: las tres rutas en el índice, su sección (respuesta, errores,
  efecto en saldos), el campo `status` en la respuesta de movimiento y la nota en los reportes de
  que los pendientes no cuentan. Verificar leyendo las secciones. También `INVALID_STATE` en la
  tabla de errores, y dos líneas en «Lo que el API todavía no tiene».
- [x] 7.2 `AGENTS.md`: `pendientes@financeapp.local` en la tabla de usuarios del back, y la
  condición de recarga de `bruno/pending/`. `docs/database/modelo-datos.md`: la columna `status` y
  el trigger. Verificar leyendo las dos secciones.
- [x] 7.3 `bruno-personal/movimientos/`: requests de listar pendientes, aprobar y rechazar. El id va
  en la URL como ejemplo a reemplazar, igual que `eliminar.yml`, en vez de como variable. Ruta,
  método y autenticación (`inherit`, Bearer de la raíz) iguales a `bruno/pending/`. No se ejecutan.

## 8. Verificación

- [x] 8.1 `.\gradlew.bat build` en verde: 661 tests, 0 fallos, cobertura de línea 98,28 %
  (1146/1166). Corrió con el `application.yaml` versionado. El del working tree tenía un cambio del
  usuario (`${SPRING_PROFILES_ACTIVE:local}`) que hace fallar `CloudRunConfigTest`; se apartó
  durante el build y se repuso después.
- [x] 8.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde:
  286/286 requests, 238/238 tests y 652/652 aserciones.
- [x] 8.3 `cargar-datos-local.sql` al final: `demo@` y `dev@` con 107 movimientos confirmados,
  `prueba@` con 25, y `pendientes@` con 1 confirmado y 3 pendientes.
