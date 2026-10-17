# asistente Specification

## Purpose
El asistente recibe un mensaje en texto libre, una nota de voz o la foto de un recibo, como
llegarían de Telegram, y con un modelo de lenguaje decide si registrar un movimiento, consultar
movimientos o consultar el saldo del usuario del token. Lo que registra queda pendiente de
aprobación. Nació en FA-77; el audio y la imagen llegaron en FIN-57.

## Requirements

### Requirement: Mensaje al asistente
`POST /api/assistant/messages` SHALL recibir `{"data": "<texto>"}` en JSON, o un `multipart/form-data`
con uno o varios archivos, cada uno en una parte `file`, y un texto opcional en la parte `data`, y
responder 200 con `intent`, `message`, y los campos `group`, `report` y `balance`, de los que solo
SHALL venir el que corresponde al `intent` y los demás en `null`. El texto que el modelo escriba
SHALL NOT aparecer en la respuesta: `message` sale de textos fijos del back.

#### Scenario: Respuesta sin función
- **WHEN** el modelo responde solo texto, sin llamar ninguna función
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"`, un `message` que lista lo que el asistente sabe hacer, y `group`, `report` y `balance` en `null`

#### Scenario: Función desconocida
- **WHEN** el modelo llama una función que no es `crear_movimiento`, `consultar_movimientos` ni `consultar_saldo`
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"` y no se crea ni se consulta nada

#### Scenario: Ya no hay transaction
- **WHEN** el asistente responde cualquier `intent`
- **THEN** la respuesta no trae el campo `transaction`

#### Scenario: Multipart con un archivo y un texto
- **WHEN** se envía un `multipart/form-data` con una imagen PNG en `file` y en `data` un texto con el que el modelo llama `crear_movimiento`
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y el `group` de lo registrado, igual que con ese texto en JSON

#### Scenario: Multipart solo con texto
- **WHEN** se envía un `multipart/form-data` con `data` y sin `file`
- **THEN** la respuesta es la misma que con ese texto en JSON

#### Scenario: Multipart con varias imágenes, un audio y un texto
- **WHEN** se envía un `multipart/form-data` con tres imágenes PNG y un audio OGG, cada uno en su parte `file`, y en `data` un texto con el que el modelo no llama ninguna función
- **THEN** la respuesta es 200 con `"intent": "UNSUPPORTED"`, igual que con ese texto en JSON

### Requirement: Formato del mensaje
`data` SHALL ser obligatorio, no vacío ni en blanco, y de hasta 1000 caracteres. Si no cumple, la
respuesta SHALL ser 400 y el modelo SHALL NOT llamarse.

