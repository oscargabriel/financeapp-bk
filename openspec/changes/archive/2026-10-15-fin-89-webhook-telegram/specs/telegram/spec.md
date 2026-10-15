# Spec Delta

## Purpose

El bot de Telegram del lado del API: cómo entran los updates de Telegram, cómo se autentican y qué
recibe cada chat según esté vinculado o no a un usuario.

## ADDED Requirements

### Requirement: El webhook se autentica con el secreto de Telegram
`POST /api/telegram/webhook` SHALL aceptar solo peticiones cuya cabecera
`X-Telegram-Bot-Api-Secret-Token` sea igual al secreto configurado del webhook. Sin la cabecera, o
con otro valor, SHALL responder 401 con code `UNAUTHENTICATED` y field `authorization`, sin procesar
el update ni enviar nada al chat. El motivo del rechazo no SHALL salir en la respuesta.

#### Scenario: Update con el secreto correcto
- **WHEN** llega un update con la cabecera del secreto correcto
- **THEN** responde 200

#### Scenario: Sin la cabecera del secreto
- **WHEN** llega un update sin la cabecera `X-Telegram-Bot-Api-Secret-Token`
- **THEN** responde 401 con code `UNAUTHENTICATED` y field `authorization`
- **AND** no se envía ningún mensaje al chat

#### Scenario: Con un secreto equivocado
- **WHEN** llega un update con la cabecera y un valor distinto del secreto
- **THEN** responde 401 con code `UNAUTHENTICATED` y field `authorization`
- **AND** no se envía ningún mensaje al chat

### Requirement: Las credenciales de las otras cadenas no sirven en el webhook
El webhook SHALL rechazar con 401 una petición que traiga la Basic compartida o un Bearer válido en
lugar del secreto, y el secreto del webhook no SHALL autenticar ninguna otra ruta del API.

#### Scenario: Basic válida contra el webhook
- **WHEN** llega un update con la credencial Basic compartida y sin el secreto
- **THEN** responde 401 con code `UNAUTHENTICATED`

#### Scenario: Bearer válido contra el webhook
- **WHEN** llega un update con un access token válido y sin el secreto
- **THEN** responde 401 con code `UNAUTHENTICATED`

#### Scenario: El secreto contra otra ruta
- **WHEN** se pide `GET /api/status` o `GET /api/accounts` con la cabecera del secreto correcto y sin
  otra credencial
- **THEN** responde 401 con code `UNAUTHENTICATED`

### Requirement: Un chat sin vincular recibe una respuesta fija
Un mensaje de un chat que no está vinculado a un usuario SHALL recibir del bot, en ese mismo chat,
el texto «Este chat todavía no está vinculado a una cuenta de FinanceApp.», y no SHALL llegar al
asistente ni al modelo. Mientras no exista la vinculación, todo chat está sin vincular.

#### Scenario: Mensaje de texto de un chat sin vincular
- **WHEN** llega, con el secreto correcto, un update con un mensaje de texto del chat 123456
- **THEN** responde 200
- **AND** el bot envía al chat 123456 el texto fijo
- **AND** el asistente no recibe el mensaje

#### Scenario: Mensaje sin texto de un chat sin vincular
- **WHEN** llega, con el secreto correcto, un update con un mensaje sin texto (una foto, un audio,
  un contacto) del chat 123456
- **THEN** responde 200
- **AND** el bot envía al chat 123456 el texto fijo

### Requirement: Un update sin mensaje se ignora
Un update autenticado que no trae `message` (un mensaje editado, un callback, un cambio de miembros)
SHALL responder 200 sin enviar nada, para que Telegram no lo reintente.

#### Scenario: Update sin mensaje
- **WHEN** llega, con el secreto correcto, un update con `update_id` y sin `message`
- **THEN** responde 200
- **AND** no se envía ningún mensaje

### Requirement: Cuerpo del update
Un update autenticado cuyo cuerpo no es JSON SHALL responder 400 con code `JSON_PARSING_ERROR` y
field `body`. Si
trae `message`, el mensaje SHALL traer `chat.id`: sin `chat`, 400 con code `VALIDATION_ERROR` y
field `message.chat`; con `chat` y sin `id`, el mismo error con field `message.chat.id`.

#### Scenario: Cuerpo que no es JSON
- **WHEN** llega, con el secreto correcto, un cuerpo que no es JSON
- **THEN** responde 400 con code `JSON_PARSING_ERROR` y field `body`

#### Scenario: Mensaje sin chat
- **WHEN** llega, con el secreto correcto, un update con `message` y sin `chat`
- **THEN** responde 400 con code `VALIDATION_ERROR` y field `message.chat`
- **AND** no se envía ningún mensaje

### Requirement: Un fallo al responder no hace reintentar el update
Si Telegram no acepta el mensaje del bot o no responde, el webhook SHALL responder 200 igual y dejar
el fallo en el log con su status, para que Telegram no reenvíe el update.

#### Scenario: La Bot API rechaza el envío
- **WHEN** llega un mensaje autenticado de un chat sin vincular y la Bot API responde con error al
  enviar el texto fijo
- **THEN** el webhook responde 200

### Requirement: Credenciales del bot obligatorias y fuera de los logs
La aplicación SHALL leer el token del bot y el secreto del webhook de `TELEGRAM_BOT_TOKEN` y
`TELEGRAM_WEBHOOK_SECRET`, sin valor por defecto: sin ellas no arranca. El secreto SHALL tener entre
32 y 256 caracteres de `A-Z`, `a-z`, `0-9`, `_` y `-`, o la aplicación no arranca. Ni el token ni el
secreto SHALL aparecer en los logs, tampoco en los de un envío fallido.

#### Scenario: Sin las variables
- **WHEN** el servicio arranca sin `TELEGRAM_BOT_TOKEN` o sin `TELEGRAM_WEBHOOK_SECRET`
- **THEN** la aplicación no arranca

#### Scenario: Secreto que Telegram no aceptaría o demasiado corto
- **WHEN** el servicio arranca con un secreto de menos de 32 caracteres o con un carácter fuera del
  alfabeto admitido
- **THEN** la aplicación no arranca, y el error no incluye el valor del secreto

#### Scenario: Envío fallido
- **WHEN** la Bot API rechaza un envío
- **THEN** el log registra el fallo con su status y sin la URL que contiene el token
