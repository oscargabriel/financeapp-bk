# Spec Delta

## Purpose
Los valores válidos que el API expone bajo `/api/catalogs` para que un cliente arme sus formularios
sin copiar listas del contrato: tipos de cuenta, tipos de movimiento, monedas y categorías del
usuario.

## ADDED Requirements

### Requirement: Catálogo de tipos de cuenta
`GET /api/catalogs/account-types` SHALL devolver 200 con un elemento `{code, description}` por cada
tipo de cuenta que acepta el alta de cuentas, en el orden `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`,
`INVESTMENT`, `OTHER`. `description` SHALL ser la etiqueta en español.

#### Scenario: Lista completa con su etiqueta
- **WHEN** un usuario autenticado pide `GET /api/catalogs/account-types`
- **THEN** la respuesta es 200 con seis elementos, el primero `{"code": "CASH", "description": "Efectivo"}` y el de `CREDIT` con `"description": "Tarjeta de crédito"`

#### Scenario: Solo código y descripción
- **WHEN** un usuario autenticado pide `GET /api/catalogs/account-types`
- **THEN** cada elemento tiene exactamente las claves `code` y `description`

### Requirement: Catálogo de tipos de movimiento
`GET /api/catalogs/transaction-types` SHALL devolver 200 con un elemento `{code, description}` por
cada tipo de movimiento, en el orden `EXPENSE`, `INCOME`, `TRANSFER`, con la etiqueta en español.

#### Scenario: Lista completa con su etiqueta
- **WHEN** un usuario autenticado pide `GET /api/catalogs/transaction-types`
- **THEN** la respuesta es 200 con `[{"code": "EXPENSE", "description": "Gasto"}, {"code": "INCOME", "description": "Ingreso"}, {"code": "TRANSFER", "description": "Transferencia"}]`

### Requirement: Catálogo de monedas
`GET /api/catalogs/currencies` SHALL devolver 200 con las monedas activas del catálogo de monedas,
cada una como `{code, name, symbol}`, ordenadas por `code`. Una moneda inactiva MUST NOT aparecer.

#### Scenario: Monedas activas
- **WHEN** un usuario autenticado pide `GET /api/catalogs/currencies` con la semilla cargada
- **THEN** la respuesta es 200, incluye `{"code": "COP", "name": "Peso colombiano", "symbol": "$"}` y los códigos salen en orden alfabético

#### Scenario: Moneda inactiva
- **WHEN** existe la moneda `XTS` con `is_active` en falso y un usuario autenticado pide `GET /api/catalogs/currencies`
- **THEN** ningún elemento de la respuesta tiene `"code": "XTS"`

### Requirement: Catálogo de categorías del usuario
`GET /api/catalogs/categories` SHALL devolver 200 con las categorías vivas del usuario del token,
cada una como `{id, name}`, en el mismo orden y con el mismo filtro opcional `appliesTo` que
`GET /api/categories`. Las categorías de otro usuario y las borradas MUST NOT aparecer.

#### Scenario: Sin filtro
- **WHEN** el usuario del escenario de pruebas pide `GET /api/catalogs/categories`
- **THEN** la respuesta es 200 con los mismos `id`, en el mismo orden, que `GET /api/categories` del mismo usuario, cada elemento con exactamente `id` y `name`, y sin la categoría borrada

#### Scenario: Con filtro
- **WHEN** el usuario pide `GET /api/catalogs/categories?appliesTo=EXPENSE`
- **THEN** la respuesta trae las categorías de gasto y las de ambos tipos, y ninguna de solo ingreso

#### Scenario: Filtro inválido
- **WHEN** el usuario pide `GET /api/catalogs/categories?appliesTo=GASTO`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `appliesTo`

### Requirement: Catálogos autenticados con JWT
Las cuatro rutas de `/api/catalogs` SHALL exigir un Bearer válido. Sin credencial o con la
credencial Basic compartida, SHALL responder 401 con un error `UNAUTHENTICATED` en el campo
`authorization` y la cabecera `WWW-Authenticate: Bearer`.

#### Scenario: Sin credencial
- **WHEN** se pide cualquiera de las cuatro rutas de `/api/catalogs` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se pide `GET /api/catalogs/currencies` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED`

#### Scenario: Fuera del base-path
- **WHEN** se pide `GET /catalogs/account-types` sin el prefijo `/api` con un Bearer válido
- **THEN** la respuesta es 404
