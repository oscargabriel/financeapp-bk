# Design

## Context

El PATCH de FA-24 lee la cuenta con `findActiveByIdAndUser`, que no filtra `is_active`, le aplica
el parche en `UpdateAccountUseCase.aplicar` y la escribe con un único `UPDATE ... RETURNING`. Hoy
`aplicar` copia `guardada.active()` y el `UPDATE` no toca `is_active`. `UpdateAccountRequest` lee
`isActive` solo para rechazarlo con `@Null`.

Lo que ya hace cumplir el estado no se toca: `AccountR2dbcAdapter.findByUser` filtra `is_active`
según `includeInactive`, `ReferenciasDelUsuario.cuentaPropia` rechaza una cuenta desactivada en el
alta y en el PATCH de movimientos, y `GetBalanceUseCase` y `AssistantUseCase` piden solo las activas.

## Goals / Non-Goals

**Goals:**
- Que el estado viaje por el mismo camino que los demás campos del parche: leer, validar todo,
  escribir una vez.

**Non-Goals:**
- Un formato propio para un `isActive` mal tipado (`"si"`, `1`). Sale como cualquier campo del
  cuerpo que Jackson no puede convertir; no se agrega validación aparte.
- Cambiar la validación de movimientos o los reportes.

## Decisions

### `isActive` en el PATCH, no un endpoint aparte

Lo decidió el usuario el 09-10-2026, entre tres opciones:

- **`isActive` en el PATCH existente** (elegida). Un solo endpoint de edición para el front, sin
  ruta nueva, sin puerto ni caso de uso nuevos: el estado es un campo más de la cuenta y la
  respuesta ya lo trae.
- **`POST /accounts/{id}/deactivate` y `/activate`**, como `/transactions/{id}/approve` y
  `/reject`. Descartada: dos rutas, dos puertos y dos casos de uso para escribir una columna que
  no tiene reglas propias, porque se desactiva con cualquier saldo.
- **`PUT /accounts/{id}/active`** con `{"active": bool}`. Descartada: un sub-recurso sin precedente
  en el API.

Esto revierte una decisión de FA-24: allí `isActive` se rechazaba con el mensaje "no se cambia con
este endpoint", porque el endpoint de estado no existía todavía. No contradice `AGENTS.md`, que no
menciona el estado de las cuentas.

### El estado entra en el mismo `UPDATE`

`UpdateAccountCommand` gana `Boolean isActive`. `aplicar` lo usa si viene y, si no, conserva el
guardado, igual que los demás campos. El `UPDATE` de `AccountR2dbcAdapter` agrega
`is_active = :isActive`.

Así un parche que falla por la moneda (409) o por campos de crédito (400) no desactiva nada: todos
los chequeos corren antes de la única escritura. Se descartó un `UPDATE` separado para el estado:
dos escrituras por PATCH abrirían la puerta a dejar el estado cambiado y el resto no.

### Idempotencia sin código

Pedir el estado que la cuenta ya tiene escribe el mismo valor y devuelve la fila: 200. No hace
falta compararlo antes, y un 409 obligaría al cliente a leer la cuenta antes de cada clic.

### Con cualquier saldo

A diferencia del borrado (FA-25), desactivar se deshace y es una decisión explícita del usuario. La
cuenta sigue visible con `includeInactive=true`. El escenario de pruebas ya tiene una cuenta así:
`Nequi`, desactivada con 80000.

## Risks / Trade-offs

- [Un saldo desactivado no aparece en `GET /api/reports/balance`] → aceptado. La cuenta sigue en el
  listado con `includeInactive=true`, y reactivarla la devuelve al reporte.
- [Un cliente que contaba con el 400 en `isActive`] → solo `bruno/` lo verificaba
  (`modificar-cuenta-campos-del-sistema.yml`), y el front no lo usa. Se ajusta en este change.
- [Aprobar un pendiente sobre una cuenta desactivada mueve su saldo] → ya pasaba antes de este
  change con las desactivadas del escenario. Queda en FA-102.
