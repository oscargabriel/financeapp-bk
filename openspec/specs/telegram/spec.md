# telegram Specification

## Purpose

El bot de Telegram del lado del API: cómo entran los updates de Telegram, cómo se autentican y qué
recibe cada chat según esté vinculado o no a un usuario.

## Requirements

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
Un mensaje de un chat privado que no está vinculado a un usuario, y que no trae un contacto, SHALL
recibir del bot, en ese mismo chat, el texto «Este chat todavía no está vinculado a una cuenta de
FinanceApp. Comparte tu número con el botón de abajo para vincularlo.», con un botón «Compartir mi
número» que pide el contacto propio. El mensaje no SHALL llegar al asistente ni al modelo.

#### Scenario: Mensaje de texto de un chat sin vincular
- **WHEN** llega, con el secreto correcto, un update con un mensaje de texto del chat privado 123456
  sin vincular
- **THEN** responde 200
- **AND** el bot envía al chat 123456 el texto fijo con el botón para compartir el contacto
- **AND** el asistente no recibe el mensaje

#### Scenario: Mensaje sin texto de un chat sin vincular
- **WHEN** llega, con el secreto correcto, un update con un mensaje sin texto ni contacto (una foto,
  un audio) del chat privado 123456 sin vincular
- **THEN** responde 200
- **AND** el bot envía al chat 123456 el texto fijo con el botón para compartir el contacto

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

### Requirement: El bot solo atiende chats privados
Un mensaje de un chat que no es privado (un grupo, un supergrupo, un canal), o cuyo chat no dice su
tipo, SHALL recibir el texto «Este bot solo atiende chats privados.», sin botón, y no SHALL vincular
el chat aunque traiga un contacto, ni tratarlo como vinculado aunque lo esté.

#### Scenario: Mensaje de un grupo
- **WHEN** llega un update con un mensaje de texto de un chat de tipo `group`
- **THEN** responde 200
- **AND** el bot envía a ese chat el texto de solo chats privados

#### Scenario: Contacto propio compartido en un grupo
- **WHEN** llega un contacto propio, con un teléfono admitido de un usuario activo, desde un chat de
  tipo `supergroup`
- **THEN** responde 200
- **AND** el bot envía el texto de solo chats privados y el chat no queda vinculado

### Requirement: Vincular el chat con el contacto propio
Un chat privado sin vincular que comparte un contacto SHALL quedar vinculado al usuario cuando el
contacto es el propio del que escribe (su `user_id` es el `from.id` del mensaje), su teléfono, con
el `+` agregado si no lo trae, está en la lista de admitidos y es el `phone` de exactamente un
usuario activo. El bot SHALL responder «Hola, <nombre>. Este chat quedó vinculado a tu cuenta de
FinanceApp.» y quitar el botón.

#### Scenario: Contacto propio con teléfono admitido
- **WHEN** el usuario activo Ana, con `phone` `+573009990001` admitido, comparte su contacto propio
  desde el chat privado 777001 con `phone_number` `573009990001`
- **THEN** responde 200
- **AND** el chat 777001 queda vinculado a Ana y el bot le envía «Hola, Ana. Este chat quedó
  vinculado a tu cuenta de FinanceApp.» quitando el botón
- **AND** el perfil de Ana trae `telegramLinked` `true`

#### Scenario: El usuario ya tenía otro chat
- **WHEN** Ana, vinculada al chat 777001, comparte su contacto propio desde un chat privado nuevo
  777009
- **THEN** el chat 777009 queda vinculado a Ana y el 777001 deja de estarlo

#### Scenario: El chat estaba vinculado a un usuario que perdió el acceso
- **WHEN** el chat 777001 está guardado para un usuario cuyo `phone` ya no está admitido, y desde
  ese chat se comparte el contacto propio de otro usuario activo con teléfono admitido
- **THEN** el chat queda vinculado al segundo usuario y el primero ya no lo tiene

### Requirement: Un contacto ajeno no vincula
Si el contacto compartido no es el propio del que escribe (no trae `user_id`, o es distinto del
`from.id` del mensaje, como en un contacto reenviado o uno de la agenda), el bot SHALL responder
«Solo puedo vincular este chat con tu propio número. Compártelo con el botón de abajo.», con el botón
para compartir el contacto, y no SHALL vincular el chat ni consultar la lista de admitidos.

