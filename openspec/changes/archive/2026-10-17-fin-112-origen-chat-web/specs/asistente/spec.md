# Spec Delta

## MODIFIED Requirements

### Requirement: Crear un movimiento desde el asistente
Con `crear_movimiento`, el asistente SHALL registrar el movimiento del usuario del token sin
confirmar, con `"status": "PENDING"` y `"origin": "WEB_CHAT"`, en la moneda de su cuenta, y responder
`"intent": "CREATE_TRANSACTION"` con `group`: el `id` de su grupo de pendientes y `transactions`, la
lista de lo registrado. Cada elemento SHALL traer solo `id`, `type`, `status`, `amount`,
`currencyCode`, `description`, `occurredAt` en UTC, y los nombres `account`, `destinationAccount` y
`category`, en `null` cuando no aplican. La cuenta, la cuenta destino y la categoría SHALL resolverse
por nombre, solo entre las activas del usuario, sin distinguir mayúsculas ni tildes. `message` SHALL
pedir confirmar o descartar. El movimiento SHALL NOT mover saldos hasta confirmarse.

#### Scenario: Gasto con cuenta y categoría propias
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE`, `monto` 20000, una cuenta y una categoría de gasto del usuario escritas en minúsculas
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y un `group` con un `id` y un único elemento en `transactions`, con `"status": "PENDING"`, `amount` 20000, y en `account` y `category` los nombres tal como los guardó el usuario; el movimiento aparece en `GET /api/transactions/pending` con `"origin": "WEB_CHAT"` y el `groupId` de `group.id`

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
- **THEN** la respuesta es 200 con el movimiento `CONFIRMED`, `"origin": "WEB_CHAT"`, y el saldo de su cuenta se mueve

#### Scenario: Con un archivo también es el chat web
- **WHEN** el asistente registra un gasto a partir de un mensaje `multipart/form-data` con un archivo
- **THEN** el movimiento aparece en `GET /api/transactions/pending` con `"origin": "WEB_CHAT"`

