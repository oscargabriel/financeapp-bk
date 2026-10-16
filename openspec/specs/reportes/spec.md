# reportes Specification

## Purpose
Reportes sobre los movimientos del usuario autenticado: el reporte de movimientos por rango de días
con `GET /api/reports/transactions` (la lista, sus totales por tipo y por categoría, y el neto), y la
consulta de saldo con `GET /api/reports/balance` (ingresos menos gastos de un rango y de siempre,
con las cuentas activas y el cupo disponible de las tarjetas).

## Requirements

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
- **WHEN** el usuario de `America/Bogota` tiene un gasto el último día de un mes ya cumplido a las 21:30 locales (02:30 UTC del día siguiente) y pide `from` y `to` iguales a ese último día
- **THEN** el gasto aparece en el reporte

#### Scenario: El día siguiente no lo incluye
- **WHEN** el mismo usuario pide `from` y `to` iguales al primer día del mes siguiente
- **THEN** ese gasto no aparece

### Requirement: Cada movimiento del reporte
Cada elemento de `transactions` SHALL traer `id`, `type`, `accountId`, `destinationAccountId`,
`categoryId`, `categoryName`, `amount`, `currencyCode`, `amountBase`, `destinationAmount`,
`description`, `notes`, `occurredAt` en UTC, `scheduled`, `recurrenceId`, `installment`, `sharedBy`,
`sharedFrom` y `shares`. `categoryId` y
`categoryName` SHALL ir en null en las transferencias, y `destinationAccountId` en null en gastos e
ingresos. `destinationAmount` SHALL ser lo que entró a la cuenta destino, en la moneda de esa cuenta, en
una `TRANSFER` entre cuentas de monedas distintas, y null en todo otro movimiento. `scheduled` SHALL
ser `true` si `occurredAt` es posterior al momento en que se atiende la petición. `recurrenceId` SHALL
ser el id de la serie de la que el movimiento es ocurrencia, o null si no pertenece a ninguna.
`installment` SHALL ser `{purchaseId, number, count}` si el movimiento es una cuota de una compra en
cuotas, o null si no lo es. `sharedBy`, `sharedFrom` y `shares` SHALL tener la forma y el sentido que
tienen en la respuesta de un movimiento (ver `compartidos`).

#### Scenario: Gasto y transferencia en el mismo reporte
- **WHEN** el reporte incluye un gasto de Mercado y una transferencia
- **THEN** el gasto trae `categoryName` "Mercado" y `destinationAccountId` null, y la transferencia trae `categoryId` y `categoryName` null y su `destinationAccountId`

#### Scenario: Transferencia entre monedas distintas
- **WHEN** el reporte incluye el cambio de dólares, en el que salen 100 USD de Ahorros USD y entran 410.000 COP a Bancolombia
- **THEN** esa transferencia trae `amount` 100, `currencyCode` USD y `destinationAmount` 410000

#### Scenario: Transferencia en la misma moneda
- **WHEN** el reporte incluye el retiro de cajero de Bancolombia a Efectivo, las dos en COP
- **THEN** esa transferencia trae `destinationAmount` null

#### Scenario: Gastos e ingresos sin monto de destino
- **WHEN** el reporte incluye gastos e ingresos
- **THEN** todos traen `destinationAmount` null

#### Scenario: Un movimiento programado en la lista
- **WHEN** el rango incluye un gasto con fecha posterior al momento actual
- **THEN** ese gasto aparece en `transactions` con `"scheduled": true`, y los demás con `"scheduled": false`

#### Scenario: Ocurrencias de una serie en la lista
- **WHEN** el rango incluye las ocurrencias de una serie y un gasto suelto
- **THEN** las ocurrencias traen el `recurrenceId` de su serie y el gasto suelto `"recurrenceId": null`

