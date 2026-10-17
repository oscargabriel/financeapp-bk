## MODIFIED Requirements

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

## ADDED Requirements

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
