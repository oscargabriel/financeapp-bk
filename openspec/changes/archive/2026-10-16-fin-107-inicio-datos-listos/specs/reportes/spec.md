# Spec Delta

## ADDED Requirements

### Requirement: Resumen de Inicio
`GET /api/reports/home` SHALL devolver en una sola respuesta lo que muestra la pantalla de Inicio del
usuario del token: `currencyCode` (su moneda base), `month` (el mes en curso, `YYYY-MM`), la serie
`months`, `spent`, `spentChangePercent`, `latestExpenses`, `topExpenses`, `debitBalance` y
`accounts`. No recibe parámetros: uno desconocido SHALL ignorarse. Los montos y porcentajes SHALL
salir como números, sin formato.

#### Scenario: Usuario del escenario
- **WHEN** el usuario del escenario, con moneda COP, pide `GET /api/reports/home`
- **THEN** la respuesta es 200 con `currencyCode` COP, `month` igual al mes en curso y todos los campos presentes

#### Scenario: Usuario sin datos
- **WHEN** un usuario recién registrado, sin cuentas ni movimientos, pide `GET /api/reports/home`
- **THEN** la respuesta es 200 con los 6 meses en cero, `spent` 0, `spentChangePercent` `null`, `latestExpenses` y `topExpenses` vacías, `debitBalance` 0 y `accounts` vacía

#### Scenario: Parámetro desconocido
- **WHEN** se pide `GET /api/reports/home?from=2026-01`
- **THEN** la respuesta es 200, igual que sin el parámetro

### Requirement: Mes en curso del resumen de Inicio
El mes en curso de `GET /api/reports/home` SHALL decidirse con la fecha de hoy en la zona horaria
del usuario, y el mes de cada movimiento con la misma zona, como en `GET /api/monthly-spending`.

#### Scenario: Fin de mes en la zona del usuario
- **WHEN** en UTC ya es el día 1 del mes siguiente y en la zona del usuario (America/Bogota) todavía es el último día del mes
- **THEN** `month` es el mes que termina en la zona del usuario

### Requirement: Serie de seis meses del resumen de Inicio
`months` SHALL traer exactamente los 6 meses que terminan en el mes en curso, del más antiguo al
más reciente, cada uno con `periodMonth`, `totalIncome` y `totalSpent` con el significado de
`GET /api/monthly-spending`. Un mes sin movimientos SHALL venir con los dos totales en 0.

#### Scenario: Meses del escenario
- **WHEN** el usuario del escenario pide el resumen
- **THEN** `months` trae 6 meses; los tres últimos traen `totalSpent` 1445000, 1820000 y 1905500 y `totalIncome` 4500000, 4500000 y 5300000, y los tres primeros vienen en 0

#### Scenario: Mes con solo meta
- **WHEN** el usuario tiene en un mes de la ventana una meta y ningún movimiento
- **THEN** ese mes viene con `totalIncome` 0 y `totalSpent` 0

### Requirement: Gastado del mes y su variación en el resumen de Inicio
`spent` SHALL ser el `totalSpent` del mes en curso. `spentChangePercent` SHALL ser
`(spent − gasto del mes anterior) / gasto del mes anterior × 100`, redondeado a 2 decimales
(mitad hacia arriba), con signo. Si el gasto del mes anterior es 0, `spentChangePercent` SHALL ser
`null`.

#### Scenario: Variación del escenario
- **WHEN** el usuario del escenario tiene gastos por 1905500 en el mes en curso y por 1820000 en el anterior
- **THEN** `spent` es 1905500 y `spentChangePercent` es 4.70

#### Scenario: Gasto menor que el del mes anterior
- **WHEN** el usuario gastó 500000 en el mes en curso y 1000000 en el anterior
- **THEN** `spentChangePercent` es -50.00

#### Scenario: Mes anterior sin gastos
- **WHEN** el usuario tiene gastos en el mes en curso y ninguno en el anterior
- **THEN** `spentChangePercent` es `null`

### Requirement: Últimos gastos del resumen de Inicio
`latestExpenses` SHALL traer los 5 gastos (`EXPENSE`) confirmados y ya ocurridos más recientes del
usuario, de cualquier fecha, del más reciente al más antiguo. Cada uno SHALL traer `id`,
`description`, `categoryName` (`null` sin categoría), `occurredAt`, `amount` y `currencyCode` en la
moneda de su cuenta, y `amountBase` en la moneda base. Con menos de 5, SHALL traer los que haya.

#### Scenario: Escenario con más de cinco gastos
- **WHEN** el usuario del escenario pide el resumen
- **THEN** `latestExpenses` trae 5 gastos ordenados por `occurredAt` descendente, ninguno posterior al momento actual

