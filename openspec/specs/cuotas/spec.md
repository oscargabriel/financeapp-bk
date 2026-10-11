# cuotas Specification

## Purpose
Compras con tarjeta de crédito diferidas a cuotas (FA-108): la simulación, el alta, el listado de las
activas, la edición en grupo con alcance y la cancelación, bajo `/api/installment-purchases`. Cada cuota
es un gasto normal de la tarjeta, programado mientras su fecha no llega (FA-106).

## Requirements

### Requirement: Alta de una compra en cuotas
`POST /api/installment-purchases` SHALL registrar una compra del usuario autenticado con `accountId`,
`categoryId`, `amount`, `description`, `purchaseDate` e `installmentCount`, y crear sus cuotas como
movimientos `EXPENSE` `CONFIRMED` del usuario sobre la tarjeta, con la categoría y la descripción de la
compra y el monto de cada cuota, en la misma transacción de base que la compra: o entra todo o no entra
nada. SHALL responder 201 con la compra —`id`, los campos recibidos, `currencyCode`,
`monthlyInterestRate`, `paidCount`, `remainingPrincipal`, `remainingAmount` y `nextInstallment`— más
`installments`: cada cuota con `number`, `transactionId`, `dueAt`, `principal`, `interest` y `amount`.

#### Scenario: Compra a tres cuotas
- **WHEN** el usuario registra hoy una compra de 1200000 a 3 cuotas en una tarjeta con tasa 2 y un día de pago posterior a hoy
- **THEN** la respuesta es 201 con `paidCount` 0, `remainingPrincipal` 1200000, `remainingAmount` 1248000 y `installments` de 424000, 416000 y 408000, y existen 3 movimientos con su `installment` y `"scheduled": true`

#### Scenario: Compra a una cuota
- **WHEN** el usuario registra una compra de 300000 a 1 cuota en una tarjeta con tasa 2
- **THEN** la única cuota tiene `principal` 300000, `interest` 0 y `amount` 300000

### Requirement: Simulación de las cuotas
`POST /api/installment-purchases/preview` SHALL recibir el mismo cuerpo que el alta, aplicar las mismas
validaciones y responder 200 con `accountId`, `amount`, `currencyCode`, `purchaseDate`,
`installmentCount`, `monthlyInterestRate`, `totalInterest`, `totalAmount` e `installments`, cada una con
`number`, `dueAt`, `principal`, `interest` y `amount`. MUST NOT guardar nada.

#### Scenario: Simular antes de comprar
- **WHEN** el usuario simula una compra de 1200000 a 3 cuotas en una tarjeta con tasa 2
- **THEN** la respuesta es 200 con `totalInterest` 48000, `totalAmount` 1248000 y las tres cuotas del alta, y el `currentBalance` y el `availableCredit` de la tarjeta no cambian

#### Scenario: La simulación valida
- **WHEN** el usuario simula una compra con `installmentCount` 0
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `installmentCount`

### Requirement: Fechas de las cuotas
Con el día de corte C (`statementDay`) y el día de pago P (`paymentDueDay`) de la tarjeta, el primer
corte SHALL ser el día C del mes de la compra si la compra cae ese día o antes, y el del mes siguiente si
no. La primera cuota SHALL vencer el día P del mes del primer corte si P es mayor que C, y del mes
siguiente si no. Cada cuota siguiente SHALL vencer el día P del mes siguiente a la anterior. Un día que
el mes no tiene SHALL caer el último día de ese mes, sin mover las siguientes. Cada cuota SHALL tener
`occurredAt` a las 00:00 de su día en la zona del usuario.

#### Scenario: Compra antes del corte
- **WHEN** la tarjeta tiene corte 20 y pago 5 y la compra es del 9 de octubre
- **THEN** las cuotas vencen el 5 de noviembre, el 5 de diciembre y el 5 de enero

#### Scenario: Compra el mismo día del corte
- **WHEN** la misma tarjeta registra una compra del 20 de octubre
- **THEN** la primera cuota vence el 5 de noviembre

#### Scenario: Compra después del corte
- **WHEN** la misma tarjeta registra una compra del 21 de octubre
- **THEN** la primera cuota vence el 5 de diciembre

#### Scenario: Pago posterior al corte en el mismo mes
- **WHEN** la tarjeta tiene corte 5 y pago 20 y la compra es del 3 de octubre
- **THEN** la primera cuota vence el 20 de octubre

#### Scenario: Día 31 en meses más cortos
- **WHEN** la tarjeta tiene corte 31 y pago 31 y la compra, a 4 cuotas, es del 15 de diciembre de un año cuyo siguiente no es bisiesto
- **THEN** las cuotas vencen el 31 de enero, el 28 de febrero, el 31 de marzo y el 30 de abril

#### Scenario: Medianoche en la zona del usuario
- **WHEN** un usuario con zona `America/Bogota` tiene una cuota que vence el 5 de noviembre
- **THEN** su `occurredAt` es el 5 de noviembre a las `05:00:00Z`

