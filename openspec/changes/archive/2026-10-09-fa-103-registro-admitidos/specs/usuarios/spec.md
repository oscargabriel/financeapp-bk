## ADDED Requirements

### Requirement: Alta solo para correos admitidos
Con la restricción encendida, `POST /api/auth/register` SHALL crear el usuario solo si su correo,
recortado y en minúsculas, está en la lista de admitidos, como correo exacto o porque la lista
admite su dominio completo. Un correo que no esté admitido MUST recibir 403
`REGISTRATION_NOT_ALLOWED` en el campo `email`, y no se crean ni el usuario ni sus categorías.

#### Scenario: Correo admitido de forma exacta, escrito con mayúsculas
- **WHEN** la lista admite `invitado@financeapp.local` y el alta trae `"email": "Invitado@FinanceApp.LOCAL"` con el resto de campos válidos
- **THEN** la respuesta es 201 con `email` `invitado@financeapp.local`

#### Scenario: Correo admitido por su dominio
- **WHEN** la lista admite `@bruno.local` y el alta trae `"email": "registro-1@bruno.local"` con el resto de campos válidos
- **THEN** la respuesta es 201

#### Scenario: Correo no admitido
- **WHEN** la lista no admite ni `fuera@no-admitido.local` ni su dominio, y el alta trae ese correo con el resto de campos válidos
- **THEN** la respuesta es 403 con un error `REGISTRATION_NOT_ALLOWED` en el campo `email`
- **AND** un login con ese correo y esa contraseña responde 401 `INVALID_CREDENTIALS`, porque el usuario no se creó

#### Scenario: Un dominio admitido no admite sus subdominios
- **WHEN** la lista admite `@bruno.local` y el alta trae `"email": "ana@otro.bruno.local"`
- **THEN** la respuesta es 403 con un error `REGISTRATION_NOT_ALLOWED` en el campo `email`

#### Scenario: Sin credencial
- **WHEN** se pide el alta de un correo no admitido sin cabecera `Authorization`, o con un Bearer válido
- **THEN** la respuesta es 401 `UNAUTHENTICATED`, no 403

### Requirement: Orden de las validaciones del alta
Con la restricción encendida, `POST /api/auth/register` SHALL resolver los errores en este orden:
primero los 400 `VALIDATION_ERROR` (formato del cuerpo y moneda inexistente), después el 403
`REGISTRATION_NOT_ALLOWED` y al final el 409 `DUPLICATE_RESOURCE` de correo repetido. A un correo
no admitido MUST NOT revelársele si ya existe una cuenta con él.

#### Scenario: Cuerpo inválido con un correo no admitido
- **WHEN** el alta trae un correo no admitido y `"password": "corta"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `password`, y ningún `REGISTRATION_NOT_ALLOWED`

#### Scenario: Moneda inexistente con un correo no admitido
- **WHEN** el alta trae un correo no admitido y `"baseCurrencyCode": "XYZ"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`

#### Scenario: Correo no admitido que ya tiene cuenta
- **WHEN** un usuario se registró con un correo y después ese correo sale de la lista, y otra alta trae el mismo correo
- **THEN** la respuesta es 403 `REGISTRATION_NOT_ALLOWED` en `email`, no 409

#### Scenario: Correo admitido que ya tiene cuenta
- **WHEN** el alta trae un correo admitido que ya tiene cuenta, sin distinguir mayúsculas
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `email`

### Requirement: La restricción del alta se apaga por configuración
La restricción del alta SHALL estar encendida salvo que la configuración la apague de forma
explícita. Apagada, `POST /api/auth/register` MUST aceptar cualquier correo que cumpla las demás
reglas, sin consultar la lista.

#### Scenario: Restricción apagada
- **WHEN** la restricción está apagada y el alta trae un correo que la lista no admite, con el resto de campos válidos
- **THEN** la respuesta es 201

#### Scenario: Sin configuración explícita
- **WHEN** la aplicación arranca sin definir la restricción
- **THEN** la restricción queda encendida

### Requirement: El login no consulta la lista de admitidos
`POST /api/auth/login` MUST NOT consultar la lista de admitidos: un usuario activo que se registró
inicia sesión aunque su correo ya no esté en la lista. Para cortarle el acceso se desactiva el
usuario.

#### Scenario: Usuario registrado cuyo correo no está en la lista
- **WHEN** `prueba@financeapp.local`, que existe, está activo y no está en la lista, inicia sesión con su contraseña
- **THEN** la respuesta es 200 con un `accessToken`
