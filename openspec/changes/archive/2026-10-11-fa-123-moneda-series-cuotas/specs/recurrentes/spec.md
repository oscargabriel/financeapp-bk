## MODIFIED Requirements

### Requirement: Alta de una serie
`POST /api/recurrences` SHALL crear una serie del usuario autenticado con `type` (`EXPENSE` o
`INCOME`), `accountId`, `categoryId`, `amount`, `description`, `frequency` (`WEEKLY` o `MONTHLY`),
`interval` (1 si no llega), `dayOfWeek` en las semanales o `dayOfMonth` en las mensuales, y
`startDate`, con `endDate` u `occurrences` opcionales. SHALL responder 201 con la serie: `id`, los
campos recibidos, `currencyCode` y `nextOccurrenceAt`. Las ocurrencias SHALL crearse como movimientos
`CONFIRMED` del usuario, con la cuenta, la categoría, el monto y la descripción de la serie, en la
misma transacción de base que la serie: o entra todo o no entra nada. La serie SHALL tomar la moneda
de su cuenta, en cualquier moneda activa, y sus ocurrencias SHALL quedar en esa misma moneda.

#### Scenario: Serie mensual con número de repeticiones
- **WHEN** el usuario crea una serie `MONTHLY` de 44900 con `dayOfMonth` 15, `startDate` dentro de un mes y `occurrences` 3
- **THEN** la respuesta es 201 con `nextOccurrenceAt` en el día 15 del mes de inicio, y existen 3 movimientos de 44900 con ese `recurrenceId`, todos con `"scheduled": true`

#### Scenario: Interval por defecto
- **WHEN** el usuario crea una serie sin `interval`
- **THEN** la respuesta trae `"interval": 1`

#### Scenario: Serie en una cuenta en dólares
- **WHEN** el usuario crea una serie `MONTHLY` de 12.5 sobre una cuenta propia en USD, con `occurrences` 2
- **THEN** la respuesta es 201 con `currencyCode` `USD`, y existen 2 movimientos de 12.5 en `USD` con ese `recurrenceId`

### Requirement: Validación del alta
El alta SHALL rechazar con 400 `VALIDATION_ERROR` y todos los errores juntos, cada uno en su campo:
`type` ausente o distinto de `EXPENSE` e `INCOME`; `accountId` o `categoryId` ausentes, ajenos,
inexistentes, desactivados, en una moneda que ya no está activa o de una categoría que no aplica al tipo; `amount`
ausente, cero, negativo o con más decimales o dígitos de los admitidos; `description` vacía o de más
de 255 caracteres; `frequency` ausente o desconocida; `interval` menor que 1 o mayor que 52 en `WEEKLY` y
que 12 en `MONTHLY`; `dayOfWeek` ausente o desconocido en `WEEKLY`, o presente en `MONTHLY`;
`dayOfMonth` ausente o fuera de 1 a 31 en `MONTHLY`, o presente en `WEEKLY`; `startDate` ausente o mal
formada; `endDate` mal formada o anterior a `startDate`; `occurrences` menor que 1 o mayor que 500;
`endDate` y `occurrences` juntos. Una serie que crearía más de 500 ocurrencias SHALL rechazarse sobre
`endDate`, o sobre `startDate` si no tiene fin; una sin ninguna ocurrencia antes de su `endDate`,
sobre `endDate`. Nada SHALL guardarse.

#### Scenario: Día del mes fuera de rango
- **WHEN** el usuario envía una serie `MONTHLY` con `dayOfMonth` 32
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `dayOfMonth`

#### Scenario: Intervalo menor que 1
- **WHEN** el usuario envía una serie con `interval` 0
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `interval`

#### Scenario: Fin anterior al inicio
- **WHEN** el usuario envía una serie con `endDate` anterior a `startDate`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `endDate`

#### Scenario: Día de la semana en una serie mensual
- **WHEN** el usuario envía una serie `MONTHLY` con `dayOfMonth` 5 y `dayOfWeek` `MONDAY`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `dayOfWeek`

#### Scenario: Semanal sin día de la semana
- **WHEN** el usuario envía una serie `WEEKLY` sin `dayOfWeek`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `dayOfWeek`

#### Scenario: Fin y repeticiones juntos
- **WHEN** el usuario envía una serie con `endDate` y `occurrences`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `occurrences`

#### Scenario: Transferencia
- **WHEN** el usuario envía una serie con `type` `TRANSFER`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `type`

#### Scenario: Cuenta de otro usuario
- **WHEN** el usuario envía una serie con el `accountId` de una cuenta ajena
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` y "La cuenta no existe" en el campo `accountId`

#### Scenario: Demasiadas ocurrencias
- **WHEN** el usuario envía una serie `WEEKLY` con `endDate` diez años después de `startDate`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `endDate`

#### Scenario: Varios errores juntos
- **WHEN** el usuario envía una serie sin `amount` y con `interval` 0
- **THEN** la respuesta es 400 con un error en `amount` y otro en `interval`

### Requirement: Validación de la edición
La edición SHALL rechazar con 400 `VALIDATION_ERROR`: `scope` ausente o distinto de `FUTURE` y `ALL`,
en el campo `scope`; un parche sin ningún campo que modificar, en el campo `body`; y cada campo
presente que no cumpla la regla del alta, en su campo. La moneda de la serie no cambia: un `accountId`
en una moneda distinta de la de la serie SHALL rechazarse en el campo `accountId`. Nada SHALL
modificarse.

#### Scenario: Sin alcance
- **WHEN** el usuario envía `{"amount": 35000}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `scope`

#### Scenario: Solo el alcance
- **WHEN** el usuario envía `{"scope": "ALL"}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `body`

#### Scenario: Intervalo inválido en la edición
- **WHEN** el usuario envía `{"scope": "FUTURE", "interval": 0}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `interval`

#### Scenario: Cuenta en otra moneda
- **WHEN** el usuario envía `{"scope": "ALL", "accountId": <cuenta propia en COP>}` sobre una serie en USD
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `accountId`, y la serie y sus ocurrencias no cambian

#### Scenario: Cuenta en la misma moneda
- **WHEN** el usuario envía `{"scope": "ALL", "accountId": <otra cuenta propia en USD>}` sobre una serie en USD
- **THEN** la respuesta es 200 con el `accountId` nuevo y `currencyCode` `USD`, y las ocurrencias pasan a la cuenta nueva