### Requirement: Capital e interés de las cuotas
El capital de cada una de las N−1 primeras cuotas SHALL ser `amount` / N redondeado hacia abajo a pesos
enteros, y el de la última, el resto, de modo que la suma sea exactamente `amount`. El interés de la
cuota k SHALL ser la tasa mensual de la tarjeta sobre el capital pendiente antes de esa cuota, redondeado
a pesos enteros con la mitad hacia arriba. Con 1 cuota, o con tasa 0 o sin tasa, el interés SHALL ser 0.
El monto de cada cuota SHALL ser su capital más su interés. La tasa SHALL tomarse de la tarjeta al
registrar la compra y conservarse en la compra.

#### Scenario: El redondeo va a la última cuota
- **WHEN** el usuario simula una compra de 1000000 a 3 cuotas en una tarjeta con tasa 2.15
- **THEN** los capitales son 333333, 333333 y 333334, y los intereses 21500, 14333 y 7167

#### Scenario: Tarjeta sin tasa
- **WHEN** el usuario registra una compra de 900000 a 3 cuotas en una tarjeta con `monthlyInterestRate` `null`
- **THEN** la compra trae `monthlyInterestRate` 0 y las tres cuotas son de 300000 sin interés

#### Scenario: Cambiar la tasa después de la compra
- **WHEN** después de registrar la compra el usuario cambia la tasa de la tarjeta
- **THEN** las cuotas creadas conservan su monto y la compra su `monthlyInterestRate`

### Requirement: Compra con fecha pasada
`purchaseDate` SHALL poder ser anterior a hoy. Las cuotas con fecha de hoy o anterior SHALL contar en el
saldo de inmediato, y las posteriores quedar programadas.

#### Scenario: Compra de hace meses
- **WHEN** el usuario registra una compra de hace cuatro meses a 6 cuotas
- **THEN** las cuotas vencidas hasta hoy bajan el `currentBalance` de la tarjeta, `paidCount` las cuenta y las demás quedan con `"scheduled": true`

### Requirement: Validación del alta
El alta y la simulación SHALL rechazar con 400 `VALIDATION_ERROR` y el campo de cada regla: `accountId`
ausente, ajeno, inactivo, en otra moneda, de un tipo distinto de `CREDIT`, o de una tarjeta sin
`statementDay` o sin `paymentDueDay`; `categoryId` ausente, ajena o de ingresos; `amount` ausente, no
mayor que 0, con más de 4 decimales o menor que `installmentCount`; `description` en blanco o de más de
255 caracteres; `purchaseDate` ausente, mal formada o posterior a hoy en la zona del usuario;
`installmentCount` ausente, menor que 1 o mayor que 48. Los errores de formato SHALL salir juntos. Nada
SHALL guardarse.

#### Scenario: Cuenta que no es de crédito
- **WHEN** el usuario envía una compra sobre una cuenta `SAVINGS`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `accountId`

#### Scenario: Tarjeta sin día de corte
- **WHEN** el usuario envía una compra sobre una tarjeta sin `statementDay`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `accountId`

#### Scenario: Tarjeta de otro usuario
- **WHEN** el usuario envía una compra con el `accountId` de una tarjeta ajena
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` y "La cuenta no existe" en el campo `accountId`

#### Scenario: Demasiadas cuotas
- **WHEN** el usuario envía una compra con `installmentCount` 49
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `installmentCount`

#### Scenario: Monto menor que las cuotas
- **WHEN** el usuario envía una compra de 10 a 12 cuotas
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `amount`

#### Scenario: Compra futura
- **WHEN** el usuario envía una compra con `purchaseDate` de mañana
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `purchaseDate`

#### Scenario: Categoría de ingresos
- **WHEN** el usuario envía una compra con una categoría que solo aplica a ingresos
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Varios errores juntos
- **WHEN** el usuario envía una compra sin `amount` y con `installmentCount` 0
- **THEN** la respuesta es 400 con un error en `amount` y otro en `installmentCount`

### Requirement: Listado de compras activas
`GET /api/installment-purchases` SHALL devolver las compras del usuario no canceladas que tienen al menos
una cuota con `occurredAt` posterior al momento actual, ordenadas por la fecha de esa próxima cuota. Cada
una SHALL traer los campos de la compra; `paidCount`: `installmentCount` menos las cuotas existentes con
fecha posterior al momento actual; `remainingPrincipal` y `remainingAmount`: la suma del capital y del
monto de esas cuotas; y `nextInstallment`: `number`, `transactionId`, `dueAt` y `amount` de la más
próxima. Sin compras activas, SHALL responder 200 con una lista vacía.

#### Scenario: Una activa y una pagada
- **WHEN** el usuario tiene una compra con cuotas por venir y otra cuyas cuotas ya pasaron todas
- **THEN** la respuesta es 200 y solo trae la primera

#### Scenario: Una cuota ya pagada
- **WHEN** una compra a 3 cuotas de 424000, 416000 y 408000 tiene la primera vencida
- **THEN** trae `paidCount` 1, `remainingPrincipal` 800000, `remainingAmount` 824000 y `nextInstallment` con `number` 2

#### Scenario: Compras de otro usuario
- **WHEN** otro usuario tiene compras activas
- **THEN** no aparecen en el listado del usuario autenticado

### Requirement: Edición en grupo con alcance
`PATCH /api/installment-purchases/{id}` SHALL recibir `scope` (`FUTURE` o `ALL`) y al menos uno de
`description` y `categoryId`; ausente o `null` es «no cambia». Con `FUTURE`, los cambios SHALL aplicarse
a la compra y a sus cuotas con `occurredAt` posterior al momento actual; con `ALL`, también a las
pasadas. Las cuotas editadas a mano dentro del alcance SHALL recibir el cambio. Ningún saldo SHALL
cambiar. SHALL responder 200 con la compra, sin `installments`.

#### Scenario: Descripción solo en las futuras
- **WHEN** una compra tiene una cuota pasada y dos futuras y el usuario envía `{"scope": "FUTURE", "description": "TV sala"}`
- **THEN** la respuesta es 200 con `description` "TV sala"; las dos futuras la cambian y la pasada conserva la anterior

#### Scenario: Categoría en todas
- **WHEN** el usuario envía `{"scope": "ALL", "categoryId": <otra categoría de gastos>}`
- **THEN** las tres cuotas pasan a esa categoría

### Requirement: Validación de la edición
La edición SHALL rechazar con 400 `VALIDATION_ERROR`: `scope` ausente o distinto de `FUTURE` y `ALL`, en
el campo `scope`; un parche sin `description` ni `categoryId`, en el campo `body`; una `description` en
blanco o de más de 255 caracteres, en `description`; una `categoryId` mal formada, ajena o de ingresos,
en `categoryId`. Nada SHALL modificarse.

#### Scenario: Sin alcance
- **WHEN** el usuario envía `{"description": "TV"}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `scope`

