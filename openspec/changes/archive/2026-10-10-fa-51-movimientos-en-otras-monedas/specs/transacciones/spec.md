## ADDED Requirements

### Requirement: Movimiento en la moneda de su cuenta
El alta en lote SHALL admitir cuentas activas del usuario en cualquier moneda, y cada movimiento SHALL
registrarse con `currencyCode` igual a la moneda de su cuenta origen. `currencyCode` en el elemento
SHALL ser opcional. Ausente o igual a la moneda de la cuenta, el movimiento SHALL registrarse con el
monto recibido y en el estado que corresponde a su origen. Un `currencyCode` que no tenga el formato
de 3 letras SHALL dar 400 `VALIDATION_ERROR` en `[i].currencyCode`, y uno que no sea una moneda activa
del catálogo SHALL dar lo mismo.

#### Scenario: Gasto en una cuenta en USD
- **WHEN** el usuario registra un gasto de 25 sobre su cuenta en USD, sin `currencyCode`
- **THEN** la respuesta es 201 con `"currencyCode": "USD"`, `"amount": 25`, `"status": "CONFIRMED"` y `originalAmount` y `originalCurrencyCode` en null, y el saldo de la cuenta baja 25

#### Scenario: currencyCode igual al de la cuenta
- **WHEN** el elemento trae `"currencyCode": "usd"` sobre una cuenta en USD
- **THEN** el movimiento se registra como si no lo trajera

#### Scenario: Moneda mal formada
- **WHEN** el elemento `[3]` trae `"currencyCode": "DOLAR"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[3].currencyCode`, junto con los demás errores de formato del lote

#### Scenario: Moneda que no está en el catálogo
- **WHEN** el elemento `[0]` trae `"currencyCode": "XYZ"`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].currencyCode` y nada se guarda

### Requirement: Conversión a la moneda de la cuenta
Si `currencyCode` difiere de la moneda de la cuenta origen, el monto SHALL convertirse a la moneda de
la cuenta con la tasa de `GET /api/exchange-rates` para la fecha del movimiento en la zona de la app.
El resultado SHALL redondearse a los decimales de esa moneda en el catálogo, con un mínimo de una
unidad de esa escala. El movimiento SHALL quedar `PENDING` sea cual sea su origen. `originalAmount` y
`originalCurrencyCode` SHALL guardar el monto y la moneda recibidos, y no SHALL modificarse después.
Si el par no tiene ninguna tasa, la respuesta SHALL ser 502 `EXTERNAL_SERVICE_ERROR` en `server`, y
nada SHALL guardarse.

#### Scenario: Gasto en pesos sobre una cuenta en dólares
- **WHEN** la tasa `USD→COP` de hoy es 4100 y el usuario registra un gasto de 41000 con `"currencyCode": "COP"` sobre su cuenta en USD
- **THEN** la respuesta es 201 con `"currencyCode": "USD"`, `"amount": 10`, `"status": "PENDING"`, `"originalAmount": 41000` y `"originalCurrencyCode": "COP"`, y el saldo de la cuenta no cambia hasta aprobarlo

#### Scenario: Convertido que redondea a cero
- **WHEN** la conversión de un monto a una moneda de 2 decimales da menos de 0.005
- **THEN** el movimiento se registra con `"amount": 0.01`

#### Scenario: Sin ninguna tasa del par
- **WHEN** una de las dos monedas no tiene ninguna fila en `exchange_rates` y el proveedor no responde
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server` y nada se guarda

#### Scenario: Ajustar el convertido antes de aprobarlo
- **WHEN** el usuario cambia con PATCH el `amount` de un gasto convertido pendiente a 9.8 y luego lo aprueba
- **THEN** el saldo de la cuenta baja 9.8, y `originalAmount` y `originalCurrencyCode` siguen siendo 41000 y COP

### Requirement: Transferencia entre cuentas de monedas distintas
En una transferencia entre cuentas de monedas distintas, `destinationAmount` SHALL estar en la moneda
del destino y ser lo que entra al destino.

- Con `destinationAmount`, la transferencia SHALL quedar en el estado de su origen, salvo que su monto
  se haya convertido.
- Sin `destinationAmount`, lo que entra al destino SHALL calcularse convirtiendo `amount`, ya en la
  moneda del origen, a la moneda del destino con la misma regla de tasa y redondeo. La transferencia
  SHALL quedar `PENDING`.

`destinationAmount` SHALL tener el formato de `amount`. SHALL dar 400 `VALIDATION_ERROR` en
`[i].destinationAmount` en un gasto o un ingreso, y en una transferencia entre cuentas de la misma
moneda.

#### Scenario: Con el monto de destino
- **WHEN** el usuario transfiere 410000 de su cuenta en COP a su cuenta en USD con `"destinationAmount": 95`
- **THEN** la respuesta es 201 con `"status": "CONFIRMED"` y `"destinationAmount": 95`; el saldo de la cuenta en COP baja 410000 y el de la cuenta en USD sube 95

