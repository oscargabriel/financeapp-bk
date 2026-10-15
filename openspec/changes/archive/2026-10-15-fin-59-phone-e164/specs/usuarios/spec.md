## MODIFIED Requirements

### Requirement: Celular en el alta
`POST /api/auth/register` SHALL aceptar `phone` opcional en formato internacional E.164: un `+`, un
primer dígito de 1 a 9 y de 7 a 15 dígitos en total, sin espacios ni separadores, tolerando espacios
en el borde, que se recortan. El celular SHALL guardarse con el `+`. La respuesta del alta SHALL
incluir `phone`, en `null` si no vino.

#### Scenario: Alta con celular
- **WHEN** el alta trae `"phone": " +573001234567 "` con el resto de campos válidos
- **THEN** la respuesta es 201 con `phone` `+573001234567`

#### Scenario: Alta sin celular
- **WHEN** el alta no trae `phone`
- **THEN** la respuesta es 201 con `phone` en `null`

#### Scenario: Celular mal formado en el alta
- **WHEN** el alta trae `"phone": "300 123"` o `"phone": "abc1234567"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

#### Scenario: Celular sin indicativo en el alta
- **WHEN** el alta trae `"phone": "3001234567"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

#### Scenario: Celular con separadores en el alta
- **WHEN** el alta trae `"phone": "+57 300 123 4567"` o `"phone": "+57-3001234567"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

#### Scenario: Indicativo que empieza por cero
- **WHEN** el alta trae `"phone": "+0573001234567"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

#### Scenario: Celular con más de 15 dígitos
- **WHEN** el alta trae `"phone": "+5730012345678901"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el usuario no se crea

### Requirement: Forma del perfil
`GET /api/users/me` y `PATCH /api/users/me` SHALL presentar el perfil como `{id, email, firstName,
lastName, phone, baseCurrencyCode, timezone, hasPassword, googleLinked}`. `hasPassword` SHALL ser
verdadero si el usuario tiene contraseña, y `googleLinked` si tiene una cuenta Google vinculada. La
respuesta MUST NOT incluir la contraseña, su hash ni el identificador de Google.

#### Scenario: Leer el perfil
- **WHEN** un usuario registrado con `phone` `+573001234567` y sin apellido pide `GET /api/users/me` con su token
- **THEN** la respuesta es 200 con su `id`, `email`, `firstName`, `phone` `+573001234567`, `lastName` en `null`, `baseCurrencyCode` y `timezone`, y sin `password` ni `passwordHash`

#### Scenario: Usuario registrado con contraseña
- **WHEN** un usuario registrado con correo y contraseña, que nunca entró con Google, pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `true` y `googleLinked` `false`

#### Scenario: Usuario registrado con Google
- **WHEN** un usuario registrado con Google pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `false` y `googleLinked` `true`, y no trae el identificador de Google

#### Scenario: Usuario con los dos métodos
- **WHEN** un usuario con contraseña cuya cuenta se vinculó a Google en su primer login con Google pide `GET /api/users/me`
- **THEN** la respuesta trae `hasPassword` `true` y `googleLinked` `true`

#### Scenario: El PATCH trae los dos campos
- **WHEN** un usuario registrado con Google modifica su nombre con `PATCH /api/users/me`
- **THEN** la respuesta es 200 con `hasPassword` `false` y `googleLinked` `true`

### Requirement: Modificación parcial del perfil
`PATCH /api/users/me` SHALL aceptar cualquier subconjunto de `firstName`, `lastName`, `email`, `phone`,
`timezone` y `baseCurrencyCode`, con las reglas de formato del alta. Un campo ausente o en `null`
SHALL conservar su valor. `lastName` y `phone` en blanco SHALL borrar el valor. La respuesta SHALL ser
200 con el perfil tal como quedó.

#### Scenario: Cambiar nombre, celular y zona
- **WHEN** el parche trae `{"firstName": " Ana María ", "phone": "+573109876543", "timezone": "Europe/Madrid"}`
- **THEN** la respuesta es 200 con `firstName` `Ana María`, `phone` `+573109876543` y `timezone` `Europe/Madrid`, y el resto del perfil sin cambios

#### Scenario: Borrar el celular y el apellido
- **WHEN** el parche trae `{"phone": "", "lastName": "  "}`
- **THEN** la respuesta es 200 con `phone` y `lastName` en `null`

#### Scenario: El GET refleja el cambio
- **WHEN** después de un `PATCH` el usuario pide `GET /api/users/me`
- **THEN** el perfil es el de la respuesta del `PATCH`

### Requirement: Formato del parche del perfil
Cada campo enviado SHALL cumplir el formato del alta. `firstName`, `email`, `timezone` y
`baseCurrencyCode` MUST NOT ir en blanco. Un parche sin ningún campo modificable SHALL responder 400
`VALIDATION_ERROR` en `body`; `currentPassword` no cuenta como campo modificable. Ningún error SHALL
modificar el perfil.

#### Scenario: Parche vacío
- **WHEN** el cuerpo es `{}`, solo trae campos en `null` o solo trae `currentPassword`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

#### Scenario: Nombre en blanco o demasiado largo
- **WHEN** el parche trae `"firstName": "   "` o un nombre de 101 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `firstName`

#### Scenario: Celular, correo o zona mal formados
- **WHEN** el parche trae `"phone": "12-34"`, `"email": "sin-arroba"` o `"timezone": "Marte/Base"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo correspondiente, uno por campo

#### Scenario: Celular sin indicativo en el parche
- **WHEN** el parche trae `"phone": "3109876543"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `phone` y el perfil no cambia

#### Scenario: Moneda en blanco o mal formada
- **WHEN** el parche trae `"baseCurrencyCode": "  "`, `"baseCurrencyCode": "US"` o `"baseCurrencyCode": "U5D"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`
