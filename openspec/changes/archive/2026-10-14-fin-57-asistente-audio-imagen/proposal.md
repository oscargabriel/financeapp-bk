# Proposal

Origen: FIN-57, del epic FIN-11 (Asistente y Telegram).

## Why

Hoy `POST /api/assistant/messages` solo entiende texto (`{"data": "..."}`, hasta 1000 caracteres). El
epic pide que el asistente también registre desde una nota de voz y desde la foto de un recibo o un
voucher, y el bot de Telegram (FIN-61, bloqueada por esta) necesita que el asistente ya los reciba
para pasarle lo que llegue del chat.

Decisiones de FIN-11 que aplica:

- Imagen y audio llegan en esta etapa (06-10-2026).
- El modelo multimodal extrae los datos del recibo o voucher, y la imagen no se guarda. El audio va
  directo al modelo y se trata como texto. Video no (13-10-2026).
- El asistente traduce el mensaje a cuenta, categoría, monto y descripción y lo devuelve para
  confirmar (13-10-2026). Con un archivo pasa igual: lo registrado queda pendiente en un grupo, como
  desde FIN-58.

Contrato decidido con el usuario el 14-10-2026, que cubre el primer criterio de la tarea: el archivo
viaja en `multipart/form-data` por la misma ruta, con un solo archivo por mensaje, de hasta 5 MB.

## What Changes

- `POST /api/assistant/messages` acepta además `multipart/form-data` con una parte `file` (un audio o
  una imagen) y una parte `data` opcional, con el texto que la acompaña. El cuerpo JSON de solo texto
  no cambia.
- Tipos admitidos: imágenes JPEG, PNG, WEBP, HEIC y HEIF, y audios OGG, MP3, WAV, AAC y FLAC. El tipo
  declarado tiene que estar en la lista y coincidir con la firma de los primeros bytes. Tope: 5 MB.
- Video, cualquier otro tipo, un archivo vacío, más de un archivo, un archivo por encima del tope o
  una parte desconocida dan 400 `VALIDATION_ERROR` sin llamar al modelo.
- El archivo va al modelo dentro de la misma petición, junto al texto si lo hay. La instrucción del
  modelo suma reglas: la nota de voz vale como si fuera texto; de la imagen se toman el monto, la
  fecha y la descripción, nunca la cuenta.
- El archivo no se guarda en la base ni en disco, y ni el archivo ni lo que diga aparecen en el log.
- `contrato-api.md` y `bruno/assistant/` documentan y prueban la variante multipart.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `asistente`: el mensaje puede traer un archivo de audio o de imagen, con sus reglas de formato, lo
  que se envía al modelo y la privacidad del archivo.

## Fuera de alcance

- Video (fuera de alcance en FIN-11, 13-10-2026).
- Varios archivos en un mismo mensaje: el usuario eligió uno.
- El bot de Telegram, que descarga el archivo del chat y lo pasa al asistente: FIN-61.
- Grabar o subir el archivo desde el front. El contrato queda listo para eso, pero ningún issue de
  `Frontend` lo pide todavía.
- Audio en formatos que Gemini no lista, como el `audio/webm` que graba el navegador. Si el front lo
  necesita, va a una tarea nueva.

## Impact

- `ia/`: puerto de entrada y puerto del modelo con el mensaje y su archivo, el controlador con la
  variante multipart, `GeminiAssistantAdapter` (partes `inlineData`), `StubAssistantAdapter` y
  `asistente/instrucciones.txt`.
- Errores: ninguno nuevo. El archivo usa `VALIDATION_ERROR` en el campo `file`.
- `docs/api/contrato-api.md`, sección `POST /api/assistant/messages`.
- `bruno/assistant/` con requests multipart y archivos de prueba pequeños, que se replican en
  `bruno-personal/`.
- `docs/verificaciones-manuales.md`: lo que hace el modelo real con un audio y una imagen no lo puede
  ejercer el stub.
- FIN-61 usa el puerto de entrada nuevo.
