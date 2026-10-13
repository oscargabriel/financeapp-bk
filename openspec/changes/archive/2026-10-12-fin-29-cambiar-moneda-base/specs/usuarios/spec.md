# Spec Delta

## MODIFIED Requirements

### Requirement: Modificación parcial del perfil
`PATCH /api/users/me` SHALL aceptar cualquier subconjunto de `firstName`, `lastName`, `email`, `phone`,
`timezone` y `baseCurrencyCode`, con las reglas de formato del alta. Un campo ausente o en `null`
SHALL conservar su valor. `lastName` y `phone` en blanco SHALL borrar el valor. La respuesta SHALL ser
200 con el perfil tal como quedó.

#### Scenario: Cambiar nombre, celular y zona
- **WHEN** el parche trae `{"firstName": " Ana María ", "phone": "3109876543", "timezone": "Europe/Madrid"}`
- **THEN** la respuesta es 200 con `firstName` `Ana María`, `phone` `3109876543` y `timezone` `Europe/Madrid`, y el resto del perfil sin cambios

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

#### Scenario: Moneda en blanco o mal formada
- **WHEN** el parche trae `"baseCurrencyCode": "  "`, `"baseCurrencyCode": "US"` o `"baseCurrencyCode": "U5D"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`

## ADDED Requirements

### Requirement: Cambio de la moneda base desde el perfil
`baseCurrencyCode` en el parche SHALL cambiar la moneda en que la persona ve sus totales, si es el
código de una moneda activa del catálogo, sin distinguir mayúsculas y tolerando espacios en el borde.
La moneda SHALL guardarse en mayúsculas y el 200 SHALL traerla. Una moneda inexistente o inactiva
SHALL responder 400 `VALIDATION_ERROR` en `baseCurrencyCode`. Mandar la moneda actual SHALL responder
200 sin cambiarla y sin buscar tasas.

#### Scenario: Cambio de moneda válido
- **WHEN** un usuario con moneda base `COP` manda `{"baseCurrencyCode": " usd ", "firstName": "Ana"}`
- **THEN** la respuesta es 200 con `baseCurrencyCode` `USD` y `firstName` `Ana`, y `GET /api/users/me` trae `USD`

#### Scenario: Solo la moneda
- **WHEN** un usuario con moneda base `COP` manda solo `{"baseCurrencyCode": "EUR"}`
- **THEN** la respuesta es 200 con `baseCurrencyCode` `EUR`

#### Scenario: Moneda inactiva
- **WHEN** el parche trae `{"firstName": "Otro", "baseCurrencyCode": "XTS"}` con `XTS` inactiva en el catálogo
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`, y ni el nombre ni la moneda cambian

#### Scenario: Moneda inexistente
- **WHEN** el parche trae `{"baseCurrencyCode": "XYZ"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`

#### Scenario: La misma moneda en otra caja
- **WHEN** un usuario con moneda base `EUR` manda `{"baseCurrencyCode": "eur"}`
- **THEN** la respuesta es 200 con `baseCurrencyCode` `EUR`, sin consultar el catálogo, las tasas guardadas ni el proveedor

### Requirement: La moneda nueva necesita una tasa
Antes de guardar una moneda base distinta de la actual y de `USD`, el sistema SHALL resolver la tasa
`USD→<moneda nueva>` de hoy con las reglas de `tasas-de-cambio`: consulta al proveedor si le falta la
fila de hoy y el límite de consultas lo permite. Si no queda ninguna tasa guardada de esa moneda,
SHALL responder 502 `EXTERNAL_SERVICE_ERROR` en `server`, sin detalle del proveedor, y MUST NOT
aplicar nada del parche.

#### Scenario: Moneda con tasa de hoy guardada
- **WHEN** existe `USD→EUR` de hoy y un usuario con moneda `COP` cambia a `EUR`
- **THEN** la respuesta es 200 con `baseCurrencyCode` `EUR` y el proveedor no se consulta

#### Scenario: Moneda sin la tasa de hoy
- **WHEN** no existe `USD→EUR` de hoy, el proveedor responde EUR 0.8 y un usuario cambia a `EUR`
- **THEN** el proveedor se consulta, queda guardada `USD→EUR` de hoy y la respuesta es 200 con `EUR`

#### Scenario: Moneda sin ninguna tasa y proveedor caído
- **WHEN** no existe ninguna fila `USD→MXN`, el proveedor falla, y el parche trae `{"firstName": "Otro", "baseCurrencyCode": "MXN"}`
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server`, la descripción no menciona al proveedor, y ni el nombre ni la moneda cambian

#### Scenario: Cambio a USD
- **WHEN** un usuario con moneda `COP` cambia a `USD`
- **THEN** la respuesta es 200 con `USD` sin buscar tasas: `USD→USD` vale 1

### Requirement: Orden de las validaciones del parche del perfil
`PATCH /api/users/me` SHALL resolver los errores en este orden, y responder solo los del primer paso
que falle: formato de todos los campos, parche vacío, moneda base activa, contraseña actual y unicidad
del correo, tasa de la moneda nueva. La tasa MUST NOT buscarse si un paso anterior falla.

#### Scenario: Moneda inactiva y contraseña equivocada
- **WHEN** el parche trae un `email` nuevo, un `currentPassword` equivocado y `"baseCurrencyCode": "XTS"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `baseCurrencyCode`, y ningún `INVALID_CREDENTIALS`

#### Scenario: Contraseña equivocada con una moneda válida
- **WHEN** el parche trae un `email` nuevo, un `currentPassword` equivocado y `"baseCurrencyCode": "MXN"`
- **THEN** la respuesta es 400 con un error `INVALID_CREDENTIALS` en el campo `currentPassword`, y no se busca la tasa de `MXN`

#### Scenario: Formato inválido con una moneda inactiva
- **WHEN** el parche trae `"firstName": "   "` y `"baseCurrencyCode": "XTS"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` solo en el campo `firstName`

## REMOVED Requirements

### Requirement: La moneda base no cambia desde el perfil
**Reason**: FIN-29 habilita el cambio de moneda base desde el perfil; omitir el campo era provisional
desde FA-89.
**Migration**: `baseCurrencyCode` en `PATCH /api/users/me` cambia la moneda (requisito «Cambio de la
moneda base desde el perfil»). Un cliente que lo mandaba esperando que se ignorara pasa a cambiarla.
