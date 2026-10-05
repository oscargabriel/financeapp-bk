# Spec Delta

## Purpose

Reportes sobre los movimientos del usuario autenticado. Hoy, el reporte de movimientos por rango de
días con `GET /api/reports/transactions`: la lista y sus totales por tipo y por categoría.

## ADDED Requirements

### Requirement: Reporte de movimientos por rango de días
`GET /api/reports/transactions` SHALL devolver los movimientos del usuario del token cuya fecha, en
la zona horaria del usuario, cae entre `from` y `to`, ambos inclusive. La lista SHALL ir ordenada por
`occurredAt` descendente, y SHALL traer además la moneda base del usuario y los totales.

#### Scenario: Mes completo sin filtros
- **WHEN** el usuario pide `from` el primer día y `to` el último día del mes en curso, sin filtros
- **THEN** la respuesta es 200 con todos sus gastos, ingresos y transferencias del mes, del más reciente al más antiguo, y `currencyCode` igual a su moneda base

#### Scenario: Un solo día
- **WHEN** el usuario pide `from` y `to` iguales
- **THEN** la respuesta trae solo los movimientos de ese día

#### Scenario: Rango sin movimientos
- **WHEN** el usuario pide un rango en el que no tiene movimientos
- **THEN** la respuesta es 200 con `transactions` vacía, `totalsByType` con los tres tipos en cero y `totalsByCategory` vacía

### Requirement: Día cortado en la zona horaria del usuario
El sistema SHALL decidir a qué día pertenece un movimiento con la zona horaria del usuario, no con
UTC.

#### Scenario: Movimiento de la noche que en UTC ya es del día siguiente
- **WHEN** el usuario de `America/Bogota` tiene un gasto el último día del mes a las 21:30 locales (02:30 UTC del día siguiente) y pide `from` y `to` iguales a ese último día
- **THEN** el gasto aparece en el reporte

#### Scenario: El día siguiente no lo incluye
- **WHEN** el mismo usuario pide `from` y `to` iguales al primer día del mes siguiente
- **THEN** ese gasto no aparece

### Requirement: Cada movimiento del reporte
Cada elemento de `transactions` SHALL traer `id`, `type`, `accountId`, `destinationAccountId`,
`categoryId`, `categoryName`, `amount`, `currencyCode`, `amountBase`, `description`, `notes` y
`occurredAt` en UTC. `categoryId` y `categoryName` SHALL ir en null en las transferencias, y
`destinationAccountId` en null en gastos e ingresos.

#### Scenario: Gasto y transferencia en el mismo reporte
- **WHEN** el reporte incluye un gasto de Mercado y una transferencia
- **THEN** el gasto trae `categoryName` "Mercado" y `destinationAccountId` null, y la transferencia trae `categoryId` y `categoryName` null y su `destinationAccountId`

### Requirement: Totales del reporte
`totalsByType` SHALL traer una entrada por cada tipo consultado —los tres sin filtro de tipo, en el
orden `EXPENSE`, `INCOME`, `TRANSFER`—, con `total` (suma de `amountBase`) y `count`, en cero si no
hubo movimientos. `totalsByCategory` SHALL traer una entrada por categoría con movimientos, con
`categoryId`, `categoryName`, `total` y `count`, ordenada por `total` descendente.

#### Scenario: Totales del mes
- **WHEN** el reporte del mes trae gastos, ingresos y transferencias
- **THEN** cada `total` de `totalsByType` es la suma de `amountBase` de los movimientos de ese tipo en la lista, y `count` su cantidad

#### Scenario: Las transferencias no entran en los totales por categoría
- **WHEN** el reporte incluye transferencias
- **THEN** `totalsByCategory` no tiene ninguna entrada por ellas, y la suma de sus `total` es la de gastos más ingresos

#### Scenario: Totales con filtro de tipo
- **WHEN** el usuario filtra por `type=EXPENSE`
- **THEN** `totalsByType` trae una sola entrada, la de `EXPENSE`

### Requirement: Filtro opcional por categorías
El parámetro `categoryId` SHALL aceptar uno o varios ids, repitiendo el parámetro o separados por
coma, y SHALL limitar el reporte a los movimientos de esas categorías. Con este filtro las
transferencias SHALL quedar fuera, porque no tienen categoría.

#### Scenario: Una categoría
- **WHEN** el usuario pide el mes con `categoryId` de Mercado
- **THEN** la respuesta trae solo sus movimientos de Mercado

#### Scenario: Varias categorías
- **WHEN** el usuario pide `categoryId` de Mercado y de Restaurantes
- **THEN** la respuesta trae los movimientos de las dos y `totalsByCategory` tiene exactamente esas dos entradas

#### Scenario: Categoría de otro usuario
- **WHEN** el usuario pide un `categoryId` bien formado que no es suyo
- **THEN** la respuesta es 200 con la lista vacía: nunca devuelve movimientos ajenos ni revela si la categoría existe

### Requirement: Filtro opcional por tipo
El parámetro `type` SHALL aceptar uno o varios de `EXPENSE`, `INCOME` y `TRANSFER`, sin distinguir
mayúsculas, repitiendo el parámetro o separados por coma, y SHALL limitar el reporte a esos tipos.

#### Scenario: Un tipo
- **WHEN** el usuario pide el mes con `type=INCOME`
- **THEN** la respuesta trae solo sus ingresos del mes

#### Scenario: Categoría y tipo combinados
- **WHEN** el usuario pide `categoryId` de Mercado y de Salario con `type=EXPENSE`
- **THEN** la respuesta trae solo los gastos de Mercado

### Requirement: Validación de los parámetros
`from` y `to` SHALL ser obligatorios con formato `YYYY-MM-DD`. Un parámetro inválido SHALL
responder 400 con un error `VALIDATION_ERROR` en el campo del parámetro, sin consultar movimientos.

#### Scenario: Falta from o to
- **WHEN** el usuario pide el reporte sin `from`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: Formato inválido
- **WHEN** `to` vale `2026-10-1`, `2026/10/01` o `01-10-2026`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `to`

#### Scenario: Fecha inexistente
- **WHEN** `from` vale `2026-02-30`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: from posterior a to
- **WHEN** `from` es `2026-10-31` y `to` es `2026-10-01`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: categoryId mal formado
- **WHEN** `categoryId` vale `abc`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: type fuera del enum
- **WHEN** `type` vale `PAGO`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `type`

### Requirement: Aislamiento por usuario
El reporte SHALL contener únicamente movimientos del usuario del token, sin aceptar un `userId` por
la URL ni por el query.

#### Scenario: Dos usuarios con movimientos en el mismo rango
- **WHEN** otro usuario registra un movimiento en el mes y el usuario del escenario pide el reporte del mes
- **THEN** el movimiento del otro usuario no aparece, y el reporte del otro usuario trae solo el suyo

### Requirement: Reporte autenticado con JWT
`GET /api/reports/transactions` SHALL exigir un Bearer JWT válido. Sin credencial, o con la
credencial Basic compartida de `/auth/*` y `/status`, SHALL responder 401.

#### Scenario: Sin credencial
- **WHEN** se pide el reporte sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se pide el reporte con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