#### Scenario: Contacto reenviado
- **WHEN** desde el chat privado 777001, con `from.id` 777001, llega un contacto con `user_id`
  888002 y el teléfono admitido de un usuario activo
- **THEN** responde 200
- **AND** el bot envía el texto de contacto ajeno con el botón y el chat no queda vinculado

#### Scenario: Contacto sin user_id
- **WHEN** llega un contacto sin `user_id` a un chat privado sin vincular
- **THEN** el bot envía el texto de contacto ajeno con el botón y el chat no queda vinculado

### Requirement: Una vinculación fallida no revela el motivo
Si el contacto es el propio pero su teléfono no tiene formato E.164 tras agregarle el `+`, no está en
la lista de admitidos, no es el `phone` de ningún usuario activo o es el de más de uno, el bot SHALL
responder «No pude vincular este chat con ese número.», el mismo texto en todos los casos, y no
SHALL vincular nada.

#### Scenario: Teléfono no admitido
- **WHEN** llega el contacto propio con el teléfono de un usuario activo que no está en la lista
- **THEN** responde 200
- **AND** el bot envía el texto genérico y el chat no queda vinculado

#### Scenario: Teléfono admitido sin usuario
- **WHEN** llega el contacto propio con un teléfono admitido que no es el `phone` de ningún usuario
- **THEN** el bot envía el mismo texto genérico y el chat no queda vinculado

#### Scenario: Teléfono de un usuario inactivo
- **WHEN** llega el contacto propio con el teléfono admitido de un usuario desactivado
- **THEN** el bot envía el mismo texto genérico y el chat no queda vinculado

#### Scenario: Teléfono admitido de dos usuarios
- **WHEN** llega el contacto propio con un teléfono admitido que es el `phone` de dos usuarios activos
- **THEN** el bot envía el mismo texto genérico y ninguno de los dos queda vinculado

### Requirement: Un chat vinculado depende de la lista de admitidos
En cada mensaje, un chat SHALL contar como vinculado solo si el usuario que lo tiene está activo y su
`phone` sigue en la lista de admitidos. Si no, el mensaje SHALL atenderse como el de un chat sin
vincular, desde ese mismo mensaje.

#### Scenario: El teléfono sale de la lista
- **WHEN** el chat 777001 está vinculado a Ana, se quita su teléfono de la lista y llega un mensaje
  de texto de ese chat
- **THEN** el bot envía el texto fijo de chat sin vincular con el botón
- **AND** el perfil de Ana trae `telegramLinked` `false`

#### Scenario: El usuario se desactiva
- **WHEN** el chat 777001 está vinculado a un usuario que se desactiva y llega un mensaje de ese chat
- **THEN** el bot envía el texto fijo de chat sin vincular con el botón

### Requirement: Un chat vinculado recibe un saludo fijo
Hasta que el chat se conecte con el asistente, todo mensaje de un chat privado vinculado, traiga o no
un contacto, SHALL recibir «Hola, <nombre>. Este chat ya está vinculado a tu cuenta de FinanceApp.»,
sin botón, y no SHALL cambiar la vinculación.

#### Scenario: Mensaje de texto de un chat vinculado
- **WHEN** llega un mensaje de texto del chat 777001, vinculado a Ana
- **THEN** responde 200
- **AND** el bot envía «Hola, Ana. Este chat ya está vinculado a tu cuenta de FinanceApp.»

#### Scenario: Contacto de otro usuario en un chat vinculado
- **WHEN** desde el chat 777001, vinculado a Ana, se comparte el contacto propio con el teléfono
  admitido de otro usuario activo
- **THEN** el bot envía el saludo de chat vinculado a Ana y el chat sigue vinculado a Ana

### Requirement: Los teléfonos no aparecen en los logs
Ni el teléfono del contacto ni el `phone` del usuario SHALL aparecer en los logs de la aplicación,
tampoco cuando la vinculación falla o la base no responde.

#### Scenario: Vinculación fallida
- **WHEN** llega el contacto propio con un teléfono no admitido
- **THEN** ningún log de la petición contiene el teléfono

#### Scenario: Vinculación exitosa
- **WHEN** un chat se vincula con un teléfono admitido
- **THEN** ningún log de la petición contiene el teléfono
