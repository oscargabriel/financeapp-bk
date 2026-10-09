# Tasks

## 1. Bruno primero, fallando

Skills: `bruno-cli`.

Cuentas del usuario `pendientes@` en `test-data.sql`: `Cuenta pendientes`
(`20000000-0000-7000-8000-000000000007`) y `Bolsillo pendientes`
(`20000000-0000-7000-8000-000000000008`).

- [x] 1.1 Abrir hueco en `bruno/pending/`: `aprobar-gasto.yml` … `reporte-tras-aprobar.yml` pasan de
  10–14 a 20–24, y `pendientes-tras-rechazar.yml` … `rechazar-con-basic.yml` de 16–29 a 25–38.
  `rechazar.yml` conserva el 15, que ahora cae dentro de la ventana de 1.2 (ver 1.3). Verificar con un listado
  ordenado por `seq` que no quedan repetidos.
- [x] 1.2 Agregar, con `{{tokenPendientes}}`:
  - `desactivar-bolsillo.yml` (10): `PATCH /accounts/…08` con `{"isActive": false}`, 200 e
    `isActive` false.
  - `aprobar-destino-desactivado.yml` (11): aprobar `{{idPendienteTraslado}}`, 409 con un único error
    `INVALID_STATE` en `destinationAccountId`.
  - `desactivar-cuenta-pendientes.yml` (12): igual sobre `…07`.
  - `aprobar-origen-desactivado.yml` (13): aprobar `{{idPendienteGasto}}`, 409 con un único error
    `INVALID_STATE` en `accountId`.
  - `aprobar-ambas-desactivadas.yml` (14): aprobar `{{idPendienteTraslado}}`, 409 con dos errores
    `INVALID_STATE`, uno en `accountId` y otro en `destinationAccountId`, comparados por contenido.
  - `pendientes-con-desactivadas.yml` (16): `GET /transactions/pending` con exactamente el traslado y
    el gasto, los dos `PENDING`.
  - `saldos-con-desactivadas.yml` (17): `GET /accounts?includeInactive=true`, `Cuenta pendientes` en
    970000 y `Bolsillo pendientes` en 0.
  - `reactivar-cuenta-pendientes.yml` (18) y `reactivar-bolsillo.yml` (19): `{"isActive": true}`,
    200 e `isActive` true.
- [x] 1.3 `rechazar.yml` queda en 15, con la `Cuenta pendientes` desactivada: su aserción (204) no
  cambia y su `docs` dice que rechazar no mira el estado de la cuenta. `pendientes-tras-rechazar.yml`
  y `saldos-tras-rechazar.yml` siguen valiendo después de las aprobaciones (lista vacía, 820000 y
  100000).
- [x] 1.4 Actualizar el `docs` de `bruno/pending/folder.yml` con la ventana de cuentas desactivadas.
  Verificar contra la app actual que 11, 13 y 14 fallan (hoy responden 200) y que la corrida de la
  carpeta se rompe ahí, no antes.

## 2. Aprobar exige cuentas activas

Skills: `java-architect`, `java-exceptions`.

- [x] 2.1 En `ApprovePendingTransactionUseCaseTest`, agregar el mock de `AccountRepositoryPort` y los
  tests: gasto con la cuenta desactivada → 409 `INVALID_STATE` en `accountId` sin llamar a
  `confirm`; transferencia con el destino desactivado → solo `destinationAccountId`; con las dos →
  los dos errores; transferencia con las dos activas → se confirma. Ajustar los tests existentes a la
  lectura de la cuenta. Verlos fallar por la razón correcta. Si `AccountMother` no tiene una cuenta
  desactivada, agregarla.
- [x] 2.2 `ApprovePendingTransactionUseCase`: leer la cuenta origen y, si hay, la destino con
  `findActiveByIdAndUser` después de `PendienteDelUsuario.buscar`, y fallar con
  `BadRequestException(CONFLICT)` con un `ErrorDetail` `INVALID_STATE` por cada cuenta desactivada
  ("La cuenta esta desactivada" / "La cuenta destino esta desactivada"). Javadoc de
  `ApprovePendingTransactionPort` con el 409 nuevo. Verifica `ApprovePendingTransactionUseCaseTest`
  en verde y los requests 11, 13 y 14 de `bruno/pending/`.

## 3. Colección personal

Skills: `bruno-cli`.

- [x] 3.1 Actualizar el `docs` de `bruno-personal/movimientos/aprobar.yml`: con una cuenta
  desactivada responde 409, y la salida es reactivarla o rechazar el pendiente. Ruta, cuerpo y
  autenticación no cambian; sin ejecutarlo.

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea reales.
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con
  el conteo real de requests y aserciones.
