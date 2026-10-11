# recurrentes Specification

## Purpose
Series de gastos o ingresos que se repiten cada cierto número de semanas o de meses (FA-107): su alta,
el listado de las activas, la edición en grupo con alcance y la cancelación, bajo `/api/recurrences`.
Cada ocurrencia de una serie es un movimiento normal, programado mientras su fecha no llega (FA-106).

## Requirements

### Requirement: Alta de una serie
`POST /api/recurrences` SHALL crear una serie del usuario autenticado con `type` (`EXPENSE` o
`INCOME`), `accountId`, `categoryId`, `amount`, `description`, `frequency` (`WEEKLY` o `MONTHLY`),
`interval` (1 si no llega), `dayOfWeek` en las semanales o `dayOfMonth` en las mensuales, y
`startDate`, con `endDate` u `occurrences` opcionales. SHALL responder 201 con la serie: `id`, los
campos recibidos, `currencyCode` y `nextOccurrenceAt`. Las ocurrencias SHALL crearse como movimientos
`CONFIRMED` del usuario, con la cuenta, la categoría, el monto y la descripción de la serie, en la
misma transacción de base que la serie: o entra todo o no entra nada.

#### Scenario: Serie mensual con número de repeticiones
- **WHEN** el usuario crea una serie `MONTHLY` de 44900 con `dayOfMonth` 15, `startDate` dentro de un mes y `occurrences` 3
- **THEN** la respuesta es 201 con `nextOccurrenceAt` en el día 15 del mes de inicio, y existen 3 movimientos de 44900 con ese `recurrenceId`, todos con `"scheduled": true`

#### Scenario: Interval por defecto
- **WHEN** el usuario crea una serie sin `interval`
- **THEN** la respuesta trae `"interval": 1`

### Requirement: Fechas de las ocurrencias
La primera ocurrencia SHALL ser la primera fecha igual o posterior a `startDate` que cae en el día de
la serie. Las siguientes SHALL caer cada `interval` semanas, en `dayOfWeek`, o cada `interval` meses,
en `dayOfMonth`. Si `dayOfMonth` no existe en un mes, la ocurrencia SHALL caer el último día de ese
mes, sin mover las siguientes. Cada ocurrencia SHALL tener `occurredAt` a las 00:00 de su día en la zona
del usuario.

#### Scenario: Semanal cada dos semanas
- **WHEN** el usuario crea una serie `WEEKLY` con `interval` 2, `dayOfWeek` `MONDAY`, un `startDate` que es miércoles y `occurrences` 3
- **THEN** las ocurrencias caen el lunes siguiente al inicio y dos y cuatro semanas después

#### Scenario: Día 31 en meses más cortos
- **WHEN** el usuario crea una serie `MONTHLY` con `dayOfMonth` 31, `startDate` el 31 de enero de un año no bisiesto y `occurrences` 4
- **THEN** las ocurrencias caen el 31 de enero, el 28 de febrero, el 31 de marzo y el 30 de abril

#### Scenario: Medianoche en la zona del usuario
- **WHEN** un usuario con zona `America/Bogota` crea una serie cuya ocurrencia cae el 15 de un mes
- **THEN** su `occurredAt` es el 15 a las `05:00:00Z`

### Requirement: Series con fin
Una serie con `endDate` SHALL crear al darse de alta todas sus ocurrencias hasta esa fecha, incluida.
Una con `occurrences` SHALL crear exactamente ese número. Las que tengan fecha posterior a hoy SHALL
quedar programadas; las de hoy o antes SHALL contar en los saldos de inmediato.

#### Scenario: Con fecha de fin
- **WHEN** el usuario crea una serie `WEEKLY` que empieza en un lunes futuro, con `dayOfWeek` `MONDAY` y `endDate` 21 días después del inicio
- **THEN** existen 4 ocurrencias, todas programadas

#### Scenario: Serie que empezó en el pasado
- **WHEN** el usuario crea una serie `MONTHLY` de 30000 con `dayOfMonth` 1, `startDate` el día 1 de hace dos meses y `occurrences` 3, sobre una cuenta con saldo 2000000
- **THEN** las tres ocurrencias ya ocurrieron y cuentan, y el `currentBalance` de la cuenta queda en 1910000

### Requirement: Series sin fin
Una serie sin `endDate` ni `occurrences` SHALL tener creadas sus ocurrencias con fecha de hoy o
anterior y exactamente una posterior a hoy. Cuando la fecha de esa ocurrencia llega, la siguiente
SHALL existir sin que nadie la cree a mano, aunque el servicio no haya atendido peticiones mientras
tanto: `GET /api/accounts`, `GET /api/reports/balance`, `GET /api/reports/transactions`,
`GET /api/monthly-spending` y `GET /api/recurrences` SHALL responder con las ocurrencias que falten ya
creadas.