#### Scenario: Sin el monto de destino
- **WHEN** la tasa `USD→COP` de hoy es 4100 y el usuario transfiere 10 de su cuenta en USD a su cuenta en COP sin `destinationAmount`
- **THEN** la respuesta es 201 con `"status": "PENDING"` y `"destinationAmount": 41000`, y ningún saldo cambia hasta aprobarla

#### Scenario: Monto de destino entre cuentas de la misma moneda
- **WHEN** el elemento `[0]` es una transferencia entre dos cuentas en COP con `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].destinationAmount`

#### Scenario: Monto de destino en un gasto
- **WHEN** el elemento `[0]` es un gasto con `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `[0].destinationAmount`

### Requirement: Campos de moneda en la respuesta de un movimiento
La respuesta del alta, del PATCH, de la aprobación y de la lista de pendientes SHALL incluir
`destinationAmount`, `originalAmount` y `originalCurrencyCode`, en null cuando no aplican.

#### Scenario: Pendiente convertido en la lista
- **WHEN** el usuario tiene un gasto convertido pendiente y pide `GET /api/transactions/pending`
- **THEN** ese movimiento trae `originalAmount` y `originalCurrencyCode` con lo recibido

#### Scenario: Movimiento sin conversión
- **WHEN** el movimiento es un gasto en la moneda de su cuenta
- **THEN** `destinationAmount`, `originalAmount` y `originalCurrencyCode` vienen en null

### Requirement: Moneda de las cuentas en la modificación
`currencyCode` no SHALL ser modificable.

- Un `accountId` en el parche SHALL estar en la moneda del movimiento. Si no, SHALL dar 400
  `VALIDATION_ERROR` en `accountId`.
- Si la transferencia resultante es entre cuentas de la misma moneda, o el tipo resultante no es
  transferencia, `destinationAmount` SHALL quedar en null. Un `destinationAmount` en el parche SHALL
  dar 400 `VALIDATION_ERROR` en `destinationAmount`.
- Si la transferencia resultante es entre monedas distintas, SHALL valer el `destinationAmount` del
  parche. Si el parche no lo trae, SHALL conservarse el guardado, siempre que el destino guardado
  estuviera en la misma moneda que el resultante. Si no hay ninguno aplicable, SHALL dar 400
  `VALIDATION_ERROR` en `destinationAmount`.
- `destinationAmount` en el parche SHALL tener el formato de `amount`.

#### Scenario: Ajustar el destino de una transferencia convertida
- **WHEN** el parche trae `{"destinationAmount": 40000}` sobre la transferencia pendiente de USD a COP
- **THEN** la respuesta es 200 con `"destinationAmount": 40000` y al aprobarla la cuenta en COP sube 40000

#### Scenario: Cuenta en otra moneda
- **WHEN** el parche trae como `accountId` la cuenta en USD sobre un gasto en COP
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId` y el movimiento no cambia

#### Scenario: Monto de destino en un gasto
- **WHEN** el parche trae `destinationAmount` sobre un gasto
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAmount`

#### Scenario: Pasar a transferencia entre monedas sin monto de destino
- **WHEN** el parche convierte un gasto en COP en una transferencia hacia la cuenta en USD, sin `destinationAmount`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `destinationAmount`

#### Scenario: Transferencia entre monedas que pasa a gasto
- **WHEN** el parche cambia a `EXPENSE` con categoría una transferencia de COP a USD que tenía `destinationAmount`
- **THEN** la respuesta es 200 con `"destinationAmount": null` y los saldos quedan coherentes

## MODIFIED Requirements

### Requirement: Referencias del usuario en la modificación
Toda cuenta o categoría que el parche envíe SHALL pertenecer al usuario y cumplir las mismas
condiciones que en el alta (cuenta activa, categoría vigente y compatible), además de la regla de
moneda de la modificación. Una referencia ajena, inexistente o mal formada SHALL dar el mismo error,
sin revelar si existe para otro usuario. Las referencias que el parche no envía y que siguen
correspondiendo al tipo resultante SHALL conservarse sin volver a validarse, salvo la categoría
cuando cambia el tipo.

#### Scenario: Cuenta de otro usuario
- **WHEN** el parche trae como `accountId` una cuenta de otro usuario
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `accountId` y el movimiento no cambia

#### Scenario: Categoría inexistente
- **WHEN** el parche trae un `categoryId` que no existe o está mal formado
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `categoryId`

#### Scenario: Cuenta desactivada después del alta
- **WHEN** la cuenta de un movimiento se desactivó y el parche solo trae `description`
- **THEN** la respuesta es 200 y el movimiento conserva su cuenta
