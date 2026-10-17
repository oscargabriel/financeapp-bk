# Proposal

Origen: FIN-113, del epic FIN-11 (Asistente y Telegram). Depende de FIN-112, ya en `Por revisar`,
que cambió el mismo puerto y el mismo `docs/api/asistente.md`.

## Why

El chat web (FIN-115, FIN-117) va a mandar en un mismo mensaje texto, una nota de voz y varias fotos
de recibos, y la nota de voz va a salir del `MediaRecorder` del navegador. Hoy
`POST /api/assistant/messages` acepta en multipart un solo `file` de hasta 5 MB y rechaza
`audio/webm` y `audio/mp4`, así que el front no tiene a dónde mandar nada de eso.

Decisiones del epic que aplica:

- **16-10-2026**: un mensaje puede llevar texto, una nota de voz y hasta N imágenes juntos; N va en
  los properties del back, 3 por defecto, para ajustarlo tras probar. Publicar los límites para que
  el front los lea es de FIN-114, no de este change.
- **16-10-2026**: el chat web acepta el audio que graba el navegador: `audio/webm` en Chrome y
  Firefox, `audio/mp4` en Safari.
- **16-10-2026**: el front reduce las imágenes antes de enviarlas (el proxy de Vercel corta en
  4,5 MB). El back no reduce nada: valida y rechaza.
- **13-10-2026**: la imagen y el audio no se guardan, el audio vale como texto y la cuenta nunca sale
  de la imagen. Siguen igual con varios archivos.
- **13-10-2026**: un mensaje con varios movimientos se confirma o se descarta en un solo grupo. Tres
  recibos dan un grupo con tres movimientos.

## What Changes

- El multipart admite **varias partes `file`**: hasta N imágenes y como mucho un audio, en cualquier
  orden. Cada archivo sigue siendo de hasta 5 MB, y entre todos no pueden pasar de **10 MB**. Con un
  solo `file` todo funciona como hoy, y el JSON de solo texto no cambia.
- N sale de la propiedad `asistente.max-imagenes`, que en el servicio se lee de
  `ASISTENTE_MAX_IMAGENES` con 3 por defecto: se cambia en Cloud Run sin recompilar. Un valor menor
  que 1 impide arrancar.
- Con N+1 imágenes, dos audios, un archivo de tipo no admitido o vacío entre varios, o más de 10 MB
  en total: 400 `VALIDATION_ERROR` en `file`, sin llamar al modelo. El mensaje del error dice qué
  archivo falló cuando hay más de uno («Archivo 2: …»).
- Dos tipos de audio nuevos, con su firma validada como los demás: `audio/webm` (WebM, el de Chrome y
  Firefox) y `audio/mp4` (MP4, el de Safari). Un contenedor con pista de video declarado como audio
  se rechaza, para que «video no» siga valiendo.
- Todos los archivos van al modelo en el mismo turno que el texto, en el orden en que llegaron, y la
  instrucción del modelo agrega que cada comprobante es un movimiento aparte y que el texto y el
  audio valen para todos.
- **BREAKING** para un cliente que contara con el 400 de dos archivos: ahora dos imágenes son un
  mensaje válido. Solo lo usa el front, que todavía no manda archivos.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `asistente`: el mensaje multipart lleva varios archivos con su límite por tipo y su tope total,
  se aceptan `audio/webm` y `audio/mp4`, y lo que recibe el modelo con varios archivos.

## Impact

- `ia/domain/model`: `AssistantMessage` pasa de un archivo a una lista; `AssistantFileType` suma
  `WEBM` y `MP4` y distingue audio de imagen.
- `ia/infrastructure/adapter/in/web`: `LectorDeMensajeMultipart` y `AssistantController`, que lee la
  propiedad nueva.
- `ia/infrastructure/adapter/out/assistant`: `GeminiAssistantAdapter` manda todas las partes;
  `asistente/instrucciones.txt` suma la regla de varios comprobantes.
- `application.yaml` de `main` y de test.
- `docs/api/asistente.md`, `docs/verificaciones-manuales.md`, `docs/despliegue.md` (la variable nueva
  y, si la medición lo pide, el `GEMINI_TIMEOUT` nuevo) y `bruno/assistant/`.
- El tablero no cambia: FIN-114 publica estos límites y FIN-117 los usa desde el front.

## Fuera de alcance

- Publicar los límites en un endpoint: FIN-114.
- El chat del front, que adjunta y graba: FIN-117.
- Reducir o convertir archivos en el back. Si Gemini rechaza `audio/webm` o `audio/mp4`, se para y
  se decide con el usuario: no se transcodifica aquí.
- Álbumes de varias fotos en Telegram (fuera de alcance del epic, 16-10-2026): el bot de FIN-61
  sigue con un archivo por mensaje.
- Video (fuera de alcance del epic, 13-10-2026).
