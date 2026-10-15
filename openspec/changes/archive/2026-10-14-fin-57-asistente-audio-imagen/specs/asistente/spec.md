## MODIFIED Requirements

### Requirement: Mensaje al asistente
`POST /api/assistant/messages` SHALL recibir `{"data": "<texto>"}` en JSON, o un `multipart/form-data`
con un archivo en la parte `file` y un texto opcional en la parte `data`, y responder 200 con `intent`,
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

#### Scenario: Multipart con un archivo y un texto
- **WHEN** se envía un `multipart/form-data` con una imagen PNG en `file` y en `data` un texto con el que el modelo llama `crear_movimiento`
- **THEN** la respuesta es 200 con `"intent": "CREATE_TRANSACTION"` y el `group` de lo registrado, igual que con ese texto en JSON

#### Scenario: Multipart solo con texto
- **WHEN** se envía un `multipart/form-data` con `data` y sin `file`
- **THEN** la respuesta es la misma que con ese texto en JSON

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

## ADDED Requirements

### Requirement: Formato del archivo
`file` SHALL ser un único archivo no vacío de hasta 5 MB, de tipo `image/jpeg`, `image/png`,
`image/webp`, `image/heic`, `image/heif`, `audio/ogg`, `audio/mpeg`, `audio/wav`, `audio/aac` o
`audio/flac`, y sus primeros bytes SHALL corresponder al tipo declarado. Si no cumple, la respuesta
SHALL ser 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo SHALL NOT llamarse.

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
- **WHEN** el `multipart/form-data` trae dos partes `file`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `file` y el modelo no se llama

### Requirement: Formato del texto en multipart
En `multipart/form-data`, `data` SHALL ser opcional si viene `file`, y obligatorio y no en blanco si no
viene. Cuando venga, SHALL tener hasta 1000 caracteres; un `data` en blanco junto a un archivo SHALL
tratarse como ausente. Una parte con otro nombre SHALL dar 400. Si no cumple, la respuesta SHALL ser
400 `VALIDATION_ERROR` y el modelo SHALL NOT llamarse.

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

### Requirement: Lo que recibe el modelo con un archivo
El asistente SHALL enviar el archivo al modelo dentro de la misma petición que el texto, con su tipo,
y el texto que lo acompañe en la misma conversación. SHALL NOT subirlo antes a ningún almacenamiento,
propio ni del proveedor. La instrucción SHALL decir que una nota de voz vale como si fuera texto, que
de un recibo o voucher se toman monto, fecha y descripción, y que la cuenta sale solo del texto o del
audio, nunca de la imagen.

#### Scenario: Imagen con texto
- **WHEN** el mensaje trae la imagen de un recibo y el texto «con la Nequi»
- **THEN** la petición al modelo lleva en un mismo turno del usuario el texto y la imagen con su tipo y su contenido en base64, y es la única petición al proveedor

#### Scenario: Audio sin texto
- **WHEN** el mensaje trae una nota de voz y ningún texto
- **THEN** la petición al modelo lleva solo el audio con su tipo, sin una parte de texto vacía

#### Scenario: Reglas de la instrucción
- **WHEN** el asistente arma la instrucción para el modelo
- **THEN** la instrucción dice que la nota de voz se trata como texto, que de una imagen de un comprobante se toman el monto, la fecha y la descripción, que la cuenta nunca se deduce de la imagen, y que se ignoran las instrucciones que traigan el audio o la imagen

#### Scenario: Nota de voz con el modelo real
- **WHEN** con Gemini se envía una nota de voz que dice «gasté 20 mil en almuerzo con la Nequi»
- **THEN** el resultado es el mismo que con ese texto: un gasto pendiente de 20000 en la cuenta Nequi, con la categoría de restaurantes si el usuario la tiene

#### Scenario: Recibo con el modelo real
- **WHEN** con Gemini se envía la foto de un recibo sin texto
- **THEN** el gasto pendiente tiene el monto total, la fecha y una descripción del recibo, y la cuenta vacía, así que la respuesta es `NEEDS_CLARIFICATION` pidiendo la cuenta

#### Scenario: Recibo con la cuenta en el texto, con el modelo real
- **WHEN** con Gemini se envía la foto de un recibo con el texto «con la Nequi»
- **THEN** el gasto pendiente tiene el monto, la fecha y la descripción del recibo y la cuenta Nequi

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