#### Scenario: Cuotas de una compra en la lista
- **WHEN** el rango incluye las cuotas 1 y 2 de una compra a 3 cuotas y un gasto suelto
- **THEN** las cuotas traen `installment` con el `purchaseId` de la compra, `number` 1 y 2 y `count` 3, y el gasto suelto `"installment": null`

#### Scenario: Un gasto compartido en la lista
- **WHEN** el rango incluye un gasto de A compartido con B, con la parte de B pendiente, y un gasto sin compartir
- **THEN** el gasto compartido trae `shares` con la parte de B y `"status": "PENDING"`, y el otro trae `"shares": null`, `"sharedBy": null` y `"sharedFrom": null`

#### Scenario: Una parte pagada en la lista de quien debe
- **WHEN** B aprobó su parte del gasto de A y pide un reporte que la incluye
- **THEN** su gasto trae `sharedBy` con el correo de A y `sharedFrom` con la descripción y el total del gasto de A

### Requirement: Totales del reporte
`totalsByType` SHALL traer una entrada por cada tipo consultado —los tres sin filtro de tipo, en el
orden `EXPENSE`, `INCOME`, `TRANSFER`—, con `total` (suma de `amountBase`) y `count`, en cero si no
hubo movimientos. `totalsByCategory` SHALL traer una entrada por categoría con movimientos, con
`categoryId`, `categoryName`, `total` y `count`, ordenada por `total` descendente. Los dos SHALL
contar solo los movimientos de la lista con `scheduled` en `false`.

#### Scenario: Totales del mes
- **WHEN** el reporte del mes trae gastos, ingresos y transferencias
- **THEN** cada `total` de `totalsByType` es la suma de `amountBase` de los movimientos de ese tipo en la lista con `scheduled` en `false`, y `count` su cantidad

#### Scenario: Las transferencias no entran en los totales por categoría
- **WHEN** el reporte incluye transferencias
- **THEN** `totalsByCategory` no tiene ninguna entrada por ellas, y la suma de sus `total` es la de gastos más ingresos

#### Scenario: Totales con filtro de tipo
- **WHEN** el usuario filtra por `type=EXPENSE`
- **THEN** `totalsByType` trae una sola entrada, la de `EXPENSE`

#### Scenario: Los programados no suman
- **WHEN** el rango trae un gasto vigente de 30000 y un gasto programado de 40000 en la misma categoría
- **THEN** el total de `EXPENSE` es 30000 con `count` 1, y la categoría trae `total` 30000 y `count` 1

#### Scenario: Rango con solo programados
- **WHEN** el usuario pide un rango futuro en el que solo tiene un gasto programado
- **THEN** `transactions` trae ese gasto, `totalsByType` trae los tres tipos en cero y `totalsByCategory` va vacía

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

