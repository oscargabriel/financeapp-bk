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
Un update autenticado que no trae `message` ni `callback_query` (un mensaje editado, un cambio de
miembros) SHALL responder 200 sin enviar nada, para que Telegram no lo reintente.

#### Scenario: Update sin mensaje
- **WHEN** llega, con el secreto correcto, un update con `update_id` y sin `message` ni
  `callback_query`
- **THEN** responde 200
- **AND** no se envía ningún mensaje

#### Scenario: Mensaje editado
- **WHEN** llega, con el secreto correcto, un update con `update_id` y `edited_message`
- **THEN** responde 200
- **AND** no se envía ningún mensaje y el asistente no recibe nada

### Requirement: Cuerpo del update
Un update autenticado cuyo cuerpo no es JSON SHALL responder 400 con code `JSON_PARSING_ERROR` y
field `body`. El update SHALL traer `update_id`: sin él, 400 con code `VALIDATION_ERROR` y field
`updateId`. Si trae `message`, el mensaje SHALL traer `chat.id`: sin `chat`, 400 con code
`VALIDATION_ERROR` y field `message.chat`; con `chat` y sin `id`, el mismo error con field
`message.chat.id`. Si trae `callback_query`, SHALL traer su `id`: sin él, el mismo error con field
`callbackQuery.id`.

#### Scenario: Cuerpo que no es JSON
- **WHEN** llega, con el secreto correcto, un cuerpo que no es JSON
- **THEN** responde 400 con code `JSON_PARSING_ERROR` y field `body`

#### Scenario: Mensaje sin chat
- **WHEN** llega, con el secreto correcto, un update con `message` y sin `chat`
- **THEN** responde 400 con code `VALIDATION_ERROR` y field `message.chat`
- **AND** no se envía ningún mensaje

#### Scenario: Update sin update_id
- **WHEN** llega, con el secreto correcto, un update con un mensaje de texto y sin `update_id`
- **THEN** responde 400 con code `VALIDATION_ERROR` y field `updateId`
- **AND** no se envía ningún mensaje y el asistente no recibe nada

#### Scenario: Botón sin id
- **WHEN** llega, con el secreto correcto, un update con `callback_query` sin `id`
- **THEN** responde 400 con code `VALIDATION_ERROR` y field `callbackQuery.id`

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
Un chat privado vinculado que manda `/start`, con o sin parámetro, o que comparte un contacto,
propio o no, SHALL recibir «Hola, <nombre>. Este chat ya está vinculado a tu cuenta de FinanceApp.»,
sin botón. Ese mensaje no SHALL llegar al asistente ni cambiar la vinculación.

#### Scenario: Mensaje de texto de un chat vinculado
- **WHEN** llega el texto `/start` del chat 777001, vinculado a Ana
- **THEN** responde 200
- **AND** el bot envía «Hola, Ana. Este chat ya está vinculado a tu cuenta de FinanceApp.»
- **AND** el asistente no recibe el mensaje

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

### Requirement: El chat vinculado habla con el asistente
Un texto, una nota de voz, un audio o una foto, con o sin pie, de un chat privado vinculado SHALL
llegar al asistente como el usuario del chat y por el canal de Telegram: el texto o el pie como texto,
y la nota de voz, el audio o la foto en su tamaño más grande como archivo. Lo que registre SHALL quedar
con `origin` `TELEGRAM`. El archivo no SHALL guardarse.

#### Scenario: Texto que registra un gasto
- **WHEN** Ana, vinculada al chat 777001, escribe un mensaje que el asistente interpreta como un gasto
  en su cuenta Billetera
- **THEN** responde 200
- **AND** el gasto queda pendiente para Ana con `origin` `TELEGRAM`, y aparece en
  `GET /api/transactions/pending`

#### Scenario: Foto con pie
- **WHEN** Ana manda una foto de un recibo con un pie de foto
- **THEN** el asistente recibe el pie como texto y la foto más grande que mandó Telegram como imagen
  `image/jpeg`

#### Scenario: Nota de voz sin texto
- **WHEN** Ana manda una nota de voz sin pie
- **THEN** el asistente recibe solo el audio `audio/ogg`, sin texto

#### Scenario: Mensaje de un chat sin vincular
- **WHEN** llega un texto o una foto del chat privado 123456, sin vincular
- **THEN** el asistente no recibe nada y el bot envía el texto fijo de chat sin vincular

### Requirement: Lo registrado se muestra con Confirmar y Descartar
Cuando el asistente registra, el bot SHALL enviar cada movimiento con su tipo, cuenta (y cuenta
destino en una transferencia), categoría, monto con moneda, descripción y fecha en la zona del
usuario, seguido de «Si no los confirmas aquí, quedan en Pendientes de la web.», con dos botones,
«Confirmar» y «Descartar», que actúan sobre todo el grupo.