#### Scenario: Solo el alcance
- **WHEN** el usuario envía `{"scope": "ALL"}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `body`

### Requirement: Cancelación de una compra
`DELETE /api/installment-purchases/{id}` SHALL borrar las cuotas de la compra con `occurredAt` posterior
al momento actual, conservar las pasadas con su `installment`, sacar la compra del listado y responder
204. El capital de las cuotas borradas SHALL volver al `availableCredit` de la tarjeta.

#### Scenario: Cancelar con cuotas por venir
- **WHEN** el usuario cancela una compra con una cuota pasada y dos futuras de 400000 de capital cada una
- **THEN** la respuesta es 204, la pasada sigue en `reports/transactions` con su `installment`, las futuras ya no existen, la compra no está en el listado y el `availableCredit` de la tarjeta sube 800000

### Requirement: Compra inexistente, ajena o cancelada
`PATCH` y `DELETE` sobre una compra que no existe, es de otro usuario o está cancelada SHALL responder 404
con `NOT_FOUND` en el campo `id`, sin modificar nada. Un `id` que no es un UUID SHALL responder 400 con
`VALIDATION_ERROR` en el campo `id`.

#### Scenario: Compra de otro usuario
- **WHEN** el usuario envía `PATCH /api/installment-purchases/{id}` con el id de una compra ajena
- **THEN** la respuesta es 404 con `NOT_FOUND` en el campo `id`

#### Scenario: Cancelar dos veces
- **WHEN** el usuario cancela una compra ya cancelada
- **THEN** la respuesta es 404 con `NOT_FOUND` en el campo `id`

#### Scenario: Id mal formado
- **WHEN** el usuario envía `DELETE /api/installment-purchases/no-es-uuid`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `id`

### Requirement: Cuotas modificadas o borradas a mano
Una cuota SHALL poder modificarse o borrarse con `PATCH` o `DELETE /api/transactions/{id}` como cualquier
movimiento, sin alterar las demás cuotas de su compra. Modificada, SHALL seguir perteneciendo a su compra
con su número. Borrada, SHALL contar como pagada en `paidCount`.

#### Scenario: Monto de una cuota cambiado a mano
- **WHEN** el usuario cambia con `PATCH /api/transactions/{id}` el monto de la segunda cuota
- **THEN** la respuesta trae el mismo `installment`, las otras cuotas conservan su monto y `remainingAmount` refleja el monto nuevo

#### Scenario: Pago adelantado
- **WHEN** el usuario borra la última cuota futura de una compra
- **THEN** `paidCount` sube en 1 y su capital vuelve al `availableCredit` de la tarjeta

### Requirement: Compras en cuotas autenticadas con JWT
`POST`, `GET`, `PATCH` y `DELETE` sobre `/api/installment-purchases` y `POST
/api/installment-purchases/preview` SHALL exigir un Bearer JWT válido. Sin credencial, o con la
credencial Basic compartida, SHALL responder 401 sin modificar nada.

#### Scenario: Alta sin credencial
- **WHEN** se envía `POST /api/installment-purchases` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Simulación con la credencial Basic compartida
- **WHEN** se envía `POST /api/installment-purchases/preview` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Listado con la credencial Basic compartida
- **WHEN** se envía `GET /api/installment-purchases` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Edición sin credencial
- **WHEN** se envía `PATCH /api/installment-purchases/{id}` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Cancelación con la credencial Basic compartida
- **WHEN** se envía `DELETE /api/installment-purchases/{id}` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
