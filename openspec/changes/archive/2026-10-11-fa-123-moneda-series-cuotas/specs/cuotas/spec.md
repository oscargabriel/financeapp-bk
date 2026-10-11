## MODIFIED Requirements

### Requirement: Alta de una compra en cuotas
`POST /api/installment-purchases` SHALL registrar una compra del usuario autenticado con `accountId`,
`categoryId`, `amount`, `description`, `purchaseDate` e `installmentCount`, y crear sus cuotas como
movimientos `EXPENSE` `CONFIRMED` del usuario sobre la tarjeta, con la categoría y la descripción de la
compra y el monto de cada cuota, en la misma transacción de base que la compra: o entra todo o no entra
nada. SHALL responder 201 con la compra —`id`, los campos recibidos, `currencyCode`,
`monthlyInterestRate`, `paidCount`, `remainingPrincipal`, `remainingAmount` y `nextInstallment`— más
`installments`: cada cuota con `number`, `transactionId`, `dueAt`, `principal`, `interest` y `amount`.
La compra y sus cuotas SHALL quedar en la moneda de la tarjeta, en cualquier moneda activa.

#### Scenario: Compra a tres cuotas
- **WHEN** el usuario registra hoy una compra de 1200000 a 3 cuotas en una tarjeta con tasa 2 y un día de pago posterior a hoy
- **THEN** la respuesta es 201 con `paidCount` 0, `remainingPrincipal` 1200000, `remainingAmount` 1248000 y `installments` de 424000, 416000 y 408000, y existen 3 movimientos con su `installment` y `"scheduled": true`

#### Scenario: Compra a una cuota
- **WHEN** el usuario registra una compra de 300000 a 1 cuota en una tarjeta con tasa 2 y `chargesSingleInstallmentInterest` `false`
- **THEN** la única cuota tiene `principal` 300000, `interest` 0 y `amount` 300000

#### Scenario: Compra a una cuota con interés
- **WHEN** el usuario registra una compra de 300000 a 1 cuota en una tarjeta con tasa 2 y `chargesSingleInstallmentInterest` `true`
- **THEN** la respuesta es 201 con `remainingPrincipal` 300000 y `remainingAmount` 306000, y la única cuota tiene `principal` 300000, `interest` 6000 y `amount` 306000

#### Scenario: Compra en una tarjeta en dólares
- **WHEN** el usuario registra hoy una compra de 100 a 3 cuotas en una tarjeta propia en USD con tasa 2 y un día de pago posterior a hoy
- **THEN** la respuesta es 201 con `currencyCode` `USD`, `remainingAmount` 104, y existen 3 movimientos en `USD` de 35.33, 34.66 y 34.01 sobre la tarjeta

### Requirement: Capital e interés de las cuotas
El capital de cada una de las N−1 primeras cuotas SHALL ser `amount` / N redondeado hacia abajo a los
decimales de la moneda de la tarjeta (0 en COP, 2 en USD), y el de la última, el resto, de modo que la
suma sea exactamente `amount`. El interés de la cuota k SHALL ser la tasa mensual de la tarjeta sobre el
capital pendiente antes de esa cuota, redondeado a esos mismos decimales con la mitad hacia arriba. Con tasa 0 o sin tasa, el interés SHALL ser 0. Con 1 cuota,
SHALL ser 0 salvo en una tarjeta con `chargesSingleInstallmentInterest` `true`, que SHALL cobrar la
misma regla sobre el total. El monto de cada cuota SHALL ser su capital más su interés. La tasa SHALL
tomarse de la tarjeta al registrar la compra y conservarse en la compra.

#### Scenario: El redondeo va a la última cuota
- **WHEN** el usuario simula una compra de 1000000 a 3 cuotas en una tarjeta con tasa 2.15
- **THEN** los capitales son 333333, 333333 y 333334, y los intereses 21500, 14333 y 7167

