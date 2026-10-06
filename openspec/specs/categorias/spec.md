# categorias Specification

## Purpose
Las categorías propias de cada usuario: las que le copió el registro y las que crea él mismo para
clasificar sus gastos e ingresos.

## Requirements

### Requirement: Alta de una categoría
`POST /api/categories` SHALL crear una categoría del usuario del token con `name`, `appliesTo`,
`icon` y `color` obligatorios, y responder 201 con `{id, name, appliesTo, icon, color, isSystem}`,
el mismo contrato de `GET /api/categories`. El `id` SHALL ser un UUID v7 y `isSystem` SHALL ser
`false`.

#### Scenario: Alta completa
- **WHEN** el usuario envía `{"name": "  Plantas  ", "appliesTo": "expense", "icon": "sprout", "color": "#7cb342"}`
- **THEN** la respuesta es 201 con un `id` UUID v7, `name` `Plantas`, `appliesTo` `EXPENSE`, `icon` `sprout`, `color` `#7CB342` e `isSystem` `false`

#### Scenario: Icono con espacios en el borde
- **WHEN** el usuario envía `{"name": "Bonos", "appliesTo": "INCOME", "icon": " gift ", "color": "#ad1457"}`
- **THEN** la respuesta es 201 con `icon` `gift` y `color` `#AD1457`

#### Scenario: Sin icono ni color
- **WHEN** el usuario envía `{"name": "Sin adornos", "appliesTo": "INCOME"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `icon` y otro en `color`, y no se crea nada

#### Scenario: Aparece en el listado
- **WHEN** después del alta el mismo usuario pide `GET /api/categories`
- **THEN** la categoría nueva aparece con los mismos datos de la respuesta del alta

### Requirement: La categoría nueva va al final de la lista
La categoría creada SHALL quedar después de todas las categorías vivas que el usuario ya tenía en
el orden de `GET /api/categories`.

#### Scenario: Después de la semilla
- **WHEN** un usuario recién registrado crea una categoría y pide `GET /api/categories`
- **THEN** la categoría nueva es el último elemento de la lista

#### Scenario: Dos altas seguidas
- **WHEN** el usuario crea `Plantas` y luego `Bonos`
- **THEN** en `GET /api/categories` `Bonos` aparece después de `Plantas`, y las dos después de las que ya tenía

### Requirement: Formato del cuerpo del alta
Cada error de formato SHALL responder 400 con un error `VALIDATION_ERROR` sobre su campo, todos en
la misma respuesta, y MUST NOT crear la categoría.

#### Scenario: Nombre ausente o en blanco
- **WHEN** el cuerpo no trae `name`, o lo trae como `"   "`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `name`

#### Scenario: Nombre demasiado largo
- **WHEN** `name` tiene 61 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `name`

#### Scenario: appliesTo ausente
- **WHEN** el cuerpo no trae `appliesTo`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `appliesTo`

#### Scenario: appliesTo fuera de los tres valores
- **WHEN** el cuerpo trae `"appliesTo": "GASTO"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `appliesTo`

#### Scenario: Icono o color en blanco
- **WHEN** el cuerpo trae `"icon": " "` y `"color": ""`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en `icon` y otro en `color`

#### Scenario: Icono demasiado largo
- **WHEN** `icon` tiene 41 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `icon`

