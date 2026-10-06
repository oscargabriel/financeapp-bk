# Spec Delta

## Purpose

Las categorías propias de cada usuario: las que le copió el registro y las que crea él mismo para
clasificar sus gastos e ingresos.

## ADDED Requirements

### Requirement: Alta de una categoría
`POST /api/categories` SHALL crear una categoría del usuario del token con `name` y `appliesTo`
obligatorios e `icon` y `color` opcionales, y responder 201 con `{id, name, appliesTo, icon, color,
isSystem}`, el mismo contrato de `GET /api/categories`. El `id` SHALL ser un UUID v7 y `isSystem`
SHALL ser `false`.

#### Scenario: Alta completa
- **WHEN** el usuario envía `{"name": "  Plantas  ", "appliesTo": "expense", "icon": "sprout", "color": "#7cb342"}`
- **THEN** la respuesta es 201 con un `id` UUID v7, `name` `Plantas`, `appliesTo` `EXPENSE`, `icon` `sprout`, `color` `#7CB342` e `isSystem` `false`

#### Scenario: Sin icono ni color
- **WHEN** el usuario envía `{"name": "Bonos", "appliesTo": "INCOME"}`, o los mismos campos con `"icon": " "` y `"color": ""`
- **THEN** la respuesta es 201 con `icon` y `color` en `null`

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

#### Scenario: Icono demasiado largo
- **WHEN** `icon` tiene 41 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `icon`

#### Scenario: Color mal formado
- **WHEN** el cuerpo trae `"color": "rojo"` o `"color": "#12345"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `color`

#### Scenario: Varios errores a la vez
- **WHEN** el cuerpo es `{"appliesTo": "GASTO", "color": "rojo"}`
- **THEN** la respuesta es 400 con un error en `name`, otro en `appliesTo` y otro en `color`

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
