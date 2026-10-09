# usuarios Specification

## Purpose
Los datos del propio usuario: qué acepta el alta, cómo se leen y se modifican desde el perfil y cómo
se cambia la contraseña. El usuario siempre sale del token, nunca de la URL.

## Requirements

### Requirement: Celular en el alta
`POST /api/auth/register` SHALL aceptar `phone` opcional: un `+` opcional seguido de 7 a 15 dígitos,
sin espacios ni separadores, tolerando espacios en el borde, que se recortan. La respuesta del alta
SHALL incluir `phone`, en `null` si no vino.

#### Scenario: Alta con celular
- **WHEN** el alta trae `"phone": " +573001234567 "` con el resto de campos válidos
- **THEN** la respuesta es 201 con `phone` `+573001234567`

#### Scenario: Alta sin celular
- **WHEN** el alta no trae `phone`
- **THEN** la respuesta es 201 con `phone` en `null`

#### Scenario: Celular mal formado en el alta
- **WHEN** el alta trae `"phone": "300 123"` o `"phone": "abc1234567"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

### Requirement: Forma del perfil
`GET /api/users/me` y `PATCH /api/users/me` SHALL presentar el perfil como `{id, email, firstName,
lastName, phone, baseCurrencyCode, timezone}`. La respuesta MUST NOT incluir la contraseña ni su
hash.

#### Scenario: Leer el perfil
- **WHEN** un usuario registrado con `phone` `3001234567` y sin apellido pide `GET /api/users/me` con su token
- **THEN** la respuesta es 200 con su `id`, `email`, `firstName`, `phone` `3001234567`, `lastName` en `null`, `baseCurrencyCode` y `timezone`, y sin `password` ni `passwordHash`

### Requirement: El perfil es el del usuario del token
Las rutas de `/api/users/me` SHALL operar sobre el usuario del `sub` del token y SHALL exigir Bearer.
Sin credencial o con el Basic compartido SHALL responder 401 `UNAUTHENTICATED` en `authorization`.
Un token válido de un usuario desactivado o borrado SHALL responder 401 `UNAUTHENTICATED` en
`authorization`.

#### Scenario: Sin credenciales
- **WHEN** se pide `GET /api/users/me`, `PATCH /api/users/me` o `PUT /api/users/me/password` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Con el Basic compartido
- **WHEN** se pide cualquiera de esas rutas con el Basic de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la cabecera `WWW-Authenticate: Bearer`

#### Scenario: Token de un usuario desactivado
- **WHEN** un usuario con `is_active` en falso, o con `deleted_at`, pide `GET /api/users/me` con un token aún vigente
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Modificación parcial del perfil
`PATCH /api/users/me` SHALL aceptar cualquier subconjunto de `firstName`, `lastName`, `email`, `phone`
y `timezone`, con las reglas de formato del alta. Un campo ausente o en `null` SHALL conservar su
valor. `lastName` y `phone` en blanco SHALL borrar el valor. La respuesta SHALL ser 200 con el perfil
tal como quedó.

#### Scenario: Cambiar nombre, celular y zona
- **WHEN** el parche trae `{"firstName": " Ana María ", "phone": "3109876543", "timezone": "Europe/Madrid"}`
- **THEN** la respuesta es 200 con `firstName` `Ana María`, `phone` `3109876543` y `timezone` `Europe/Madrid`, y el resto del perfil sin cambios

#### Scenario: Borrar el celular y el apellido
- **WHEN** el parche trae `{"phone": "", "lastName": "  "}`
- **THEN** la respuesta es 200 con `phone` y `lastName` en `null`

#### Scenario: El GET refleja el cambio
- **WHEN** después de un `PATCH` el usuario pide `GET /api/users/me`
- **THEN** el perfil es el de la respuesta del `PATCH`

### Requirement: La moneda base no cambia desde el perfil
Si el parche trae `baseCurrencyCode`, el sistema SHALL omitirlo: no lo valida, no lo cambia y no da
error por él. Un parche cuyo único campo es `baseCurrencyCode` SHALL contar como parche vacío.

#### Scenario: Moneda base junto a otros campos
- **WHEN** el parche trae `{"firstName": "Ana", "baseCurrencyCode": "USD"}` y la moneda base del usuario es `COP`
- **THEN** la respuesta es 200 con `firstName` `Ana` y `baseCurrencyCode` `COP`

#### Scenario: Solo la moneda base
- **WHEN** el parche trae solo `{"baseCurrencyCode": "USD"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

### Requirement: Formato del parche del perfil
Cada campo enviado SHALL cumplir el formato del alta. `firstName`, `email` y `timezone` MUST NOT ir
en blanco. Un parche sin ningún campo modificable SHALL responder 400 `VALIDATION_ERROR` en `body`.
Ningún error SHALL modificar el perfil.

#### Scenario: Parche vacío
- **WHEN** el cuerpo es `{}` o solo trae campos en `null`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

#### Scenario: Nombre en blanco o demasiado largo
- **WHEN** el parche trae `"firstName": "   "` o un nombre de 101 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `firstName`

#### Scenario: Celular, correo o zona mal formados
- **WHEN** el parche trae `"phone": "12-34"`, `"email": "sin-arroba"` o `"timezone": "Marte/Base"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo correspondiente, uno por campo

### Requirement: Cambiar el correo exige la contraseña actual
Si `email`, normalizado en minúsculas y sin espacios, difiere del actual, el parche SHALL traer
`currentPassword` y SHALL coincidir con la contraseña del usuario. El correo nuevo SHALL estar libre
sin distinguir mayúsculas. Si vuelve a ser el mismo correo, no SHALL exigirse la contraseña.

#### Scenario: Cambio de correo con la contraseña correcta
- **WHEN** el parche trae `{"email": " Nuevo@Correo.com ", "currentPassword": "<la actual>"}`
- **THEN** la respuesta es 200 con `email` `nuevo@correo.com`, y el login con el correo nuevo y la misma contraseña funciona

#### Scenario: Cambio de correo sin la contraseña
- **WHEN** el parche trae un `email` distinto del actual sin `currentPassword`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `currentPassword` y el correo no cambia

#### Scenario: Cambio de correo con la contraseña equivocada
- **WHEN** el parche trae un `email` distinto del actual y un `currentPassword` que no es la contraseña
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword` y el correo no cambia

#### Scenario: El mismo correo en otra caja
- **WHEN** el parche trae el correo actual en mayúsculas y sin `currentPassword`
- **THEN** la respuesta es 200 y el correo sigue igual, en minúsculas

#### Scenario: Correo de otro usuario
- **WHEN** el parche trae, con la contraseña correcta, un correo que ya usa otro usuario, en cualquier caja
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `email` y el correo no cambia

### Requirement: Cambio de contraseña
`PUT /api/users/me/password` SHALL recibir `{currentPassword, newPassword}`, los dos obligatorios.
`newPassword` sigue las reglas del alta: al menos 8 caracteres y no más de 72 bytes en UTF-8. Si
`currentPassword` coincide, SHALL guardar la nueva y responder 204 sin cuerpo.

#### Scenario: Cambio correcto
- **WHEN** el usuario envía su contraseña actual y una nueva de 12 caracteres
- **THEN** la respuesta es 204, el login con la nueva funciona y con la anterior da 401 `INVALID_CREDENTIALS`

#### Scenario: Contraseña actual equivocada
- **WHEN** `currentPassword` no es la contraseña del usuario
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword` y la contraseña no cambia

#### Scenario: Contraseña nueva que no cumple las reglas
- **WHEN** `newPassword` tiene 7 caracteres o pasa de 72 bytes
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `newPassword`

#### Scenario: Faltan campos
- **WHEN** el cuerpo no trae `currentPassword` o `newPassword`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en cada campo que falta

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