#### Scenario: Redondeo a centavos
- **WHEN** el usuario simula una compra de 100 a 3 cuotas en una tarjeta en USD con tasa 2
- **THEN** los capitales son 33.33, 33.33 y 33.34, los intereses 2.00, 1.33 y 0.67, `totalInterest` 4.00 y `totalAmount` 104.00

#### Scenario: Tarjeta sin tasa
- **WHEN** el usuario registra una compra de 900000 a 3 cuotas en una tarjeta con `monthlyInterestRate` `null`
- **THEN** la compra trae `monthlyInterestRate` 0 y las tres cuotas son de 300000 sin interés

#### Scenario: Cambiar la tasa después de la compra
- **WHEN** después de registrar la compra el usuario cambia la tasa de la tarjeta
- **THEN** las cuotas creadas conservan su monto y la compra su `monthlyInterestRate`

#### Scenario: Simular una cuota con interés
- **WHEN** el usuario simula una compra de 333333 a 1 cuota en una tarjeta con tasa 2.15 y `chargesSingleInstallmentInterest` `true`
- **THEN** la respuesta es 200 con la cuota en `principal` 333333, `interest` 7167 y `amount` 340500, y `totalInterest` 7167

#### Scenario: Una cuota con la opción y sin tasa
- **WHEN** el usuario simula una compra de 300000 a 1 cuota en una tarjeta con `chargesSingleInstallmentInterest` `true` y `monthlyInterestRate` 0 o `null`
- **THEN** la cuota tiene `interest` 0 y `amount` 300000

#### Scenario: La opción no cambia las compras de varias cuotas
- **WHEN** el usuario simula una compra de 1200000 a 3 cuotas en una tarjeta con tasa 2 y `chargesSingleInstallmentInterest` `true`
- **THEN** los intereses son 24000, 16000 y 8000, los mismos que sin la opción

#### Scenario: Cambiar la opción después de la compra
- **WHEN** después de registrar una compra a 1 cuota con interés el usuario pone `chargesSingleInstallmentInterest` en `false` en la tarjeta
- **THEN** la cuota conserva su `amount` y la compra su `remainingAmount`

### Requirement: Validación del alta
El alta y la simulación SHALL rechazar con 400 `VALIDATION_ERROR` y el campo de cada regla: `accountId`
ausente, ajeno, inactivo, en una moneda que ya no está activa, de un tipo distinto de `CREDIT`, o de una
tarjeta sin `statementDay` o sin `paymentDueDay`; `categoryId` ausente, ajena o de ingresos; `amount`
ausente, no mayor que 0, con más de 4 decimales o menor que `installmentCount` por la unidad mínima de la
moneda de la tarjeta (1 en COP, 0.01 en USD), para que cada cuota lleve al menos esa unidad de capital;
`description` en blanco o de más de 255 caracteres; `purchaseDate` ausente, mal formada o posterior a hoy
en la zona del usuario; `installmentCount` ausente, menor que 1 o mayor que 48. Los errores de formato
SHALL salir juntos. Nada SHALL guardarse.

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
- **WHEN** el usuario envía una compra de 10 a 12 cuotas en una tarjeta en COP
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `amount`

#### Scenario: Monto en centavos menor que las cuotas
- **WHEN** el usuario simula una compra de 0.10 a 12 cuotas en una tarjeta en USD
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `amount`

#### Scenario: Un centavo de capital por cuota
- **WHEN** el usuario simula una compra de 0.12 a 12 cuotas en una tarjeta en USD sin tasa
- **THEN** la respuesta es 200 con doce cuotas de 0.01

#### Scenario: Compra futura
- **WHEN** el usuario envía una compra con `purchaseDate` de mañana
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `purchaseDate`

#### Scenario: Categoría de ingresos
- **WHEN** el usuario envía una compra con una categoría que solo aplica a ingresos
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Varios errores juntos
- **WHEN** el usuario envía una compra sin `amount` y con `installmentCount` 0
- **THEN** la respuesta es 400 con un error en `amount` y otro en `installmentCount`