#### Scenario: Sin fin que empieza en el futuro
- **WHEN** el usuario crea una serie sin fin con `startDate` dentro de diez días
- **THEN** existe una sola ocurrencia, programada, en la fecha de `nextOccurrenceAt`

#### Scenario: Sin fin que empieza hoy
- **WHEN** el usuario crea una serie `WEEKLY` sin fin con `startDate` hoy y `dayOfWeek` el de hoy
- **THEN** existen dos ocurrencias: la de hoy, que ya cuenta, y la de dentro de una semana, programada

#### Scenario: Serie atrasada
- **WHEN** una serie semanal sin fin tiene creada solo una ocurrencia de hace tres semanas, y el usuario pide `GET /api/accounts`
- **THEN** existen la ocurrencia de hace tres semanas, las de hace dos y una semana, la de esta semana si ya pasó su día, y una programada; el `currentBalance` de la cuenta ya descuenta las pasadas

#### Scenario: Dos lecturas a la vez
- **WHEN** dos peticiones del mismo usuario ponen al día la misma serie atrasada al mismo tiempo
- **THEN** cada ocurrencia faltante se crea una sola vez

### Requirement: Validación del alta
El alta SHALL rechazar con 400 `VALIDATION_ERROR` y todos los errores juntos, cada uno en su campo:
`type` ausente o distinto de `EXPENSE` e `INCOME`; `accountId` o `categoryId` ausentes, ajenos,
inexistentes, desactivados, en otra moneda que COP o de una categoría que no aplica al tipo; `amount`
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

### Requirement: Listado de series activas
`GET /api/recurrences` SHALL devolver las series del usuario que no están canceladas y tienen
ocurrencias por venir: toda serie sin fin, y una con fin mientras le quede alguna ocurrencia con
`occurredAt` posterior al momento actual. Cada una SHALL traer los campos del alta y
`nextOccurrenceAt`: la ocurrencia más próxima con fecha posterior al momento actual o, en una serie sin
fin que no tiene ninguna creada, la fecha de la que va a crear. SHALL ir ordenadas por
`nextOccurrenceAt`. Sin series activas, SHALL responder 200 con una lista vacía.

#### Scenario: Una activa y una terminada
- **WHEN** el usuario tiene una serie sin fin y otra con fin cuyas ocurrencias ya pasaron todas
- **THEN** la respuesta es 200 y solo trae la serie sin fin

#### Scenario: Una cancelada
- **WHEN** el usuario canceló una serie
- **THEN** la serie no aparece en el listado

#### Scenario: Series de otro usuario
- **WHEN** otro usuario tiene series activas
- **THEN** no aparecen en el listado del usuario autenticado

### Requirement: Edición en grupo con alcance
`PATCH /api/recurrences/{id}` SHALL recibir `scope` (`FUTURE` o `ALL`) y al menos uno de `amount`,
`description`, `categoryId`, `accountId`, `frequency`, `interval`, `dayOfWeek` y `dayOfMonth`; ausente
o `null` es «no cambia». Con `FUTURE`, los cambios de monto, descripción, categoría y cuenta SHALL
aplicarse a la serie y a sus ocurrencias con `occurredAt` posterior al momento actual, en la zona del
usuario. Con `ALL`, también a las pasadas, y los saldos SHALL quedar como si siempre hubieran tenido
los valores nuevos. Las ocurrencias editadas a mano dentro del alcance SHALL recibir el cambio. SHALL
responder 200 con la serie.

#### Scenario: Monto solo en las futuras
- **WHEN** una serie mensual de 30000 tiene una ocurrencia pasada y dos futuras, y el usuario envía `{"scope": "FUTURE", "amount": 35000}`
- **THEN** la respuesta es 200 con `amount` 35000; las dos futuras pasan a 35000, la pasada sigue en 30000, y el `currentBalance` no cambia

#### Scenario: Monto en todas
- **WHEN** la misma serie recibe `{"scope": "ALL", "amount": 35000}`
- **THEN** las tres ocurrencias pasan a 35000 y el `currentBalance` de la cuenta baja 5000

#### Scenario: Cambio de cuenta en todas
- **WHEN** el usuario envía `{"scope": "ALL", "accountId": <otra cuenta propia>}` sobre una serie con una ocurrencia pasada de 30000
- **THEN** el saldo de la cuenta anterior sube 30000 y el de la nueva baja 30000

### Requirement: Cambio de periodicidad
Si el parche cambia `frequency`, `interval`, `dayOfWeek` o `dayOfMonth`, con cualquier `scope`, las
ocurrencias con `occurredAt` posterior al momento actual SHALL borrarse y rehacerse con la regla nueva,
que empieza el día siguiente a hoy en la zona del usuario. Las pasadas SHALL conservar su fecha. Una
serie con `occurrences` SHALL conservar ese total, descontando las ya ocurridas; una con `endDate`
SHALL llegar hasta esa fecha; una sin fin SHALL quedar con la siguiente. Las reglas de validación
SHALL aplicarse a la serie resultante.

