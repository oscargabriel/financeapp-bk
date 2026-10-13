# Spec Delta

## ADDED Requirements

### Requirement: Compras sobre una tarjeta desactivada o borrada
Desactivar o reactivar la tarjeta de una compra MUST NOT cambiar la compra ni sus cuotas. Cada cuota
SHALL seguir venciendo en su fecha y contar desde entonces en el saldo de la tarjeta. La compra
SHALL seguir en `GET /api/installment-purchases` y admitir `PATCH` y `DELETE` como con la tarjeta
activa. Una tarjeta con cuotas por venir, activa o desactivada, MUST NOT borrarse: SHALL responder
409 `RESOURCE_IN_USE` en `id`, como toda cuenta con movimientos programados.

#### Scenario: Desactivar la tarjeta con cuotas por venir
- **WHEN** el usuario desactiva con `{"isActive": false}` la tarjeta de una compra con cuotas por venir
- **THEN** la respuesta es 200, y la compra sigue en `GET /api/installment-purchases` con el mismo `remainingAmount` y el mismo `nextInstallment`

#### Scenario: Una cuota vence con la tarjeta desactivada
- **WHEN** una tarjeta desactivada tiene una compra a 3 cuotas de 100000, sin interés, cuya primera cuota ya venció
- **THEN** la tarjeta sale en `GET /api/accounts?includeInactive=true` con `currentBalance` -100000, y la compra sale en `GET /api/installment-purchases` con `paidCount` 1 y `remainingAmount` 200000

#### Scenario: Editar la compra con la tarjeta desactivada
- **WHEN** con la tarjeta desactivada el usuario envía `PATCH /api/installment-purchases/{id}` con `{"scope": "FUTURE", "description": "Audífonos viejos"}`
- **THEN** la respuesta es 200 con `description` "Audífonos viejos"

#### Scenario: Borrar la tarjeta desactivada
- **WHEN** el usuario borra la tarjeta desactivada, con saldo vigente 0 y cuotas por venir
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `id`, y la compra sigue en `GET /api/installment-purchases`

#### Scenario: Cancelar la compra con la tarjeta desactivada
- **WHEN** con la tarjeta desactivada el usuario cancela la compra con `DELETE /api/installment-purchases/{id}`
- **THEN** la respuesta es 204, la compra sale del listado y el capital de sus cuotas por venir vuelve al `availableCredit` de la tarjeta
