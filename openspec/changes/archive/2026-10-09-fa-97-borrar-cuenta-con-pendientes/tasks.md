# Tasks

## 1. Bruno primero, fallando

Skills: `bruno-cli`.

- [x] 1.1 Abrir hueco en `bruno/accounts/`: `borrar-cuenta-con-saldo.yml` pasa de 58 a 61,
  `saldar-caja-chica.yml` y `borrar-cuenta.yml` de 59–60 a 63–64, y del 61 en adelante todo corre 5.
  Verificar con un listado ordenado por `seq` que no quedan repetidos.
- [x] 1.2 Agregar tres pendientes con `POST /assistant/messages` y `{{tokenCuentas}}` (stub de
  `verificar-bruno.ps1`): un gasto en `caja chica` (58), una transferencia de `bolsillo` a
  `caja chica` (59) y un gasto en `bolsillo` (60). Cada uno guarda su id con `bru.setVar` y afirma
  `"status": "PENDING"`.
- [x] 1.3 Agregar `pendientes-tras-borrado-rechazado.yml` (62): después del 409 por saldo, los tres
  pendientes siguen en `GET /transactions/pending`. Pasa ya hoy: documenta que un borrado rechazado
  no elimina nada.
- [x] 1.4 Agregar `pendientes-tras-borrar.yml` (65): después del 204, el gasto y la transferencia de
  `Caja chica` ya no salen en `GET /transactions/pending` y el gasto de `Bolsillo` sí. Agregar a
  `cuentas-tras-borrar.yml` que `Bolsillo` sigue en 230000. Verificar que 1.4 falla contra la app
  actual (hoy los dos pendientes siguen).

## 2. El borrado se lleva los pendientes

Skills: `java-architect`.

- [x] 2.1 `AccountR2dbcAdapter.BORRAR` pasa a la sentencia con CTE de `design.md`, y `softDelete` lee
  el `count` en vez de `rowsUpdated`. Lo verifica `pendientes-tras-borrar.yml` en `bru run` (el
  adapter no tiene test en la suite).
- [x] 2.2 Javadoc de `AccountRepositoryPort.softDelete` y del `DeleteAccountPort`: el borrado elimina
  los pendientes de la cuenta. Verificar con `gradlew build` que nada más cambió.

## 3. Colección personal

Skills: `bruno-cli`.

- [x] 3.1 Actualizar la ayuda de `bruno-personal/cuentas/borrar.yml`: borrar elimina los pendientes
  de la cuenta. Ruta, cuerpo y autenticación no cambian; sin ejecutarlo.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo real de requests y aserciones.
