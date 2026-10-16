# Spec Delta

## MODIFIED Requirements

### Requirement: Forma del perfil
`GET /api/users/me` y `PATCH /api/users/me` SHALL presentar el perfil como `{id, email, firstName,
lastName, phone, baseCurrencyCode, timezone, hasPassword, googleLinked, telegramLinked}`.
`hasPassword` SHALL ser verdadero si el usuario tiene contraseña, `googleLinked` si tiene una cuenta
Google vinculada, y `telegramLinked` si tiene un chat de Telegram vinculado y su `phone` sigue en la
lista de admitidos. La respuesta MUST NOT incluir la contraseña, su hash, el identificador de Google
ni el del chat.

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

#### Scenario: Usuario sin chat de Telegram
- **WHEN** un usuario recién registrado pide `GET /api/users/me`
- **THEN** la respuesta trae `telegramLinked` `false` y no trae el identificador del chat

#### Scenario: Chat vinculado con el teléfono admitido
- **WHEN** un usuario cuyo chat se vinculó compartiendo su contacto, y cuyo `phone` sigue admitido, pide `GET /api/users/me`
- **THEN** la respuesta trae `telegramLinked` `true`

#### Scenario: Chat guardado con un teléfono que ya no está admitido
- **WHEN** un usuario con un chat de Telegram guardado, cuyo `phone` no está en la lista de admitidos, pide `GET /api/users/me`
- **THEN** la respuesta trae `telegramLinked` `false`

## ADDED Requirements

### Requirement: La lista de admitidos acepta teléfonos solo para el bot
La lista de admitidos SHALL aceptar, además de correos y dominios, teléfonos en el mismo formato
E.164 de `phone`, con el `+`. Un teléfono de la lista solo admite vincular un chat de Telegram:
`POST /api/auth/register` MUST NOT tenerlo en cuenta, ni por el correo ni por el `phone` del alta.

#### Scenario: Alta con el celular admitido y el correo no admitido
- **WHEN** la lista admite `+573009990001` pero no el correo `fuera@no-admitido.local` ni su dominio, y el alta trae ese correo con `"phone": "+573009990001"` y el resto de campos válidos
- **THEN** la respuesta es 403 con un error `REGISTRATION_NOT_ALLOWED` en el campo `email`

### Requirement: Cambiar el celular desvincula el chat de Telegram
Un `PATCH /api/users/me` que cambia el `phone` a otro valor, o lo borra, SHALL desvincular el chat de
Telegram del usuario, porque el chat se verificó con el teléfono anterior. Un parche que no cambia el
`phone`, o lo manda con el mismo valor, MUST NOT tocar la vinculación.

#### Scenario: Cambio de celular con el chat vinculado
- **WHEN** un usuario con `telegramLinked` `true` cambia su `phone` por otro, también admitido
- **THEN** la respuesta es 200 con `telegramLinked` `false`
- **AND** el siguiente mensaje desde su chat recibe el texto de chat sin vincular

#### Scenario: Parche sin cambiar el celular
- **WHEN** un usuario con `telegramLinked` `true` cambia solo su nombre, o manda su mismo `phone`
- **THEN** la respuesta es 200 con `telegramLinked` `true`
