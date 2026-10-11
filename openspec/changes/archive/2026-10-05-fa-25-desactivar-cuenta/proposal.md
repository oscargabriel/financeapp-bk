# Proposal

Origen: FA-25. El título dice "desactivar", pero el criterio 1 pide marcar `deleted_at`. Al tomarla
se decidió en el chat (05-10-2026):

- `DELETE` marca `deleted_at`, como el borrado de categorías (FA-21). No toca `is_active`.
  Desactivar y reactivar con `is_active` queda para FA-68, creada en el backlog.
- Una cuenta con saldo vigente distinto de cero no se borra: 409. Primero se deja en cero, con una
  transferencia o corrigiendo `initialBalance` (FA-24).

## Why

Una cuenta que el usuario cerró solo se puede quitar hoy con psql. Y como el nombre es único entre
las cuentas vivas, tampoco se puede liberar para crear otra con el mismo nombre.

## What Changes

- `DELETE /api/accounts/{id}`: marca `deleted_at` en la cuenta del usuario del token y responde 204
  sin cuerpo. La fila no se borra.
- Con `currentBalance` distinto de cero responde 409 `RESOURCE_IN_USE` en `currentBalance` y no
  borra nada. Vale igual para una deuda de tarjeta.
- Una cuenta desactivada se borra igual que una activa, con la misma regla de saldo.
- Se puede borrar una cuenta con movimientos. Los movimientos no cambian:
  - El reporte de movimientos los sigue mostrando, y el filtro por esa cuenta los sigue
    encontrando.
  - Un PATCH de uno de esos movimientos que no elige la cuenta borrada se acepta.
  - Ningún movimiento nuevo, ni un PATCH que la elija, puede usarla: responde 400 en el campo de la
    cuenta, como con una cuenta que no existe.
- La cuenta borrada deja de aparecer en `GET /api/accounts`, también con `includeInactive=true`, y
  su nombre queda libre para un alta o un PATCH.
- Un id mal formado responde 400 en `id`. Una cuenta inexistente, ya borrada o de otro usuario
  responde 404 en `id`, sin distinguir.

No es un cambio incompatible.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `cuentas`: agrega el borrado lógico, la regla del saldo y el comportamiento de los movimientos de
  una cuenta borrada.
- `reportes`: el reporte sigue mostrando los movimientos de una cuenta borrada, también filtrando
  por ella.

## Fuera de alcance

- Desactivar o reactivar una cuenta con `is_active`: FA-68.
- Restaurar una cuenta borrada, o listar las borradas.
- Pasar los movimientos de una cuenta borrada a otra.
- Impedir que un PATCH o un DELETE de un movimiento viejo mueva el saldo de una cuenta borrada
  (ver `design.md`).

## Impact

- Dominio: `DeleteAccountPort`, y `softDelete` en `AccountRepositoryPort`.
- Aplicación: `DeleteAccountUseCase`, que reutiliza el 404 de `UpdateAccountUseCase`.
- Web: `@DeleteMapping("/{id}")` en `AccountController`.
- Persistencia: un `UPDATE ... SET deleted_at = now()` en `AccountR2dbcAdapter`, filtrado por `id`,
  `user_id` y `deleted_at IS NULL`.
- `bruno/accounts/`: requests nuevos al final de la carpeta; replicados en `bruno-personal/cuentas/`.
- `docs/api/contrato-api.md`: el endpoint nuevo, el índice, `RESOURCE_IN_USE` y la lista de lo que
  el API todavía no tiene.
- Sin cambios en el esquema.
