# Tasks

## 1. Bruno primero, fallando

Skills: `bruno-cli`.

- [x] 1.1 Correr los `seq` 41–58 de `bruno/accounts/` para abrir hueco después de
  `modificar-cuenta-con-basic.yml` (40) y verificar con un listado ordenado por `seq` que no quedan
  repetidos.
- [x] 1.2 Ajustar `modificar-cuenta-campos-del-sistema.yml`: el mismo cuerpo de tres campos ahora
  espera dos errores, en `currentBalance` y `type`. Verificar que falla contra la app actual (hoy da
  tres). Que `Bolsillo` sigue activa lo afirma `cuentas-tras-modificar.yml` con
  `isActive` en `true`.
- [x] 1.3 Agregar el ciclo sobre `{{idCuentaEfectivo}}` (`Bolsillo`, saldo 250000): desactivar (200,
  `isActive` false, `currentBalance` 250000), desactivar otra vez (200), `GET /accounts` sin ella,
  `GET /accounts?includeInactive=true` con ella en `false`, gasto en ella (400 en `[0].accountId`),
  reactivar (200, `true`), el mismo gasto (201) y `GET /accounts` con el saldo reducido. Verificar
  que el desactivar falla hoy con 400 en `isActive`.
- [x] 1.4 Agregar desactivar y renombrar a la vez, `{"currencyCode": "USD", "isActive": false}`
  sobre `Bolsillo` (409 en `currencyCode`) seguido de un `GET` que la muestra activa, y
  `{"isActive": null}` (400 en `body`).
- [x] 1.5 Agregar desactivar la `Efectivo` del escenario con `{{tokenCuentas}}` (404 en `id`) seguido
  del `GET /accounts` de su dueño que todavía la trae, y reactivar `Davivienda` borrada (404 en `id`).
- [x] 1.6 Revisar que los requests posteriores (`cuentas-tras-borrar.yml` y siguientes) no dependan
  del saldo de `Bolsillo`, que cambia con el gasto de 1.3. Verificarlo leyendo sus aserciones.

## 2. El parche admite `isActive`

Skills: `java-architect`.

- [x] 2.1 `UpdateAccountRequest`: quitar el `@Null` de `isActive`, incluirlo en `sinCambios()` y en
  `toCommand()`. Verificar con `UpdateAccountRequestTest`: `isActive` sin violación,
  `{"isActive": false}` no es parche vacío y `{"isActive": null}` sí.
- [x] 2.2 `UpdateAccountCommand` gana `Boolean isActive`, y `UpdateAccountUseCase.aplicar` lo usa si
  viene y conserva el guardado si no. Verificar con `UpdateAccountUseCaseTest`: desactiva, reactiva,
  conserva en null, y un 409 de moneda con `isActive` en `false` no llama a `update`.
- [x] 2.3 `AccountControllerTest`: `PATCH` con `{"isActive": false}` responde 200 y pasa el estado al
  puerto; `{"isActive": null}` responde 400 en `body`; `{"currentBalance": 1, "type": "CASH",
  "isActive": false}` responde dos errores, sin `isActive`, y no llama al puerto.
- [x] 2.4 `AccountR2dbcAdapter.ACTUALIZAR` escribe `is_active = :isActive`. Lo verifica el ciclo de
  1.3 en `bru run` (el adapter no tiene test en la suite).
- [x] 2.5 Corregir el Javadoc de `UpdateAccountRequest` y `UpdateAccountUseCase` que dice que el
  estado se rechaza.

## 3. Colección personal

Skills: `bruno-cli`.

- [x] 3.1 Agregar a `bruno-personal/` un request `PATCH /api/accounts/{id}` con `{"isActive": false}`
  (Bearer, misma ruta y cuerpo que el de `bruno/`) y, si existe uno con `isActive` esperando 400,
  ajustarlo. Sin ejecutarlo: apunta a Neon. Verificar comparando ruta, cuerpo y autenticación con
  los de `bruno/accounts/`.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo real de requests y aserciones.
