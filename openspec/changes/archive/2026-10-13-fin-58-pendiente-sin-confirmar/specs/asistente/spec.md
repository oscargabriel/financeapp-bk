## MODIFIED Requirements

### Requirement: Mensaje al asistente
`POST /api/assistant/messages` SHALL recibir `{"data": "<texto>"}` y responder 200 con `intent`,
`message`, y los campos `group`, `report` y `balance`, de los que solo SHALL venir el que corresponde
al `intent` y los demás en `null`. El texto que el modelo escriba SHALL NOT aparecer en la respuesta:
`message` sale de textos fijos del back.

#### Scenario: Respuesta sin función
- **WHEN** el modelo responde solo texto, sin llamar ninguna función
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"`, un `message` que lista lo que el asistente sabe hacer, y `group`, `report` y `balance` en `null`

#### Scenario: Función desconocida
- **WHEN** el modelo llama una función que no es `crear_movimiento`, `consultar_movimientos` ni `consultar_saldo`
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"` y no se crea ni se consulta nada

#### Scenario: Ya no hay transaction
- **WHEN** el asistente responde cualquier `intent`
- **THEN** la respuesta no trae el campo `transaction`

### Requirement: Crear un movimiento desde el asistente
Con `crear_movimiento`, el asistente SHALL registrar el movimiento del usuario del token sin
confirmar, con `"status": "PENDING"` y `"origin": "TELEGRAM"`, en la moneda de su cuenta, y responder
`"intent": "CREATE_TRANSACTION"` con `group`: el `id` de su grupo de pendientes y `transactions`, la
lista de lo registrado. Cada elemento SHALL traer solo `id`, `type`, `status`, `amount`,
`currencyCode`, `description`, `occurredAt` en UTC, y los nombres `account`, `destinationAccount` y
`category`, en `null` cuando no aplican. La cuenta, la cuenta destino y la categoría SHALL resolverse
por nombre, solo entre las activas del usuario, sin distinguir mayúsculas ni tildes. `message` SHALL
pedir confirmar o descartar. El movimiento SHALL NOT mover saldos hasta confirmarse.

#### Scenario: Gasto con cuenta y categoría propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE`, `monto` 20000, una cuenta y una categoría de gasto del usuario escritas en minúsculas
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y un `group` con un `id` y un único elemento en `transactions`, con `"status": "PENDING"`, `amount` 20000, y en `account` y `category` los nombres tal como los guardó el usuario; el movimiento aparece en `GET /api/transactions/pending` con `"origin": "TELEGRAM"` y el `groupId` de `group.id`

#### Scenario: Solo los campos livianos
- **WHEN** el asistente registra un gasto
- **THEN** cada elemento de `group.transactions` no trae `accountId`, `categoryId`, `origin`, `notes`, `originalAmount`, `groupId` ni ningún otro campo fuera de la lista del requisito, y `group` trae solo `id` y `transactions`

#### Scenario: El mensaje pide confirmar
- **WHEN** el asistente registra un gasto
- **THEN** `message` describe el gasto y pide confirmarlo o descartarlo

#### Scenario: El saldo no cambia
- **WHEN** el asistente registra un gasto sobre una cuenta
- **THEN** el `currentBalance` de esa cuenta en `GET /api/accounts` sigue igual

#### Scenario: Transferencia entre cuentas propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `TRANSFER`, una cuenta y una `cuenta_destino` del usuario
- **THEN** la respuesta es 200 con un elemento pendiente de tipo `TRANSFER`, los nombres de las dos cuentas en `account` y `destinationAccount`, y `category` en `null`

#### Scenario: Sin fecha
- **WHEN** el modelo no envía `fecha`
- **THEN** el movimiento tiene como `occurredAt` el instante en que se atendió la petición

#### Scenario: Fecha de otro día
- **WHEN** el modelo envía `fecha` `2026-10-05` y hoy es otro día
- **THEN** el movimiento tiene como `occurredAt` el 2026-10-05 a las 12:00 en la zona de la aplicación, expresado en UTC

#### Scenario: Confirmar lo que registró el asistente
- **WHEN** el usuario envía `POST /api/transactions/groups/{groupId}/approve` con el `group.id` de la respuesta
- **THEN** la respuesta es 200 con el movimiento `CONFIRMED` y el saldo de su cuenta se mueve