#### Scenario: De mensual a semanal
- **WHEN** una serie mensual con `occurrences` 6, de la que ya ocurrieron 2, recibe `{"scope": "FUTURE", "frequency": "WEEKLY", "dayOfWeek": "FRIDAY"}`
- **THEN** las 2 pasadas conservan su fecha, y existen 4 ocurrencias futuras, en los 4 viernes siguientes a hoy

#### Scenario: Pasar a mensual sin día del mes
- **WHEN** una serie semanal recibe `{"scope": "FUTURE", "frequency": "MONTHLY"}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `dayOfMonth`

### Requirement: Validación de la edición
La edición SHALL rechazar con 400 `VALIDATION_ERROR`: `scope` ausente o distinto de `FUTURE` y `ALL`,
en el campo `scope`; un parche sin ningún campo que modificar, en el campo `body`; y cada campo
presente que no cumpla la regla del alta, en su campo. Nada SHALL modificarse.

#### Scenario: Sin alcance
- **WHEN** el usuario envía `{"amount": 35000}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `scope`

#### Scenario: Solo el alcance
- **WHEN** el usuario envía `{"scope": "ALL"}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `body`

#### Scenario: Intervalo inválido en la edición
- **WHEN** el usuario envía `{"scope": "FUTURE", "interval": 0}`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `interval`

### Requirement: Cancelación de una serie
`DELETE /api/recurrences/{id}` SHALL borrar las ocurrencias de la serie con `occurredAt` posterior al
momento actual, conservar las pasadas con su `recurrenceId`, sacar la serie del listado de activas y
responder 204.

#### Scenario: Cancelar una serie con ocurrencias futuras
- **WHEN** el usuario cancela una serie con una ocurrencia pasada y dos futuras
- **THEN** la respuesta es 204, la pasada sigue en `reports/transactions` con su `recurrenceId`, las futuras ya no existen y la serie no está en `GET /api/recurrences`

### Requirement: Serie inexistente, ajena o cancelada
`PATCH` y `DELETE` sobre una serie que no existe, es de otro usuario o está cancelada SHALL responder
404 con `NOT_FOUND` en el campo `id`, sin modificar nada. Un `id` que no es un UUID SHALL responder 400
con `VALIDATION_ERROR` en el campo `id`.

#### Scenario: Serie de otro usuario
- **WHEN** el usuario envía `PATCH /api/recurrences/{id}` con el id de una serie ajena
- **THEN** la respuesta es 404 con `NOT_FOUND` en el campo `id`

#### Scenario: Cancelar dos veces
- **WHEN** el usuario cancela una serie ya cancelada
- **THEN** la respuesta es 404 con `NOT_FOUND` en el campo `id`

#### Scenario: Id mal formado
- **WHEN** el usuario envía `DELETE /api/recurrences/no-es-uuid`
- **THEN** la respuesta es 400 con `VALIDATION_ERROR` en el campo `id`

### Requirement: Ocurrencias modificadas o borradas a mano
Una ocurrencia SHALL poder modificarse o borrarse con `PATCH` o `DELETE /api/transactions/{id}` como
cualquier movimiento. Modificada, SHALL seguir perteneciendo a su serie. Borrada, la serie no SHALL
volver a crearla al crear las siguientes.

#### Scenario: Monto cambiado a mano
- **WHEN** el usuario cambia con `PATCH /api/transactions/{id}` el monto de una ocurrencia futura
- **THEN** la respuesta trae el mismo `recurrenceId`, y la serie sigue en el listado de activas

#### Scenario: Ocurrencia borrada a mano en una serie sin fin
- **WHEN** el usuario borra la ocurrencia programada de una serie sin fin y, después de su fecha, consulta sus cuentas
- **THEN** la ocurrencia borrada no vuelve a existir y la serie crea la siguiente

### Requirement: Series autenticadas con JWT
`POST`, `GET`, `PATCH` y `DELETE` sobre `/api/recurrences` SHALL exigir un Bearer JWT válido. Sin
credencial, o con la credencial Basic compartida, SHALL responder 401 sin modificar nada.

#### Scenario: Alta sin credencial
- **WHEN** se envía `POST /api/recurrences` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Listado con la credencial Basic compartida
- **WHEN** se envía `GET /api/recurrences` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Edición sin credencial
- **WHEN** se envía `PATCH /api/recurrences/{id}` sin cabecera `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Cancelación con la credencial Basic compartida
- **WHEN** se envía `DELETE /api/recurrences/{id}` con el Basic válido de `/auth/*`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`