#### Scenario: Un gasto registrado
- **WHEN** el asistente registra un gasto de 45000 COP en Billetera, categoría Mercado, descripción
  «Almuerzo», hoy
- **THEN** el bot envía «Registré 1 movimiento sin confirmar:» y el gasto con esos datos, la fecha de
  hoy como `dd/mm/aaaa` y el monto como `45.000 COP`
- **AND** el mensaje lleva los botones Confirmar y Descartar del grupo

#### Scenario: Dos movimientos en un mensaje
- **WHEN** el asistente registra dos gastos de un mismo mensaje
- **THEN** el bot envía «Registré 2 movimientos sin confirmar:», los dos numerados, y un solo par de
  botones para los dos

#### Scenario: Lo que no se confirma en el chat
- **WHEN** el asistente registra un gasto y Ana no toca ningún botón
- **THEN** el gasto sigue en `GET /api/transactions/pending` y se puede confirmar desde la web

### Requirement: Confirmar el grupo desde el chat
Al tocar «Confirmar», el bot SHALL confirmar todo lo que siga pendiente de ese grupo para el usuario
del chat, quitar los botones del mensaje y responder «Listo, guardé N movimiento(s).». Si confirmar
falla por otra causa que no sea que el grupo ya no está pendiente, SHALL responder «No pude guardar
los movimientos ahora. Intenta de nuevo o revísalos en Pendientes de la web.» y dejar los botones.

#### Scenario: Confirmar un grupo pendiente
- **WHEN** Ana toca Confirmar en el mensaje de un grupo con un gasto pendiente
- **THEN** responde 200
- **AND** el gasto queda `CONFIRMED`, ya no aparece en los pendientes y mueve el saldo de su cuenta
- **AND** el bot quita los botones y envía «Listo, guardé 1 movimiento.»

#### Scenario: Una regla de la confirmación falla
- **WHEN** Ana toca Confirmar y la confirmación del grupo falla por una regla del alta (la cuenta se
  archivó)
- **THEN** el grupo sigue pendiente, el bot envía el texto de no pude guardar y los botones quedan

### Requirement: Descartar el grupo desde el chat
Al tocar «Descartar», el bot SHALL descartar todo lo que siga pendiente de ese grupo para el usuario
del chat, quitar los botones y responder «Descarté los movimientos. No se guardó nada.».

#### Scenario: Descartar un grupo pendiente
- **WHEN** Ana toca Descartar en el mensaje de un grupo con dos gastos pendientes
- **THEN** responde 200
- **AND** los dos gastos dejan de existir y no aparecen en los pendientes
- **AND** el bot quita los botones y envía el texto de descartados

### Requirement: Un botón solo actúa sobre un grupo pendiente del chat
Un botón de un grupo que ya no tiene pendientes del usuario del chat (ya se confirmó, se descartó, se
resolvió desde la web, o es de otro usuario) SHALL responder «Esos movimientos ya no están
pendientes.», quitar los botones y no cambiar nada. Un botón con datos que el bot no reconoce no SHALL
hacer nada más que cerrar la espera. En todos los casos el bot SHALL cerrar la espera del botón.

#### Scenario: Confirmar dos veces
- **WHEN** Ana toca Confirmar en un grupo que ya confirmó
- **THEN** responde 200
- **AND** el bot envía el texto de ya no están pendientes y los movimientos no cambian

#### Scenario: Grupo de otro usuario
- **WHEN** desde el chat de Ana llega un botón Confirmar con el id de un grupo pendiente de otro
  usuario
- **THEN** el bot envía el texto de ya no están pendientes y ese grupo sigue pendiente

#### Scenario: Datos desconocidos
- **WHEN** llega un botón cuyos datos no son de Confirmar ni de Descartar con un id de grupo
- **THEN** responde 200, el bot cierra la espera y no envía ningún mensaje

#### Scenario: Botón de un chat sin vincular
- **WHEN** llega un botón Confirmar de un chat privado que ya no está vinculado
- **THEN** el grupo no cambia y el bot envía el texto fijo de chat sin vincular

#### Scenario: Botón de un mensaje que Telegram ya no entrega
- **WHEN** llega un botón sin el mensaje al que pertenece
- **THEN** responde 200, el bot cierra la espera y no cambia nada

### Requirement: Aclaraciones y lo que el asistente no hace
Cuando el asistente pide una aclaración o responde que no puede hacer lo pedido, el bot SHALL enviar
el `message` del asistente tal cual, sin botones.

#### Scenario: Cuenta que no existe
- **WHEN** Ana pide registrar un gasto en una cuenta que no tiene
- **THEN** el bot envía la aclaración del asistente y no se crea ningún movimiento

