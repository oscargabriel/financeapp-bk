# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
