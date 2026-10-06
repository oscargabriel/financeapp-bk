# Tasks

## 1. Bruno en RED

Skills: `bruno-cli`.

- [x] 1.1 Escribir en `bruno/accounts/`, a partir de seq 41, con `token: {{tokenCuentas}}` salvo
  donde se indica:
  - `cuenta-para-borrar` (POST `/accounts`): `{"name": "Caja chica", "type": "CASH", "currencyCode": "COP"}`
    → 201; guarda `idCajaChica`.
  - `gasto-en-caja-chica` (POST `/transactions`): un gasto de 30000 en `idCajaChica` con
    `idMercadoCuentas` → 201; guarda `idGastoCajaChica`.
  - `borrar-cuenta-con-saldo`: `DELETE /accounts/{{idCajaChica}}` → 409 `RESOURCE_IN_USE` en
    `currentBalance`.
  - `saldar-caja-chica` (PATCH): `{"initialBalance": 30000}` → 200, `currentBalance` 0.
  - `borrar-cuenta`: `DELETE /accounts/{{idCajaChica}}` → 204 sin cuerpo.
  - `cuentas-tras-borrar` (GET `?includeInactive=true`): 2 elementos, sin `idCajaChica`.
  - `borrar-cuenta-ya-borrada`: `idCajaChica` otra vez → 404 `NOT_FOUND` en `id`.
  - `reporte-con-cuenta-borrada` (GET `/reports/transactions` de ayer a mañana con
    `accountId={{idCajaChica}}`, fechas en un script previo): trae `idGastoCajaChica` con `accountId`
    `idCajaChica`, y en `totalsByType` la entrada `EXPENSE` con 30000 y `count` 1.
  - `modificar-gasto-de-cuenta-borrada`: `PATCH /transactions/{{idGastoCajaChica}}` con
    `{"description": "Corregido"}` → 200, `accountId` `idCajaChica`.
  - `gasto-en-cuenta-borrada`: POST `/transactions` con un gasto en `idCajaChica` → 400
    `VALIDATION_ERROR` en `[0].accountId`.
  - `recrear-cuenta-borrada` (POST `/accounts`): `{"name": "caja chica", "type": "CASH", "currencyCode": "COP"}`
    → 201 con `id` distinto de `idCajaChica`; guarda `idCajaChicaNueva`.
  - `borrar-cuenta-inexistente`: un UUID v7 que no existe → 404 en `id`.
  - `borrar-cuenta-id-mal-formado`: `DELETE /accounts/abc` → 400 `VALIDATION_ERROR` en `id`.
  - `borrar-cuenta-desactivada-con-saldo`: con `{{accessToken}}`, Nequi
    (`20000000-0000-7000-8000-000000000005`) → 409 `RESOURCE_IN_USE` en `currentBalance`.
  - `borrar-cuenta-ajena`: Efectivo del escenario (`20000000-0000-7000-8000-000000000001`) → 404 en `id`.
  - `cuenta-ajena-no-borrada` (GET con `{{accessToken}}`): `Efectivo` sigue.
  - `borrar-cuenta-sin-credenciales` (sin `auth`) y `borrar-cuenta-con-basic`: sobre
    `idCajaChicaNueva` → 401 `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`.

  Verificar que hoy fallan (405 en los DELETE) con `verificar-bruno.ps1 -RecargarDatos`.

## 2. Dominio y puertos

Skills: `java-architect`.

- [x] 2.1 Crear `DeleteAccountPort` (`Mono<Void> delete(UUID userId, UUID accountId)`) y sumar
  `Mono<Boolean> softDelete(UUID accountId, UUID userId)` a `AccountRepositoryPort`, con su
  implementación en `AccountR2dbcAdapter` (`UPDATE ... SET deleted_at = now()` filtrado por `id`,
  `user_id` y `deleted_at IS NULL`, con bind variables, y `rowsUpdated() > 0`). Sin test de suite
  (adapter excluido de cobertura): lo verifican los requests del grupo 1. Verificar que compila con
  `.\gradlew.bat compileJava`.

## 3. Caso de uso

Skills: `java-architect`, `java-exceptions`.

- [x] 3.1 `DeleteAccountUseCaseTest` en RED, con `StepVerifier` y el puerto mockeado: cuenta en cero
  completa vacío tras `softDelete`; cuenta no encontrada da 404 `NOT_FOUND` en `id` sin
  `softDelete`; saldo positivo y saldo negativo dan 409 `RESOURCE_IN_USE` en `currentBalance` sin
  `softDelete`; una desactivada en cero se borra; `softDelete` en `false` da 404.
- [x] 3.2 Implementar `DeleteAccountUseCase`, con `UpdateAccountUseCase.noEncontrada()`. Verificar
  con `DeleteAccountUseCaseTest` y `UpdateAccountUseCaseTest` en verde.

## 4. Web

Skills: `java-architect`, `java-exceptions`, `java-security`.

- [x] 4.1 `AccountControllerTest` en RED: un DELETE válido llega al puerto con el usuario del token y
  el id, y responde 204 sin cuerpo; id mal formado da 400 en `id` sin llamar al puerto; el 404 y el
  409 del puerto se propagan con su código y campo. Implementar el `@DeleteMapping("/{id}")` en
  `AccountController`. Verificar con la clase en verde.
- [x] 4.2 `AccountsIT`: el DELETE sin credencial y con el Basic compartido dan 401 con
  `WWW-Authenticate: Bearer`. Verificar con la clase en verde.

## 5. Contrato y colección personal

Skills: `bruno-cli`.

- [x] 5.1 `docs/api/contrato-api.md`: `DELETE /api/accounts/{id}` en el índice y su sección (204,
  borrado lógico, saldo en cero, movimientos, nombre liberado, 400, 404 y 409); `RESOURCE_IN_USE`
  con el caso del saldo; y en lo que el API todavía no tiene, cambiar "Borrar o desactivar cuentas"
  por "Desactivar o reactivar cuentas". Verificar leyendo las secciones.
- [x] 5.2 `bruno-personal/cuentas/borrar.yml`: el DELETE con un id de ejemplo y `docs` con la regla
  del saldo y qué pasa con los movimientos. Comparar ruta y autenticación con
  `bruno/accounts/borrar-cuenta.yml`. No se ejecuta.

## 6. Verificación

- [x] 6.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 6.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  requests, tests y aserciones reales.