#### Scenario: Mensaje que no es una función
- **WHEN** Ana escribe algo que el asistente no interpreta como registrar ni consultar
- **THEN** el bot envía el `message` de no soportado del asistente

### Requirement: Consultar el saldo desde el chat
Cuando el asistente responde un saldo, el bot SHALL enviar el rango, los ingresos, los gastos, el neto
del periodo y el neto desde siempre en la moneda del usuario, y cada cuenta con su nombre, su saldo y
su moneda.

#### Scenario: Saldo del mes
- **WHEN** Ana pide su saldo
- **THEN** el bot envía los totales del mes en curso y una línea por cuenta, como `Billetera: 120.000 COP`

### Requirement: Consultar movimientos desde el chat
Cuando el asistente responde una consulta de movimientos, el bot SHALL enviar el rango, cuántos son y
los totales, y una línea por movimiento con su fecha, tipo, monto con moneda, descripción, cuenta y
categoría, hasta 20. Si hay más, SHALL terminar con «y N más. La lista completa está en la web.».

#### Scenario: Pocos movimientos
- **WHEN** Ana pide sus gastos de la semana y son 3
- **THEN** el bot envía el resumen y las 3 líneas

#### Scenario: Más de 20
- **WHEN** la consulta devuelve 25 movimientos
- **THEN** el bot envía 20 líneas y «y 5 más. La lista completa está en la web.»

### Requirement: Ningún mensaje del bot pasa del límite de Telegram
Ningún mensaje del bot SHALL pasar de 4096 caracteres. Si una lista de movimientos, registrados o
consultados, no cabe, el bot SHALL mostrar los primeros que quepan y decir cuántos faltan; los botones
de un registro siguen actuando sobre todo el grupo.

#### Scenario: Registro largo
- **WHEN** el asistente registra 40 movimientos con descripciones de 255 caracteres
- **THEN** el bot envía un mensaje de hasta 4096 caracteres con los primeros, cuántos faltan, y los
  botones del grupo

### Requirement: Contenido que el bot no acepta
Un mensaje de un chat vinculado que no trae texto, nota de voz, audio, foto ni contacto (un video, un
documento, un sticker, una ubicación) SHALL recibir «Solo entiendo texto, notas de voz, audios y
fotos.», sin llamar al asistente.

#### Scenario: Video
- **WHEN** Ana manda un video
- **THEN** responde 200, el bot envía el texto de contenido no aceptado y el asistente no recibe nada

#### Scenario: Documento
- **WHEN** Ana manda un PDF como documento
- **THEN** el bot envía el texto de contenido no aceptado y el asistente no recibe nada

### Requirement: Archivo que el asistente no acepta
Una nota de voz, un audio o una foto de más de 5 MB, o cuyo contenido no es de un tipo que el
asistente admite, SHALL recibir «No pude leer ese archivo. Manda una foto o una nota de voz de hasta
5 MB.», sin llamar al modelo. Si Telegram declara un tamaño mayor, el bot no SHALL descargarlo.

#### Scenario: Nota de voz demasiado grande
- **WHEN** Ana manda una nota de voz con `file_size` de 6 MB
- **THEN** el bot envía el texto de archivo no admitido sin descargar el archivo

#### Scenario: Audio de un tipo no admitido
- **WHEN** Ana manda un audio cuyo contenido no es de un formato de audio admitido
- **THEN** el bot envía el texto de archivo no admitido y el modelo no recibe nada

### Requirement: Un fallo del asistente pide reintentar
Si el modelo falla o no responde, si Telegram no entrega el archivo, o si no se puede leer el chat o
registrar el update, el bot SHALL enviar «No pude procesar tu mensaje. Intenta de nuevo en un
momento.» y el webhook SHALL responder 200, sin crear nada. El texto del usuario no SHALL ir al log.

#### Scenario: El modelo falla
- **WHEN** Ana manda un texto y el modelo responde con error
- **THEN** responde 200, no se crea ningún movimiento y el bot envía el texto de reintentar

#### Scenario: Telegram no entrega el archivo
- **WHEN** Ana manda una foto y la Bot API falla al dar el archivo
- **THEN** responde 200 y el bot envía el texto de reintentar

### Requirement: Un update repetido no se procesa dos veces
Un update cuyo `update_id` ya se atendió en los dos últimos días SHALL responder 200 sin enviar nada
ni llamar al asistente, también si es un botón.

#### Scenario: El mismo mensaje dos veces
- **WHEN** llega dos veces el mismo update con un texto que registra un gasto
- **THEN** las dos respuestas son 200
- **AND** solo se crea un gasto y el bot envía un solo mensaje

#### Scenario: El mismo botón dos veces
- **WHEN** llega dos veces el mismo update de Confirmar
- **THEN** el grupo se confirma una vez y el bot responde una sola vez