#### Scenario: data ausente
- **WHEN** el cuerpo es `{}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

#### Scenario: data en blanco
- **WHEN** el cuerpo es `{"data": "   "}`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

#### Scenario: data por encima del tope
- **WHEN** `data` tiene 1001 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

### Requirement: Asistente autenticado con JWT
El endpoint SHALL exigir el JWT de la API. Sin token, o con la credencial Basic compartida de
`/auth/*` y `/status`, SHALL responder 401 sin llamar al modelo.

#### Scenario: Sin token
- **WHEN** se envía el mensaje sin `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y `WWW-Authenticate: Bearer`

#### Scenario: Con la credencial Basic
- **WHEN** se envía el mensaje con la credencial Basic compartida
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization`

#### Scenario: Archivo sin token
- **WHEN** se envía un `multipart/form-data` con un archivo y sin `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y el modelo no se llama

#### Scenario: Archivo con la credencial Basic
- **WHEN** se envía un `multipart/form-data` con un archivo y la credencial Basic compartida
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y el modelo no se llama

#### Scenario: Varios archivos sin token
- **WHEN** se envía un `multipart/form-data` con dos imágenes y un audio y sin `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED` en el campo `authorization` y el modelo no se llama

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

### Requirement: Consultar movimientos desde el asistente
Con `consultar_movimientos`, el asistente SHALL responder `"intent": "LIST_TRANSACTIONS"` con un
`report` liviano del usuario del token para el rango y los filtros dados: `from`, `to`,
`currencyCode` (la moneda base del usuario), `income`, `expense`, `net` y `transactions`. Cada
elemento de `transactions` SHALL traer solo `id`, `type`, `amount`, `currencyCode`, `description`,
`occurredAt` en UTC y los nombres `account` y `category`. Los totales y la lista SHALL coincidir con
`GET /api/reports/transactions` con los mismos parámetros, sin los programados. Sin rango, SHALL
usar el mes en curso. Antes de leer, SHALL poner al día las series del usuario, igual que
`/reports`.

#### Scenario: Gastos de un rango
- **WHEN** el modelo llama `consultar_movimientos` con `desde`, `hasta` y `tipo` `EXPENSE`
- **THEN** la respuesta es 200 con `"intent": "LIST_TRANSACTIONS"` y un `report` cuyos `income`, `expense` y `net` son los totales `INCOME` y `EXPENSE` (0 si no hay) y el `net` de `GET /api/reports/transactions` con esos parámetros

#### Scenario: La lista es la del reporte sin los programados
- **WHEN** el asistente responde un `report`
- **THEN** sus `transactions` tienen los mismos `id`, en el mismo orden, que los movimientos con `"scheduled": false` de `GET /api/reports/transactions` con los mismos parámetros

#### Scenario: Sin rango
- **WHEN** el modelo no envía `desde` ni `hasta`
- **THEN** el `report` cubre del primer al último día del mes en curso

#### Scenario: Serie vencida
- **WHEN** el usuario tiene una serie activa con una ocurrencia vencida que todavía no se registró, y el modelo llama `consultar_movimientos` sobre un rango que la incluye
- **THEN** la ocurrencia aparece en `transactions` y cuenta en los totales, como en `GET /api/reports/transactions`

### Requirement: Consultar el saldo desde el asistente
Con `consultar_saldo`, el asistente SHALL responder `"intent": "GET_BALANCE"` con un `balance`
liviano del usuario del token para el rango dado, o el mes en curso sin rango: `from`, `to`,
`currencyCode`, `income`, `expense`, `net`, `allTimeNet` y `accounts`, cada cuenta con solo `name`,
`currencyCode` y `currentBalance`. Los valores SHALL coincidir con los de
`GET /api/reports/balance` con el mismo rango. Antes de leer, SHALL poner al día las series del
usuario.

#### Scenario: Saldo del mes
- **WHEN** el modelo llama `consultar_saldo` sin argumentos
- **THEN** la respuesta es 200 con `"intent": "GET_BALANCE"` y un `balance` cuyos `income`, `expense` y `net` son los de `period`, cuyo `allTimeNet` es `allTime.net`, y cuyas `accounts` tienen el `name`, `currencyCode` y `currentBalance` de las de `GET /api/reports/balance` sin parámetros

#### Scenario: Solo los campos livianos del saldo
- **WHEN** el asistente responde un `balance`
- **THEN** sus cuentas no traen `id`, `type`, `creditLimit` ni `availableCredit`

### Requirement: Falla del modelo
Si el modelo no responde dentro del tiempo límite, responde un error o devuelve algo que no se puede
interpretar, la respuesta SHALL ser 502 con el formato común de error y el código
`EXTERNAL_SERVICE_ERROR`, sin crear nada. Ni la respuesta ni el log SHALL incluir la API key, el
prompt, el texto del usuario ni el cuerpo devuelto por el modelo.

#### Scenario: Gemini responde 500
- **WHEN** la API de Gemini responde 500
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server` y no se crea ningún movimiento

#### Scenario: Gemini no responde a tiempo
- **WHEN** la API de Gemini no responde dentro del tiempo límite
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR`

### Requirement: Configuración del modelo sin default
La aplicación SHALL leer `GEMINI_API_KEY` y `GEMINI_MODEL` sin valor por defecto y SHALL NOT
arrancar sin ellas, sea cual sea el proveedor. El stub del modelo SHALL NOT existir con el perfil
`prod`.

#### Scenario: Despliegue sin la key
- **WHEN** el servicio arranca sin `GEMINI_API_KEY`
- **THEN** la aplicación no arranca

#### Scenario: Stub pedido en producción
- **WHEN** el servicio arranca con el perfil `prod` y `ASISTENTE_PROVEEDOR=stub`
- **THEN** la aplicación no arranca: el stub no existe en `prod` y el adapter de Gemini solo se activa con el proveedor `gemini`

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

### Requirement: Formato del archivo
Cada parte `file` SHALL ser un archivo no vacío de hasta 5 MB, de tipo `image/jpeg`, `image/png`,
`image/webp`, `image/heic`, `image/heif`, `audio/ogg`, `audio/mpeg`, `audio/wav`, `audio/aac`,
`audio/flac`, `audio/webm` o `audio/mp4`, y sus primeros bytes SHALL corresponder al tipo declarado.
Un `audio/webm` o `audio/mp4` con una pista de video SHALL rechazarse. Si un archivo no cumple, la
respuesta SHALL ser 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo SHALL NOT
llamarse.

#### Scenario: Video
- **WHEN** `file` se declara `video/mp4`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Otro tipo de archivo
- **WHEN** `file` se declara `application/pdf`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`

#### Scenario: El contenido no es del tipo declarado
- **WHEN** `file` se declara `image/jpeg` y sus bytes son los de un audio OGG
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Archivo por encima del tope
- **WHEN** `file` mide 5 MB más un byte
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Archivo justo en el tope
- **WHEN** `file` es una imagen JPEG de exactamente 5 MB
- **THEN** el mensaje llega al modelo

#### Scenario: Archivo vacío
- **WHEN** `file` no tiene bytes
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`

#### Scenario: Dos archivos
- **WHEN** el `multipart/form-data` trae dos partes `file`, cada una con una imagen PNG válida
- **THEN** el mensaje llega al modelo con las dos imágenes

#### Scenario: Audio grabado en Chrome o Firefox
- **WHEN** `file` se declara `audio/webm;codecs=opus` y sus bytes son los de un WebM de solo audio
- **THEN** el mensaje llega al modelo con el audio de tipo `audio/webm`

#### Scenario: Audio grabado en Safari
- **WHEN** `file` se declara `audio/mp4` y sus bytes son los de un MP4 de solo audio
- **THEN** el mensaje llega al modelo con el audio de tipo `audio/mp4`

#### Scenario: Video declarado como audio
- **WHEN** `file` se declara `audio/mp4` y sus bytes son los de un MP4 con una pista de video
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Matroska que no es WebM
- **WHEN** `file` se declara `audio/webm` y sus bytes son los de un Matroska cuyo tipo de documento no es `webm`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`

#### Scenario: Un archivo no admitido entre varios
- **WHEN** el `multipart/form-data` trae dos imágenes PNG y un PDF, cada uno en su parte `file`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`, cuya descripción dice que el que falló es el archivo 3, y el modelo no se llama

#### Scenario: Audio WebM y MP4 con el modelo real
- **WHEN** con Gemini se envía la nota «gasté 20 mil en almuerzo con la Nequi» grabada como `audio/webm` por Chrome, y después como `audio/mp4`
- **THEN** cada una da lo mismo que ese texto: un gasto pendiente de 20000 en la cuenta Nequi

### Requirement: Formato del texto en multipart
En `multipart/form-data`, `data` SHALL ser opcional si viene al menos un `file`, y obligatorio y no en
blanco si no viene ninguno. Cuando venga, SHALL tener hasta 1000 caracteres y SHALL venir una sola
vez; un `data` en blanco junto a un archivo SHALL tratarse como ausente. Una parte con otro nombre
SHALL dar 400. Si no cumple, la respuesta SHALL ser 400 `VALIDATION_ERROR` y el modelo SHALL NOT
llamarse.

#### Scenario: Solo el archivo
- **WHEN** el `multipart/form-data` trae `file` y no trae `data`
- **THEN** el mensaje llega al modelo solo con el archivo

#### Scenario: Ni texto ni archivo
- **WHEN** el `multipart/form-data` no trae `data` ni `file`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data`

#### Scenario: Texto por encima del tope junto a un archivo
- **WHEN** el `multipart/form-data` trae un archivo válido y `data` de 1001 caracteres
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data` y el modelo no se llama

#### Scenario: Parte desconocida
- **WHEN** el `multipart/form-data` trae una parte llamada `foto`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `foto` y el modelo no se llama

#### Scenario: Dos textos
- **WHEN** el `multipart/form-data` trae dos partes `data`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `data` y el modelo no se llama

### Requirement: Lo que recibe el modelo con un archivo
El asistente SHALL enviar todos los archivos al modelo dentro de la misma petición que el texto, cada
uno con su tipo y en el orden en que llegaron, y el texto que los acompañe en la misma conversación.
SHALL NOT subirlos antes a ningún almacenamiento, propio ni del proveedor. La instrucción SHALL decir
que una nota de voz vale como si fuera texto, que de un recibo o voucher se toman monto, fecha y
descripción, que cada comprobante es un movimiento aparte, que el texto y el audio valen para todos
los comprobantes, y que la cuenta sale solo del texto o del audio, nunca de la imagen.

#### Scenario: Imagen con texto
- **WHEN** el mensaje trae la imagen de un recibo y el texto «con la Nequi»
- **THEN** la petición al modelo lleva en un mismo turno del usuario el texto y la imagen con su tipo y su contenido en base64, y es la única petición al proveedor

#### Scenario: Audio sin texto
- **WHEN** el mensaje trae una nota de voz y ningún texto
- **THEN** la petición al modelo lleva solo el audio con su tipo, sin una parte de texto vacía

#### Scenario: Varias imágenes, un audio y un texto
- **WHEN** el mensaje trae dos imágenes, un audio y un texto
- **THEN** la petición al modelo lleva en un mismo turno del usuario el texto y después los tres archivos en el orden en que llegaron, cada uno con su tipo, y es la única petición al proveedor

#### Scenario: Reglas de la instrucción
- **WHEN** el asistente arma la instrucción para el modelo
- **THEN** la instrucción dice que la nota de voz se trata como texto, que de una imagen de un comprobante se toman el monto, la fecha y la descripción, que cada comprobante es un movimiento aparte con el texto y el audio aplicados a todos, que la cuenta nunca se deduce de la imagen, y que se ignoran las instrucciones que traigan el audio o la imagen

#### Scenario: Nota de voz con el modelo real
- **WHEN** con Gemini se envía una nota de voz que dice «gasté 20 mil en almuerzo con la Nequi»
- **THEN** el resultado es el mismo que con ese texto: un gasto pendiente de 20000 en la cuenta Nequi, con la categoría de restaurantes si el usuario la tiene

#### Scenario: Recibo con el modelo real
- **WHEN** con Gemini se envía la foto de un recibo sin texto
- **THEN** el gasto pendiente tiene el monto total, la fecha y una descripción del recibo, y la cuenta vacía, así que la respuesta es `NEEDS_CLARIFICATION` pidiendo la cuenta

#### Scenario: Recibo con la cuenta en el texto, con el modelo real
- **WHEN** con Gemini se envía la foto de un recibo con el texto «con la Nequi»
- **THEN** el gasto pendiente tiene el monto, la fecha y la descripción del recibo y la cuenta Nequi

#### Scenario: Tres recibos con la cuenta en el texto, con el modelo real
- **WHEN** con Gemini se envían las fotos de tres recibos distintos con el texto «con la Nequi»
- **THEN** la respuesta es `CREATE_TRANSACTION` con un solo grupo de tres gastos pendientes, uno por recibo, cada uno con el monto, la fecha y la descripción de su recibo y la cuenta Nequi

#### Scenario: Recibos y una nota de voz con el modelo real
- **WHEN** con Gemini se envían las fotos de dos recibos y una nota de voz que dice «los dos con la Nequi», sin texto
- **THEN** la respuesta es `CREATE_TRANSACTION` con un grupo de dos gastos pendientes en la cuenta Nequi, uno por recibo

### Requirement: El archivo no se guarda ni se registra
El archivo SHALL NOT guardarse en la base ni escribirse en disco en ningún momento de la petición. Ni
el archivo, ni su contenido en base64, ni lo que diga SHALL aparecer en el log, tampoco cuando el
modelo falla o cuando el archivo se rechaza.

#### Scenario: El modelo falla con un archivo
- **WHEN** se envía una imagen y Gemini responde 500
- **THEN** la respuesta es 502 con un error `EXTERNAL_SERVICE_ERROR` en el campo `server`, y ninguna línea del log contiene el contenido del archivo ni su base64

#### Scenario: Archivo rechazado
- **WHEN** se rechaza un archivo por su tipo o su tamaño
- **THEN** ninguna línea del log contiene su contenido

#### Scenario: Lo registrado desde una imagen
- **WHEN** el asistente registra un gasto a partir de una imagen
- **THEN** el movimiento guarda solo los campos del alta, como si viniera de un texto, y ningún endpoint devuelve la imagen

### Requirement: Cuántos archivos lleva un mensaje
Un mensaje multipart SHALL llevar como mucho N imágenes y un audio, en cualquier orden, y entre todos
sus archivos SHALL NOT pasar de 10 MB (10 485 760 bytes). Si no cumple, la respuesta SHALL ser 400
con un error `VALIDATION_ERROR` en el campo `file` y el modelo SHALL NOT llamarse. Un mensaje con
más partes `file` que N más una SHALL rechazarse sin leer su contenido.

#### Scenario: N imágenes
- **WHEN** con N en 3, el mensaje trae tres imágenes y ningún audio
- **THEN** el mensaje llega al modelo con las tres imágenes

#### Scenario: Una imagen de más
- **WHEN** con N en 3, el mensaje trae cuatro imágenes
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Dos audios
- **WHEN** el mensaje trae una imagen y dos audios
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: N imágenes y un audio
- **WHEN** con N en 3, el mensaje trae un audio y después tres imágenes
- **THEN** el mensaje llega al modelo con los cuatro archivos, en ese orden

#### Scenario: Por encima del tope total
- **WHEN** el mensaje trae tres imágenes que miden cada una menos de 5 MB y entre las tres más de 10 MB
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

#### Scenario: Justo en el tope total
- **WHEN** el mensaje trae dos imágenes de exactamente 5 MB cada una
- **THEN** el mensaje llega al modelo

#### Scenario: Demasiadas partes
- **WHEN** con N en 3, el mensaje trae seis partes `file`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`, y el contenido de la quinta parte y las siguientes no se lee

### Requirement: Máximo de imágenes configurable
N SHALL leerse de la configuración del servicio, con 3 si no se define, y SHALL poder cambiarse con
una variable de entorno sin recompilar. Un N menor que 1 SHALL impedir que la app arranque.

#### Scenario: Sin la variable
- **WHEN** el servicio arranca sin `ASISTENTE_MAX_IMAGENES`
- **THEN** un mensaje admite hasta 3 imágenes

#### Scenario: Con la variable
- **WHEN** el servicio arranca con `ASISTENTE_MAX_IMAGENES=5`
- **THEN** un mensaje admite hasta 5 imágenes, y con 6 la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file`

#### Scenario: Un máximo que no sirve
- **WHEN** la app arranca con `ASISTENTE_MAX_IMAGENES=0`
- **THEN** el contexto no levanta y el error dice que el máximo de imágenes tiene que ser 1 o más
