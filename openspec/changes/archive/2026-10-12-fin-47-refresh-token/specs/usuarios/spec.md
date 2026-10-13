# Spec Delta

## MODIFIED Requirements

### Requirement: Cambio de contraseña
`PUT /api/users/me/password` SHALL recibir `{currentPassword, newPassword}`, los dos obligatorios.
`newPassword` sigue las reglas del alta: al menos 8 caracteres y no más de 72 bytes en UTF-8. Si
`currentPassword` coincide, SHALL guardar la nueva y responder 204 sin cuerpo. Un cambio correcto
SHALL cerrar todas las sesiones del usuario: el access con el que se pidió y todo access y refresh
emitidos antes dejan de servir, como describe la capacidad `auth`. Un cambio rechazado no cierra
ninguna.

#### Scenario: Cambio correcto
- **WHEN** el usuario envía su contraseña actual y una nueva de 12 caracteres
- **THEN** la respuesta es 204, el login con la nueva funciona y con la anterior da 401 `INVALID_CREDENTIALS`

#### Scenario: El token del cambio deja de servir
- **WHEN** después de un cambio correcto el usuario pide `GET /api/users/me` con el mismo access con el que cambió la contraseña
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Contraseña actual equivocada
- **WHEN** `currentPassword` no es la contraseña del usuario
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword`, la contraseña no cambia y el access con el que se pidió sigue sirviendo

#### Scenario: Contraseña nueva que no cumple las reglas
- **WHEN** `newPassword` tiene 7 caracteres o pasa de 72 bytes
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `newPassword`

#### Scenario: Faltan campos
- **WHEN** el cuerpo no trae `currentPassword` o `newPassword`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en cada campo que falta