#### Scenario: Pendientes y programados fuera
- **WHEN** `saldo@financeapp.local`, con un gasto confirmado de 200000 de hace una hora, uno de 10 USD de hace 40 días, uno pendiente de 50000 y uno programado de 70000 para el mes siguiente, pide el resumen
- **THEN** `latestExpenses` trae solo los dos confirmados: el de 200000 y el de 10 USD con `amountBase` 39000

### Requirement: Gastos más costosos del mes en el resumen de Inicio
`topExpenses` SHALL traer hasta 5 gastos confirmados y ya ocurridos del mes en curso, de mayor a
menor `amountBase`, con los campos de `latestExpenses` y `shareOfTop`: su `amountBase` sobre el del
primero × 100, a 2 decimales (mitad hacia arriba). El primero SHALL traer 100. Un empate SHALL
ordenarse por `occurredAt` descendente.

#### Scenario: Más costosos del escenario
- **WHEN** el usuario del escenario pide el resumen
- **THEN** `topExpenses` trae 5 gastos del mes en curso ordenados por `amountBase` descendente, el primero con `shareOfTop` 100 y los demás con su proporción

#### Scenario: Se ordena por la moneda base
- **WHEN** la persona, con moneda COP, tiene en el mes un gasto de 50 USD (`amountBase` 205000) y uno de 150000 COP
- **THEN** el de 50 USD va primero y el de 150000 trae `shareOfTop` 73.17

#### Scenario: Mes sin gastos
- **WHEN** el usuario no tiene gastos ya ocurridos en el mes en curso
- **THEN** `topExpenses` es una lista vacía

### Requirement: Cuentas y cupo usado en el resumen de Inicio
`debitBalance` SHALL ser el de `GET /api/reports/balance`, y `accounts` SHALL traer sus mismas
cuentas activas con sus mismos campos, más `creditUsedPercent`. En una tarjeta (`CREDIT`) con
`creditLimit` mayor que 0, SHALL ser `−currentBalance / creditLimit × 100`, a 2 decimales (mitad
hacia arriba) y acotado entre 0 y 100. Sin cupo, o en otro tipo de cuenta, SHALL ser `null`.

#### Scenario: Tarjeta con deuda
- **WHEN** una tarjeta tiene `creditLimit` 5000000 y `currentBalance` -658000
- **THEN** su `creditUsedPercent` es 13.16

#### Scenario: Tarjeta con saldo a favor
- **WHEN** una tarjeta tiene `creditLimit` 5000000 y `currentBalance` 20000
- **THEN** su `creditUsedPercent` es 0

#### Scenario: Tarjeta pasada del cupo
- **WHEN** una tarjeta tiene `creditLimit` 1000000 y `currentBalance` -1200000
- **THEN** su `creditUsedPercent` es 100

#### Scenario: Tarjeta sin cupo y cuenta débito
- **WHEN** el usuario tiene una tarjeta sin `creditLimit` y una cuenta débito
- **THEN** las dos traen `creditUsedPercent` `null`

#### Scenario: Saldo débito igual al de la consulta de saldo
- **WHEN** `saldo@financeapp.local` pide el resumen
- **THEN** `debitBalance` es 1551000 y `accounts` trae las mismas cuentas que `GET /api/reports/balance`

### Requirement: Pendientes y programados en el resumen de Inicio
Los movimientos pendientes y los programados SHALL quedar fuera de todo el resumen de Inicio
(`months`, `spent`, `spentChangePercent`, `latestExpenses`, `topExpenses`, `debitBalance` y el
`currentBalance` de las cuentas), con las mismas reglas que los demás reportes. Las transferencias
MUST NOT contar como gasto ni como ingreso.

#### Scenario: Programado del mes en curso
- **WHEN** el usuario tiene un gasto programado para más adelante en el mes en curso
- **THEN** no está en `spent`, ni en `latestExpenses`, ni en `topExpenses`

#### Scenario: Transferencias del escenario
- **WHEN** el mes en curso del escenario tiene dos transferencias que entran a la cuenta débito
- **THEN** el mes en curso de `months` sigue con `totalIncome` 5300000 y ninguna transferencia está en `latestExpenses` ni en `topExpenses`

### Requirement: Aislamiento por usuario en el resumen de Inicio
`GET /api/reports/home` SHALL devolver solo datos del usuario del token.

#### Scenario: Otro usuario con gastos
- **WHEN** otro usuario tiene gastos en el mes en curso y el usuario del escenario pide su resumen
- **THEN** ninguno de esos gastos está en `latestExpenses` ni en `topExpenses`, y los totales no cambian

### Requirement: Resumen de Inicio autenticado con JWT
`GET /api/reports/home` SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial Basic
compartida de `/auth/*` y `/status`, SHALL responder 401.

#### Scenario: Sin credencial
- **WHEN** se pide el resumen sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se pide el resumen con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
