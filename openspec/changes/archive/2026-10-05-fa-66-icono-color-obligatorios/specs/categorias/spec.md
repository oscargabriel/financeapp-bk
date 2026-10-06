# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