#### Scenario: Color mal formado
- **WHEN** el cuerpo trae `"color": "rojo"` o `"color": "#12345"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `color`

#### Scenario: Varios errores a la vez
- **WHEN** el cuerpo es `{"appliesTo": "GASTO", "color": "rojo"}`
- **THEN** la respuesta es 400 con un error en `name`, otro en `appliesTo`, otro en `icon` y otro en `color`

#### Scenario: Cuerpo que no es JSON
- **WHEN** el cuerpo es `{name:`
- **THEN** la respuesta es 400 con un error `JSON_PARSING_ERROR`

### Requirement: Nombre único entre las categorías vivas
El nombre SHALL ser único entre las categorías no borradas del usuario, sin distinguir mayúsculas
y después de recortar los espacios del borde. Un nombre repetido SHALL responder 409 con un error
`DUPLICATE_RESOURCE` en el campo `name`. Un nombre que solo usan categorías borradas del usuario,
o categorías de otro usuario, SHALL poder crearse.

#### Scenario: Repite una categoría propia
- **WHEN** el usuario ya tiene `Plantas` y envía `{"name": " PLANTAS ", "appliesTo": "BOTH"}`
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`, y no se crea nada

#### Scenario: Repite una categoría de la semilla
- **WHEN** el usuario envía `{"name": "mercado", "appliesTo": "EXPENSE"}` y tiene viva la `Mercado` de la semilla
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`

#### Scenario: El nombre lo usa una categoría borrada
- **WHEN** el usuario envía el nombre de una categoría suya que está borrada lógicamente
- **THEN** la respuesta es 201 y la categoría nueva aparece en `GET /api/categories`

#### Scenario: El nombre lo usa otro usuario
- **WHEN** un usuario recién registrado crea `Gimnasio`, que es una categoría propia del usuario del escenario de pruebas
- **THEN** la respuesta es 201

### Requirement: Alta autenticada con JWT
`POST /api/categories` SHALL exigir un Bearer válido. Sin credencial o con la credencial Basic
compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`, sin crear nada.

#### Scenario: Sin credencial
- **WHEN** se envía `POST /api/categories` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `POST /api/categories` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

### Requirement: Modificación parcial de una categoría
`PATCH /api/categories/{id}` SHALL aceptar cualquier subconjunto de `name`, `appliesTo`, `icon` y
`color`, normalizados como en el alta. Un campo ausente o en `null` SHALL conservar su valor. La
respuesta SHALL ser 200 con `{id, name, appliesTo, icon, color, isSystem}` tal como quedó.

#### Scenario: Cambiar nombre, icono y color
- **WHEN** el usuario envía `PATCH /api/categories/{id}` sobre su categoría `Plantas` con `{"name": " Huerta ", "icon": "leaf", "color": "#558b2f"}`
- **THEN** la respuesta es 200 con `name` `Huerta`, `icon` `leaf`, `color` `#558B2F` y el mismo `id` y `appliesTo`

#### Scenario: Campo en null
- **WHEN** el parche trae `{"color": "#33691E", "icon": null}` sobre una categoría con icono
- **THEN** la respuesta es 200, el color cambia y el icono sigue siendo el mismo

#### Scenario: Una categoría de la semilla
- **WHEN** el usuario modifica el color de su `Mercado`, copiada de la semilla
- **THEN** la respuesta es 200 con el color nuevo e `isSystem` en `true`

#### Scenario: El listado refleja el cambio
- **WHEN** después de modificar una categoría el usuario pide `GET /api/categories`
- **THEN** la categoría aparece con los datos de la respuesta del PATCH y en la misma posición que tenía

### Requirement: Formato del parche de una categoría
Cada campo enviado SHALL cumplir las reglas de formato del alta y MUST NOT ir en blanco: el parche
no vacía ningún campo. Un parche sin ningún campo modificable SHALL responder 400 con un error
`VALIDATION_ERROR` en el campo `body`. Ningún error de formato SHALL modificar la categoría.

#### Scenario: Parche vacío
- **WHEN** el cuerpo es `{}` o solo trae campos en `null`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `body`

#### Scenario: Icono o color en blanco
- **WHEN** el parche trae `{"icon": ""}` o `{"color": "  "}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en ese campo, y la categoría conserva el valor que tenía

#### Scenario: Nombre en blanco o demasiado largo
- **WHEN** el parche trae `"name": "   "` o un nombre de 61 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `name`

#### Scenario: appliesTo fuera de los tres valores
- **WHEN** el parche trae `{"appliesTo": "GASTO"}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `appliesTo`

#### Scenario: Color mal formado o icono demasiado largo
- **WHEN** el parche trae `"color": "rojo"` o un `icon` de 41 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en ese campo

#### Scenario: Varios errores a la vez
- **WHEN** el parche trae `{"name": " ", "appliesTo": "GASTO", "icon": "", "color": "rojo"}`
- **THEN** la respuesta es 400 con un error en `name`, otro en `appliesTo`, otro en `icon` y otro en `color`

### Requirement: Categoría a modificar
El `{id}` SHALL ser un UUID; si no lo es, la respuesta SHALL ser 400 con un error
`VALIDATION_ERROR` en el campo `id`. Una categoría que no existe, que está borrada o que es de otro
usuario SHALL responder 404 con un error `NOT_FOUND` en el campo `id`, sin distinguir entre los
tres casos y sin modificar nada.

#### Scenario: Id mal formado
- **WHEN** el usuario envía `PATCH /api/categories/abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `id`

#### Scenario: Categoría inexistente
- **WHEN** el `{id}` es un UUID que no corresponde a ninguna categoría
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Categoría borrada
- **WHEN** el usuario del escenario de pruebas modifica su categoría borrada `Cigarrillos`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`

#### Scenario: Categoría de otro usuario
- **WHEN** un usuario recién registrado modifica una categoría del usuario del escenario de pruebas
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `id`, y la categoría ajena no cambia

### Requirement: Nombre único al modificar
El nombre nuevo SHALL ser único entre las otras categorías no borradas del usuario, sin distinguir
mayúsculas. Un nombre repetido SHALL responder 409 con un error `DUPLICATE_RESOURCE` en el campo
`name` sin modificar nada. Cambiar solo las mayúsculas del nombre de la propia categoría SHALL
permitirse.

#### Scenario: Repite otra categoría viva
- **WHEN** el usuario tiene `Huerta` y `Bonos` y renombra `Huerta` a `BONOS`
- **THEN** la respuesta es 409 con un error `DUPLICATE_RESOURCE` en el campo `name`, y `Huerta` conserva su nombre

#### Scenario: Cambia solo las mayúsculas del propio nombre
- **WHEN** el usuario renombra `Huerta` a `HUERTA`
- **THEN** la respuesta es 200 con `name` `HUERTA`

### Requirement: Alcance compatible con los movimientos de la categoría
Cambiar `appliesTo` a `EXPENSE` cuando la categoría tiene movimientos de ingreso, o a `INCOME`
cuando tiene movimientos de gasto, SHALL responder 409 con un error `RESOURCE_IN_USE` en el campo
`appliesTo`, sin modificar ningún campo del parche. Cambiar a `BOTH` SHALL permitirse siempre.

#### Scenario: Gasto registrado y cambio a ingreso
- **WHEN** la categoría `Huerta` (`EXPENSE`) tiene un gasto y el parche trae `{"appliesTo": "INCOME", "color": "#000000"}`
- **THEN** la respuesta es 409 con un error `RESOURCE_IN_USE` en el campo `appliesTo`, y la categoría conserva su alcance y su color

#### Scenario: Ampliar a ambos tipos
- **WHEN** la misma categoría con un gasto recibe `{"appliesTo": "BOTH"}`
- **THEN** la respuesta es 200 con `appliesTo` `BOTH`

#### Scenario: Sin movimientos
- **WHEN** una categoría `EXPENSE` sin movimientos recibe `{"appliesTo": "INCOME"}`
- **THEN** la respuesta es 200 con `appliesTo` `INCOME`

### Requirement: Modificación autenticada con JWT
`PATCH /api/categories/{id}` SHALL exigir un Bearer válido. Sin credencial o con la credencial
Basic compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`, sin modificar nada.

#### Scenario: Sin credencial
- **WHEN** se envía `PATCH /api/categories/{id}` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `PATCH /api/categories/{id}` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

### Requirement: Toda categoría tiene icono y color
Toda categoría del usuario SHALL tener `icon` y `color`: `GET /api/categories` MUST NOT devolver
ninguno de los dos en `null`. Las categorías que existían sin ellos SHALL quedar con el icono
`ellipsis` y el color `#757575`.

#### Scenario: Listado del escenario
- **WHEN** el usuario del escenario de pruebas pide `GET /api/categories`
- **THEN** ninguna categoría trae `icon` ni `color` en `null`, incluida `Ajustes`

#### Scenario: Categoría vieja sin icono ni color
- **WHEN** se aplica el update del cambio sobre una base con una categoría sin icono ni color
- **THEN** la categoría queda con `icon` `ellipsis` y `color` `#757575`, y las que ya tenían valor no cambian
