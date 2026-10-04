# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 En `bruno/transactions/lote-de-varios.yml`, guardar con `bru.setVar` los ids de "Fruta"
  (`idFruta`) y de la transferencia "Ahorro del mes" (`idTransferencia`). Verificar que el request
  sigue en verde.
- [x] 1.2 Escribir los requests de modificación, a partir de seq 18, todos con
  `token: {{tokenTransacciones}}` salvo donde se indica:
  - `saldos-antes-de-modificar` (GET `/accounts`): guarda los saldos de Efectivo y Ahorros, porque
    `lote-sin-fecha` ya los movió después de `saldos-tras-el-alta`.
  - `modificar-monto-y-descripcion`: `idFruta` con `{"amount": 45000, "description": "Fruta y verdura"}`
    → 200, monto y descripción nuevos, mismo `type`, `accountId`, `categoryId` y `occurredAt`.
  - `saldos-tras-modificar`: Efectivo baja 14999.5 respecto de la foto anterior.
  - `modificar-con-cuenta-ajena`: `accountId` `20000000-0000-7000-8000-000000000001` → 400 en `accountId`.
  - `modificar-con-campos-invalidos`: `{"amount": -1, "occurredAt": "ayer"}` → 400 con `amount` y `occurredAt`.
  - `modificar-parche-vacio`: `{}` → 400 en `body`.
  - `modificar-a-transferencia-sin-destino`: `{"type": "TRANSFER"}` → 400 en `destinationAccountId`.
  - `modificar-a-transferencia`: `{"type": "TRANSFER", "destinationAccountId": "{{idDestino}}"}` → 200
    con `categoryId` null.
  - `modificar-a-gasto-sin-categoria`: `{"type": "EXPENSE"}` sobre `idFruta` → 400 en `categoryId`.
  - `modificar-a-gasto`: `{"type": "EXPENSE", "categoryId": "{{idMercado}}"}` → 200 con
    `destinationAccountId` null.
  - `modificar-id-mal-formado`: `PATCH /transactions/abc` → 400 en `id`.
  - `modificar-movimiento-ajeno`: `idFruta` con `{{accessToken}}` (el usuario del escenario) → 404 en `id`.
  - `modificar-sin-credenciales` (sin `auth`) y `modificar-con-basic` → 401 `UNAUTHENTICATED`.

  Verificar que hoy fallan (`bru run transactions -r --env local` desde `bruno/`, tras correr
  `auth/` para tener `accessToken`).
- [x] 1.3 Escribir los requests de eliminación a continuación:
  - `eliminar-movimiento-ajeno`: `idFruta` con `{{accessToken}}` → 404 en `id`.
  - `eliminar`: `DELETE` de `idFruta` → 204.
  - `saldos-tras-eliminar`: Efectivo vuelve a la foto de `saldos-antes-de-modificar` más los 30000.5
    del gasto original.
  - `eliminar-de-nuevo`: el mismo `DELETE` → 404 en `id`.
  - `eliminar-id-mal-formado` → 400 en `id`.
  - `eliminar-sin-credenciales` y `eliminar-con-basic` → 401 `UNAUTHENTICATED`.

  Verificar que hoy fallan. Actualizar el `docs` de `bruno/transactions/folder.yml`: los requests
  de movimiento ajeno usan el `accessToken` de `auth/`.

## 2. Dominio y puertos

Skills: `java-architect`.

- [x] 2.1 Crear `UpdateTransactionCommand` (campos como texto, igual que `CreateTransactionCommand`),
  `UpdateTransactionPort` y `DeleteTransactionPort`, y sumar a `TransactionRepositoryPort`
  `findByIdAndUser`, `update` y `deleteByIdAndUser`. Verificar con `.\gradlew.bat compileJava`.

## 3. Referencias compartidas con el alta

Skills: `java-architect`.

- [x] 3.1 Extraer `cuentaPropia` y `categoria` de `TransactionBatchValidator` a
  `ReferenciasDelUsuario`, con el prefijo de campo como parámetro. Verificar con
  `CreateTransactionsUseCaseTest` en verde sin tocar sus aserciones.

## 4. Caso de uso de modificación

Skills: `java-architect`, `java-exceptions`.

- [x] 4.1 `UpdateTransactionUseCaseTest` en RED, con `StepVerifier` y mocks de los puertos:
  parche de monto y descripción conserva el resto; fecha con offset; `null` no cambia;
  gasto→transferencia con y sin destino; transferencia→gasto con y sin categoría;
  gasto→ingreso con categoría incompatible; categoría en transferencia; destino en gasto; origen
  igual al destino; cuenta y categoría ajenas; cuenta guardada desactivada con parche de
  descripción da 200; movimiento inexistente da 404 `NOT_FOUND` en `id`; `update` sin filas da 404.
  Ningún caso de error llama a `update`.
- [x] 4.2 Implementar `UpdateTransactionUseCase`. Verificar con `UpdateTransactionUseCaseTest` en verde.

## 5. Caso de uso de eliminación

Skills: `java-architect`, `java-exceptions`.

- [x] 5.1 `DeleteTransactionUseCaseTest` en RED: borra y completa vacío; sin filas, 404 `NOT_FOUND`
  en `id`.
- [x] 5.2 Implementar `DeleteTransactionUseCase`. Verificar con `DeleteTransactionUseCaseTest` en verde.

## 6. Web y seguridad

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 6.1 `UpdateTransactionRequestTest` en RED: cada campo opcional, y los formatos inválidos de
  `amount`, `type`, `description` (en blanco y 256 caracteres) y `occurredAt` dan violación en su
  campo. Implementar `UpdateTransactionRequest`. Verificar con la clase en verde.
- [x] 6.2 `TransactionControllerTest` en RED: PATCH válido llega al puerto y responde 200; parche
  vacío 400 en `body` sin llamar al puerto; id mal formado 400 en `id` en PATCH y DELETE; DELETE
  responde 204; el 404 del puerto se propaga. Implementar los dos métodos en `TransactionController`
  con el id parseado a mano. Verificar con la clase en verde.
- [x] 6.3 `TransactionsIT`: PATCH y DELETE sin credencial y con el Basic compartido dan 401
  `UNAUTHENTICATED`. Verificar con la clase en verde.
- [x] 6.4 Test de preflight CORS en RED (`OPTIONS` con `Access-Control-Request-Method: PATCH`),
  agregar `PATCH` en `SecurityConfig`. Verificar con el test en verde.

## 7. Persistencia

- [x] 7.1 Implementar en `TransactionR2dbcAdapter` el `SELECT`, el `UPDATE` (con
  `amount_base = amount`) y el `DELETE`, todos filtrados por `id` y `user_id` con bind variables.
  Sin test de suite (adapter excluido de cobertura): lo verifican los requests de los grupos 1.2 y
  1.3 en verde.

## 8. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 8.1 `docs/api/contrato-api.md`: los dos endpoints con ejemplos y errores, el índice, `PATCH`
  en CORS, y quitar "editar o borrar movimientos" de lo que el API todavía no tiene. Verificar
  leyendo las secciones.
- [x] 8.2 `bruno-personal/movimientos/modificar.yml`: PATCH con **todos** los campos modificables
  y valores de ejemplo reales (ids de `registrar.yml`), y un `docs` con los casos de cambio de tipo
  y la regla de `null`. `bruno-personal/movimientos/eliminar.yml` con un id de ejemplo. Actualizar
  el `docs` de `registrar.yml`, que dice que no hay cómo borrar. Comparar ruta, cuerpo y
  autenticación con `bruno/`. No se ejecutan.

## 9. Verificación

- [x] 9.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 9.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