### Requirement: Lo que el asistente no puede resolver
Si un nombre de cuenta o de categoría no coincide con ninguno del usuario, o coincide con más de
uno, o si los argumentos no cumplen el formato del alta, el asistente SHALL responder 200 con
`"intent": "NEEDS_CLARIFICATION"` y un `message` que diga qué falta, y SHALL NOT crear ni consultar
nada. Con varios movimientos, basta que uno no se resuelva para que no se cree ninguno, y `message`
SHALL decir cuál, por su posición en el mensaje.

#### Scenario: Cuenta que no existe
- **WHEN** el modelo llama `crear_movimiento` con una cuenta que el usuario no tiene
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, el `message` nombra las cuentas del usuario, y `GET /api/transactions/pending` no cambia

#### Scenario: Cuenta de otro usuario
- **WHEN** el modelo nombra una cuenta que existe pero es de otro usuario
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Categoría ambigua
- **WHEN** el usuario tiene dos categorías activas cuyo nombre normalizado coincide con el que envía el modelo
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Monto inválido
- **WHEN** el modelo envía `monto` 0 o negativo
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y no se crea nada

#### Scenario: Filtro de consulta que no se resuelve
- **WHEN** el modelo llama `consultar_movimientos` con una categoría que el usuario no tiene
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"` y `report` en `null`

#### Scenario: El segundo de dos movimientos no se resuelve
- **WHEN** el modelo llama `crear_movimiento` dos veces y la cuenta de la segunda llamada no existe
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, el `message` dice que el problema está en el movimiento 2, `group` es `null` y `GET /api/transactions/pending` no cambia

### Requirement: Categoría que el usuario no nombra
La instrucción que el asistente envía al modelo SHALL pedirle que, si el usuario no nombra la
categoría de un gasto o un ingreso, la deduzca de lo que describe, solo entre las categorías del
usuario que aplican a ese tipo, y la omita si ninguna encaja con claridad o caben varias. SHALL
pedirle también que no deduzca la cuenta. La categoría que envíe el modelo SHALL resolverse igual
que una nombrada por el usuario.

#### Scenario: La instrucción pide deducir la categoría y no la cuenta
- **WHEN** el asistente consulta al modelo
- **THEN** la instrucción de sistema y la descripción de `categoria` en `crear_movimiento` piden deducir la categoría de lo que describe el usuario cuando no la nombra, y la instrucción pide enviar la cuenta vacía cuando el usuario no la nombra

#### Scenario: Gasto sin categoría
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE` y sin `categoria`
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, un `message` que nombra las categorías de gasto del usuario, `group` en `null`, y `GET /api/transactions/pending` no cambia

## ADDED Requirements

### Requirement: Varios movimientos en un mensaje
La instrucción SHALL pedir al modelo una llamada a `crear_movimiento` por cada movimiento que el
usuario mencione, y una sola llamada a las demás funciones. Si el modelo llama `crear_movimiento` una
o más veces, el asistente SHALL registrar todos esos movimientos en un mismo grupo, en el orden de las
llamadas, e ignorar las llamadas a otras funciones. Si no, SHALL atender la primera llamada.

#### Scenario: Dos gastos en un mensaje
- **WHEN** el modelo llama `crear_movimiento` dos veces, con un gasto de 20000 y otro de 15000 sobre la misma cuenta
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"`, un `group` con los dos en `transactions` en ese orden, los dos `PENDING`, y el saldo de la cuenta no cambia

#### Scenario: Confirmarlos juntos
- **WHEN** el usuario confirma el `group.id` de esa respuesta
- **THEN** la respuesta es 200 con los dos movimientos `CONFIRMED`, y el saldo baja 35000

#### Scenario: Descartarlos juntos
- **WHEN** el usuario descarta el `group.id` de una respuesta con dos gastos
- **THEN** la respuesta es 204 y ninguno de los dos sale en `GET /api/transactions/pending`

#### Scenario: Lo que no se confirma queda en la web
- **WHEN** el usuario no confirma ni descarta el grupo
- **THEN** los dos movimientos salen en `GET /api/transactions/pending` con el mismo `groupId` y se aprueban o rechazan uno a uno

#### Scenario: Registrar y consultar en el mismo mensaje
- **WHEN** el modelo llama `crear_movimiento` y `consultar_saldo`
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"`, el movimiento en `group` y `balance` en `null`

#### Scenario: Dos consultas
- **WHEN** el modelo llama `consultar_saldo` y después `consultar_movimientos`
- **THEN** la respuesta es 200 con `"intent": "GET_BALANCE"`

#### Scenario: La instrucción pide una llamada por movimiento
- **WHEN** el asistente consulta al modelo
- **THEN** la instrucción de sistema pide llamar `crear_movimiento` una vez por cada movimiento que el usuario mencione