#### Scenario: accountId mal formado
- **WHEN** `accountId` vale `abc`, o uno de los valores separados por coma no es un UUID
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId`

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

### Requirement: Movimientos de una categoría borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una categoría borrada,
con su `categoryId` y el `categoryName` que tenía al borrarse, tanto en `transactions` como en
`totalsByCategory`.

#### Scenario: Gasto de una categoría borrada
- **WHEN** el usuario borra `Huerta`, que tiene un gasto de hoy, y pide el reporte de hoy
- **THEN** el gasto aparece con el `categoryId` y el `categoryName` que tenía `Huerta` al borrarse, y `totalsByCategory` trae una entrada de esa categoría con ese gasto

### Requirement: Movimientos de una cuenta borrada en el reporte
`GET /api/reports/transactions` SHALL seguir incluyendo los movimientos de una cuenta borrada, con
su `accountId`, y el filtro `accountId` con el id de esa cuenta SHALL seguir encontrándolos.

#### Scenario: Gasto de una cuenta borrada
- **WHEN** el usuario borra `Caja chica`, que tiene un gasto de hoy, y pide el reporte de hoy filtrado por esa cuenta
- **THEN** el gasto aparece con el `accountId` de la cuenta borrada y cuenta en los totales

### Requirement: Neto del reporte de movimientos
`GET /api/reports/transactions` SHALL traer un campo `net` igual al total de `INCOME` menos el total
de `EXPENSE` de `totalsByType`, sobre la misma lista que devuelve. Las transferencias no entran. Un
tipo que el filtro deja fuera SHALL contar como cero. El neto SHALL devolverse aunque sea negativo.
Como sale de `totalsByType`, los programados no entran.

#### Scenario: Neto del mes
- **WHEN** el usuario del escenario pide el reporte del mes en curso sin filtros, con ingresos por 5.300.000 y gastos por 1.905.500
- **THEN** la respuesta trae `net` 3394500, y los demás campos del reporte no cambian

#### Scenario: Neto negativo con filtro de gastos
- **WHEN** el usuario pide el mismo mes con `type=EXPENSE`
- **THEN** la respuesta trae `net` -1905500

#### Scenario: Neto con solo transferencias
- **WHEN** el usuario pide el mismo mes con `type=TRANSFER`
- **THEN** la respuesta trae `net` 0

#### Scenario: Rango sin movimientos
- **WHEN** el usuario pide un rango en el que no tiene movimientos
- **THEN** la respuesta trae `net` 0

#### Scenario: Rango con solo programados
- **WHEN** el usuario pide un rango futuro en el que solo tiene un gasto programado
- **THEN** la respuesta trae `net` 0

### Requirement: Consulta de saldo
`GET /api/reports/balance` SHALL devolver, en la moneda base del usuario (`currencyCode`), un
`period` con `from`, `to`, `income`, `expense` y `net` del rango pedido, y un `allTime` con
`income`, `expense` y `net` de todos sus movimientos. `income` y `expense` SHALL sumar `amountBase`
de ingresos y gastos; `net` SHALL ser su resta, aunque dé negativa. Las transferencias no entran.

#### Scenario: Mes en curso e histórico
- **WHEN** el usuario del escenario pide `from` y `to` del primer al último día del mes en curso
- **THEN** la respuesta es 200 con `currencyCode` COP, `period` con `income` 5300000, `expense` 1905500 y `net` 3394500, y `allTime` con `income` 14300000, `expense` 5170500 y `net` 9129500

#### Scenario: Mes anterior
- **WHEN** el usuario pide el primer y el último día del mes anterior
- **THEN** `period` trae `income` 4500000, `expense` 1820000 y `net` 2680000, y `allTime` es el mismo que con el mes en curso

#### Scenario: Neto negativo
- **WHEN** el usuario pide del segundo al último día del mes anterior, donde solo tiene gastos por 620.000
- **THEN** `period` trae `income` 0, `expense` 620000 y `net` -620000

#### Scenario: Los gastos con tarjeta de crédito cuentan como egresos
- **WHEN** el rango incluye gastos pagados con la tarjeta Visa
- **THEN** esos gastos están sumados en `expense` igual que los de cualquier otra cuenta

#### Scenario: Las transferencias no cuentan
- **WHEN** el rango incluye el retiro de cajero y el cambio de dólares
- **THEN** ni `income` ni `expense` los incluyen

### Requirement: Día cortado en la zona horaria del usuario en la consulta de saldo
`GET /api/reports/balance` SHALL asignar cada movimiento a un día con la zona horaria del usuario,
con el mismo corte que el reporte de movimientos.

#### Scenario: Gasto de la última noche del mes
- **WHEN** el usuario de `America/Bogota` tiene un gasto de 35.000 el último día de un mes ya cumplido a las 21:30 locales y pide `from` y `to` iguales a ese día
- **THEN** `period` trae `expense` 35000

### Requirement: Rango por defecto de la consulta de saldo
Sin `from` ni `to`, `GET /api/reports/balance` SHALL tomar el mes en curso en la zona horaria de la
aplicación, y `period.from` y `period.to` SHALL traer el primer y el último día de ese mes.

#### Scenario: Sin parámetros
- **WHEN** el usuario del escenario pide la consulta de saldo sin parámetros
- **THEN** la respuesta es la misma que pidiendo el primer y el último día del mes en curso, con esas dos fechas en `period.from` y `period.to`

### Requirement: Cuentas en la consulta de saldo
`GET /api/reports/balance` SHALL traer en `accounts` las cuentas activas y no borradas del usuario,
ordenadas por nombre, cada una con `id`, `name`, `type`, `currencyCode`, `currentBalance`,
`creditLimit` y `availableCredit`. El `currentBalance` de cada cuenta SHALL ir en la moneda de la
cuenta, sin convertir; el único total entre cuentas es `debitBalance`. En una cuenta que no es
`CREDIT`, o en una `CREDIT` sin cupo, `creditLimit` y `availableCredit` SHALL ser `null`.

#### Scenario: Tarjeta de crédito con cupo
- **WHEN** el usuario del escenario pide la consulta de saldo, y su tarjeta Visa tiene cupo 5.000.000 y saldo vigente -658.000
- **THEN** la cuenta Visa trae `currentBalance` -658000, `creditLimit` 5000000 y `availableCredit` 4342000

#### Scenario: Cuenta en otra moneda
- **WHEN** el usuario tiene la cuenta Ahorros USD
- **THEN** esa cuenta trae `currencyCode` USD y su `currentBalance` en dólares, con `creditLimit` y `availableCredit` en `null`

#### Scenario: Cuentas desactivadas y borradas
- **WHEN** el usuario tiene la cuenta desactivada Nequi y la borrada Davivienda
- **THEN** ninguna de las dos aparece en `accounts`

#### Scenario: Cuenta débito en otra moneda sin convertir
- **WHEN** `saldo@financeapp.local` pide la consulta de saldo
- **THEN** «Débito dólares» trae `currencyCode` USD y su `currentBalance` en dólares, aunque su aporte a `debitBalance` vaya en COP

### Requirement: Validación de los parámetros de la consulta de saldo
`from` y `to` SHALL venir los dos o ninguno, con formato `YYYY-MM-DD`. Un parámetro inválido o
faltante SHALL responder 400 con un error `VALIDATION_ERROR` en el campo del parámetro, sin
consultar movimientos.

#### Scenario: Solo from
- **WHEN** el usuario pide la consulta de saldo con `from` y sin `to`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `to`

#### Scenario: Solo to
- **WHEN** el usuario pide la consulta de saldo con `to` y sin `from`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: Formato inválido
- **WHEN** `from` vale `2026/10/01` o `2026-02-30`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: from posterior a to
- **WHEN** `from` es `2026-10-31` y `to` es `2026-10-01`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

### Requirement: Aislamiento por usuario en la consulta de saldo
`GET /api/reports/balance` SHALL calcular los netos y listar las cuentas únicamente con datos del
usuario del token, sin aceptar un `userId` por la URL ni por el query.

#### Scenario: Otro usuario con movimientos en el mismo mes
- **WHEN** otro usuario registra un gasto en el mes en curso y pide su consulta de saldo
- **THEN** su `period` y su `allTime` traen solo ese gasto, sus `accounts` solo las suyas, y la consulta del usuario del escenario no cambia

### Requirement: Consulta de saldo autenticada con JWT
`GET /api/reports/balance` SHALL exigir un Bearer JWT válido. Sin credencial, o con la credencial
Basic compartida de `/auth/*` y `/status`, SHALL responder 401.

#### Scenario: Sin credencial
- **WHEN** se pide la consulta de saldo sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic compartida
- **WHEN** se pide la consulta de saldo con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

### Requirement: Los pendientes no cuentan en los reportes
Un movimiento `PENDING` SHALL quedar fuera de `GET /api/reports/transactions` (su lista, totales y
neto), de `GET /api/reports/balance` (el neto del rango y el histórico) y de
`GET /api/monthly-spending` (el gasto y el ingreso del mes, y el avance contra la meta). Al
aprobarse, SHALL contar en los tres como cualquier movimiento confirmado.

#### Scenario: Reporte de movimientos con un pendiente
- **WHEN** el usuario tiene en el rango un gasto confirmado de 30000 y un gasto pendiente de 45000
- **THEN** `GET /api/reports/transactions` devuelve solo el confirmado, y el total de `EXPENSE` es 30000

#### Scenario: Consulta de saldo con un pendiente
- **WHEN** el usuario tiene en el mes un gasto confirmado de 30000 y un gasto pendiente de 45000
- **THEN** `GET /api/reports/balance` devuelve `period.expense` 30000 y `allTime.expense` sin los 45000

#### Scenario: Usuario con solo pendientes
- **WHEN** el usuario no tiene movimientos confirmados y sí tiene pendientes
- **THEN** `GET /api/reports/balance` responde 200 con `period` y `allTime` en cero

#### Scenario: Gasto mensual con un pendiente
- **WHEN** el usuario tiene en el mes un gasto pendiente
- **THEN** `GET /api/monthly-spending` no lo suma al gasto del mes

#### Scenario: Ingreso pendiente en el gasto mensual
- **WHEN** el usuario tiene en el mes un ingreso pendiente
- **THEN** `GET /api/monthly-spending` no lo suma a `totalIncome`

#### Scenario: El pendiente cuenta al aprobarse
- **WHEN** el usuario aprueba el gasto pendiente de 45000
- **THEN** `GET /api/reports/transactions` lo devuelve y `GET /api/reports/balance` suma 45000 a `period.expense`

### Requirement: Los programados no cuentan en los reportes
Un movimiento con `occurredAt` posterior al momento actual SHALL quedar fuera de los totales de
`GET /api/reports/transactions` (aunque aparezca en su lista), de `GET /api/reports/balance` (el
neto del rango, el histórico y el `currentBalance` de cada cuenta) y de `GET /api/monthly-spending`
(el gasto y el ingreso del mes, y el avance contra la meta). Cuando llega su fecha SHALL contar en
los tres sin que nadie lo modifique.

#### Scenario: Consulta de saldo con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/reports/balance` del mes siguiente trae `period.expense` 0, y `allTime.expense` no lo incluye

#### Scenario: Gasto mensual con un programado
- **WHEN** el usuario tiene un gasto programado de 40000 para el mes siguiente
- **THEN** `GET /api/monthly-spending` de ese mes no lo suma al gasto del mes

#### Scenario: Ingreso programado en el gasto mensual
- **WHEN** la persona tiene un ingreso programado para el mes siguiente y ningún otro movimiento ni meta ese mes
- **THEN** `GET /api/monthly-spending` de ese mes devuelve un arreglo vacío

#### Scenario: El programado cuenta al llegar su fecha
- **WHEN** el usuario registra un gasto de 40000 con `occurredAt` unos segundos en el futuro y pide `GET /api/reports/balance` después de esa fecha
- **THEN** `period.expense` y `allTime.expense` ya incluyen los 40000

### Requirement: Gasto mensual autenticado con JWT
`GET /api/monthly-spending` SHALL exigir un Bearer válido. Sin credencial o con la credencial Basic
compartida SHALL responder 401 con un error `UNAUTHENTICATED` en el campo `authorization` y la
cabecera `WWW-Authenticate: Bearer`.

#### Scenario: Sin credencial
- **WHEN** se envía `GET /api/monthly-spending` sin `Authorization`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con el Basic compartido
- **WHEN** se envía `GET /api/monthly-spending` con la credencial Basic de `/auth/*` y `/status`
- **THEN** la respuesta es 401 con `UNAUTHENTICATED` en `authorization` y `WWW-Authenticate: Bearer`

### Requirement: Montos en la moneda de la persona
Estos montos SHALL estar en la moneda base de la persona (`currencyCode`):
- `amountBase` de cada movimiento del reporte;
- los totales de `reports/transactions` y de `reports/balance`;
- `totalSpent` y `totalIncome` de `monthly-spending`.

Cada movimiento SHALL aportar su `amount` si su moneda es la de la persona. Si no, SHALL aportar su
equivalente en USD multiplicado por la tasa `USD→moneda de la persona` de su fecha local, elegida con
la regla de `tasas-de-cambio`, a 4 decimales. Cambiar la moneda de la persona MUST NOT requerir
reescribir movimientos.

#### Scenario: Persona en COP con movimientos en COP
- **WHEN** el usuario del escenario, con moneda COP, pide sus reportes
- **THEN** los totales son la suma de los `amount` de sus movimientos en COP, sin diferencias de redondeo

#### Scenario: Persona en USD con movimientos en COP
- **WHEN** una persona con moneda USD tiene en una cuenta COP un gasto de 41000 de hoy (tasa 4100) y uno de 39000 de hace 50 días (antes de la tasa más antigua, 3900), y pide `reports/transactions` de hace 50 días a hoy
- **THEN** la respuesta trae `currencyCode` USD, `amountBase` 10 en cada gasto y el total de gastos en 20

#### Scenario: Movimiento en otra moneda con la tasa de su fecha
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes una transferencia de 100 USD y la tasa `USD→COP` de esa fecha es 4100
- **THEN** su `amountBase` en el reporte es 410000

#### Scenario: Gasto mensual en la moneda de la persona
- **WHEN** la persona con moneda USD pide `monthly-spending` del mes en curso
- **THEN** `currencyCode` es USD y `totalSpent` incluye el gasto de hoy como 10

#### Scenario: Ingresos del gasto mensual en la moneda de la persona
- **WHEN** la persona con moneda USD tiene en su cuenta COP un ingreso de 82000 de hoy (tasa 4100) y otro de 39000 de hace 100 días (antes de la tasa más antigua, 3900)
- **THEN** `monthly-spending` trae `totalIncome` 20 en el mes en curso y 10 en el mes de hace 100 días

#### Scenario: Después de cambiar la moneda base
- **WHEN** una persona con moneda COP cambia su moneda base a EUR desde `PATCH /api/users/me` y pide `reports/balance`
- **THEN** la respuesta trae `currencyCode` EUR, y sus movimientos conservan `amount`, `amount_base` y `exchange_rate`

### Requirement: Ingresos de cada mes en el gasto mensual
Cada mes de `GET /api/monthly-spending` SHALL traer `totalIncome`: la suma de los ingresos (`INCOME`)
confirmados del mes que ya ocurrieron, con el mes decidido en la zona horaria del usuario. Las
transferencias MUST NOT sumar. `totalSpent`, la meta, `remaining`, `percentUsed` y
`transactionCount` SHALL conservar su significado; `transactionCount` cuenta solo los gastos.

#### Scenario: Mes en curso del escenario
- **WHEN** el usuario del escenario, con gastos por 1905500 y dos ingresos de 4500000 y 800000 en el mes en curso, pide `GET /api/monthly-spending`
- **THEN** el mes en curso trae `totalIncome` 5300000, `totalSpent` 1905500 y `transactionCount` 11

#### Scenario: Las transferencias no son ingresos
- **WHEN** el mes en curso del escenario tiene además dos transferencias que entran a la cuenta de débito
- **THEN** su `totalIncome` sigue siendo 5300000

#### Scenario: Meses anteriores con salario
- **WHEN** el usuario del escenario tiene un salario de 4500000 en cada uno de los dos meses anteriores
- **THEN** los dos meses traen `totalIncome` 4500000, y el de hace dos meses conserva `totalSpent` 1445000 y `transactionCount` 3

#### Scenario: Mes con meta y sin movimientos
- **WHEN** el usuario pide el mes siguiente, que solo tiene meta
- **THEN** el mes trae `totalIncome` 0 y `totalSpent` 0

### Requirement: Meses que trae el gasto mensual
`GET /api/monthly-spending` SHALL devolver, del más reciente al más antiguo, cada mes del rango
pedido que tenga al menos un gasto, un ingreso o una meta. Un mes sin ninguno de los tres MUST NOT
venir. Sin `from` ni `to` el rango SHALL ser los 12 meses que terminan en el mes en curso; con solo
`from`, hasta el mes en curso; con solo `to`, los 12 meses que terminan ahí.

#### Scenario: Mes con solo ingresos
- **WHEN** la persona tiene en un mes un ingreso y ningún gasto ni meta
- **THEN** ese mes viene con su `totalIncome`, `totalSpent` 0, `transactionCount` 0 y `budgetAmount`, `remaining` y `percentUsed` en `null`

#### Scenario: Rango por defecto del escenario
- **WHEN** el usuario del escenario pide `GET /api/monthly-spending` sin parámetros
- **THEN** recibe el mes en curso y los dos anteriores, en ese orden

#### Scenario: Usuario sin movimientos ni metas
- **WHEN** un usuario recién registrado pide `GET /api/monthly-spending`
- **THEN** la respuesta es 200 con un arreglo vacío

### Requirement: Validación de los parámetros del gasto mensual
`from` y `to` de `GET /api/monthly-spending` SHALL venir en formato `YYYY-MM`. Un mes mal escrito, o
un `from` posterior a `to`, SHALL responder 400 `VALIDATION_ERROR`.

#### Scenario: Mes mal escrito
- **WHEN** se pide `GET /api/monthly-spending?from=2026-13`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `from`

#### Scenario: Mes de cierre mal escrito
- **WHEN** se pide `GET /api/monthly-spending?to=2026-9`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `to`

#### Scenario: Rango invertido
- **WHEN** se pide `GET /api/monthly-spending` con `from` posterior a `to`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `from`

### Requirement: Metas en la moneda de la persona
`budgetAmount`, `remaining` y `percentUsed` de `GET /api/monthly-spending` SHALL calcularse con la
meta en la moneda base de la persona. Una meta en esa moneda SHALL aportar su monto tal cual; una en
otra SHALL convertirse con las tasas `USD→X` del primer día de su mes, elegidas con la regla de
`tasas-de-cambio`, a 4 decimales. Los topes por categoría SHALL seguir la misma regla. Las metas
guardadas MUST NOT reescribirse.

#### Scenario: Meta en la moneda de la persona
- **WHEN** el usuario del escenario, con moneda COP y meta global de 2000000 COP en el mes en curso, pide `GET /api/monthly-spending`
- **THEN** el mes en curso trae `budgetAmount` 2000000

#### Scenario: Meta en otra moneda
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes siguiente una meta global de 500 USD y ningún movimiento, y la tasa `USD→COP` vigente el primer día de ese mes es 4100
- **THEN** ese mes trae `budgetAmount` 2050000, `remaining` 2050000 y `percentUsed` 0

#### Scenario: Tope por categoría en otra moneda
- **WHEN** el usuario del escenario, con moneda COP, tiene en el mes en curso un tope de 100 USD en Viajes y la tasa `USD→COP` vigente el primer día del mes es 4100
- **THEN** el desglose por categoría del mes trae para Viajes un tope de 410000

### Requirement: Total de las cuentas débito en la consulta de saldo
`GET /api/reports/balance` SHALL traer `debitBalance`, en `currencyCode`: la suma de los saldos
vigentes de las cuentas `DEBIT` activas y no borradas del usuario, de cualquier moneda, convertidos
a su moneda base. Las cuentas de otros tipos, las desactivadas y las borradas MUST NOT sumar. Un
usuario sin cuentas débito SHALL recibir `debitBalance` 0.

#### Scenario: Usuario del escenario con una sola cuenta débito activa
- **WHEN** el usuario del escenario, con la cuenta débito Bancolombia activa, la débito Nequi desactivada, y Efectivo, Ahorros USD y Visa de otros tipos, pide la consulta de saldo
- **THEN** `debitBalance` es igual al `currentBalance` de Bancolombia

#### Scenario: Cuentas débito en dos monedas
- **WHEN** el usuario `saldo@financeapp.local`, con moneda COP, tiene la débito «Débito pesos» en COP y la débito «Débito dólares» en USD, y pide la consulta de saldo
- **THEN** `currencyCode` es COP y `debitBalance` es 1551000: 790000 de la cuenta en pesos más 761000 de la cuenta en dólares

#### Scenario: Usuario sin cuentas débito
- **WHEN** la persona en USD, que solo tiene una cuenta de efectivo, pide la consulta de saldo
- **THEN** `debitBalance` es 0

#### Scenario: Cuenta débito desactivada o de otro tipo
- **WHEN** `saldo@financeapp.local` tiene además la débito desactivada «Débito apagada» con 80000 y la de efectivo «Efectivo saldo» con 400000
- **THEN** ninguna de las dos está en `debitBalance` 1551000

### Requirement: Conversión del total débito a la moneda base
Cada aporte a `debitBalance` SHALL convertirse con la tasa de su propia fecha, elegida con la regla de `tasas-de-cambio`, nunca con la de hoy:
- el saldo inicial de la cuenta, con la tasa de la fecha local en que se creó;
- cada movimiento confirmado y ya ocurrido, con la regla de *Montos en la moneda de la persona*;
- lo que entra por una transferencia en otra moneda, su `destinationAmount` con la tasa de su fecha.

Lo que ya está en la moneda base MUST sumar su monto tal cual.

#### Scenario: Saldo inicial con la tasa del día en que se creó la cuenta
- **WHEN** «Débito dólares» se creó hace 50 días con saldo inicial 100 USD, la tasa `USD→COP` más antigua es 3900 (de hace 45 días) y la de hoy 4100
- **THEN** su saldo inicial aporta 390000 a `debitBalance`, no 410000

#### Scenario: Movimiento en otra moneda con la tasa de su fecha
- **WHEN** «Débito dólares» tiene un gasto de 10 USD de hace 40 días, cuando regía la tasa 3900
- **THEN** ese gasto resta 39000 de `debitBalance`

#### Scenario: Transferencia entre cuentas débito de monedas distintas
- **WHEN** hoy sale una transferencia de 410000 COP de «Débito pesos» que entra como 100 USD a «Débito dólares», con la tasa de hoy en 4100
- **THEN** resta 410000 de la cuenta en pesos, suma 410000 a la cuenta en dólares: su efecto neto sobre `debitBalance` es 0

#### Scenario: Montos en la moneda base sin pasar por el dólar
- **WHEN** «Débito pesos» tiene saldo inicial 1000000 COP, un ingreso de 500000, un gasto de 200000, la transferencia a «Débito dólares» y una de 100000 a «Efectivo saldo», todo en COP
- **THEN** la cuenta aporta exactamente 790000, sin diferencias de redondeo

#### Scenario: Pendientes y programados no cuentan
- **WHEN** «Débito pesos» tiene además un gasto pendiente de 50000 y un gasto programado de 70000 para el mes siguiente
- **THEN** `debitBalance` sigue en 1551000

#### Scenario: Después de cambiar la moneda base
- **WHEN** `saldo@financeapp.local` cambia su moneda base a USD desde `PATCH /api/users/me` y pide la consulta de saldo
- **THEN** la respuesta trae `currencyCode` USD y `debitBalance` 382.6829, y los movimientos y las cuentas conservan sus montos guardados

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
