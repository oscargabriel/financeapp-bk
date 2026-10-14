## ADDED Requirements

### Requirement: Un usuario sin contraseña no puede probar la actual
Para un usuario registrado solo con Google, `PUT /api/users/me/password` y un `PATCH /api/users/me`
que cambie el correo SHALL responder como a una contraseña actual equivocada: 400
`INVALID_CREDENTIALS` en `currentPassword`, sin cambiar nada y sin cerrar sesiones.

#### Scenario: Cambio de contraseña sin tener una
- **WHEN** un usuario registrado con Google pide `PUT /api/users/me/password` con cualquier `currentPassword` y una `newPassword` válida
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword` y el access con el que se pidió sigue sirviendo

#### Scenario: Cambio de correo sin tener contraseña
- **WHEN** un usuario registrado con Google pide `PATCH /api/users/me` con un `email` distinto y cualquier `currentPassword`
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword` y el correo no cambia
