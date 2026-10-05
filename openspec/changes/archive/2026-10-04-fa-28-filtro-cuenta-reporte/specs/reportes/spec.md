# Spec Delta

## ADDED Requirements

### Requirement: Filtro opcional por cuentas
El parámetro `accountId` SHALL aceptar uno o varios ids, repitiendo el parámetro o separados por coma,
y SHALL limitar el reporte a los movimientos en los que alguna de esas cuentas es el origen o el
destino. Cada movimiento SHALL aparecer una sola vez, y los totales SHALL calcularse sobre la lista
filtrada con las mismas reglas de siempre. Se combina con `categoryId` y `type` con Y.

#### Scenario: Una cuenta con gastos y una transferencia que entra
- **WHEN** el usuario pide el mes con el `accountId` de su cuenta Efectivo, que tiene cuatro gastos y recibe el retiro de cajero desde Bancolombia
- **THEN** la respuesta es 200 con esos cinco movimientos, la transferencia con `destinationAccountId` igual a Efectivo, y `totalsByType` con `EXPENSE` en 4 movimientos y `TRANSFER` en 1

#### Scenario: Una cuenta que solo origina una transferencia
- **WHEN** el usuario pide el mes con el `accountId` de su cuenta Ahorros USD, cuyo único movimiento del mes es el cambio de dólares hacia Bancolombia
- **THEN** la respuesta trae solo esa transferencia, con `accountId` igual a Ahorros USD

#### Scenario: Varias cuentas con una transferencia entre ellas
- **WHEN** el usuario pide `accountId` de Efectivo y de Bancolombia
- **THEN** la respuesta trae los movimientos de las dos cuentas, y el retiro de cajero entre ellas aparece una sola vez

#### Scenario: Cuenta y tipo combinados
- **WHEN** el usuario pide el `accountId` de Bancolombia con `type=EXPENSE,TRANSFER`
- **THEN** la respuesta trae los gastos de Bancolombia y las dos transferencias del mes, la que sale de Bancolombia y la que entra, y `totalsByType` trae solo `EXPENSE` y `TRANSFER`, sumados sobre esa lista

#### Scenario: Cuenta y categoría combinadas
- **WHEN** el usuario pide el `accountId` de Efectivo con el `categoryId` de Mercado
- **THEN** la respuesta trae solo los gastos de Mercado pagados desde Efectivo

#### Scenario: Cuenta de otro usuario
- **WHEN** el usuario pide un `accountId` bien formado que pertenece a otro usuario y tiene movimientos en el rango
- **THEN** la respuesta es 200 con la lista vacía: nunca devuelve movimientos ajenos ni revela si la cuenta existe

#### Scenario: Sin filtro de cuenta
- **WHEN** el usuario pide el reporte sin `accountId`
- **THEN** la respuesta es la misma que antes de existir el filtro

## MODIFIED Requirements

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

#### Scenario: accountId mal formado
- **WHEN** `accountId` vale `abc`, o uno de los valores separados por coma no es un UUID
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId`

#### Scenario: type fuera del enum
- **WHEN** `type` vale `PAGO`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `type`
