# Proposal

FIN-61, del epic FIN-11 (Asistente y Telegram).

## Why

El chat de Telegram ya se vincula a su usuario (FIN-60) y el asistente ya registra, consulta y agrupa lo
que registra para confirmarlo o descartarlo junto (FIN-57, FIN-58, FIN-112, FIN-113), pero el bot
todavía contesta un saludo fijo a todo. Este change une las piezas: lo que el usuario escribe, graba o
fotografía en su chat llega al asistente, y lo que el asistente registra se confirma o se descarta
desde el mismo chat.

Decisiones del epic que aplica:

- Telegram se crea pensando en que va desplegado y con varios usuarios; imagen y audio llegan en esta
  etapa (06-10-2026).
- Imagen: el modelo extrae los datos del recibo y la imagen no se guarda. Audio: va directo al modelo
  y se trata como texto. Video no (13-10-2026).
- Un movimiento queda pendiente porque no se ha confirmado, no por su origen (13-10-2026).
- El asistente devuelve lo registrado para confirmar; un mensaje puede traer varios movimientos, que se
  confirman o se descartan juntos, y al confirmar la respuesta dice que quedaron guardados
  (13-10-2026).
- Lo que no se confirma en el chat queda en los pendientes de la web (13-10-2026).
- Se puede consultar el saldo total y el de cada cuenta (13-10-2026).
- El modelo es uno solo para la web y Telegram, y el bot se hace sobre el puerto ya cambiado (16-10-2026).
- El bot sigue con el origen `TELEGRAM` (16-10-2026).

## What Changes

- Un texto, una nota de voz, un audio o una foto, con o sin pie de foto, de un chat privado vinculado
  llegan al asistente como el usuario de ese chat y con el canal `TELEGRAM`. El bot descarga el archivo
  de la Bot API y no lo guarda.
- Lo que el asistente registra se muestra en el chat (tipo, cuenta, categoría, monto con moneda,
  descripción, fecha) con dos botones, Confirmar y Descartar, que actúan sobre todo el grupo.
  Confirmar guarda y lo dice; Descartar no guarda nada y lo dice; en los dos casos los botones
  desaparecen. Un grupo que ya no está pendiente lo dice sin tocar nada.
- La aclaración y el «no entiendo» del asistente se responden con su `message`. El saldo se muestra
  con el total y cada cuenta; la consulta de movimientos, con la lista del rango, hasta un tope.
- Video, documentos, stickers y cualquier otro contenido reciben un texto fijo, sin llamar al modelo.
- Si el modelo o la Bot API fallan, el bot pide reintentar y el webhook responde 200.
- Un `update_id` que ya se atendió no se vuelve a procesar. Para eso el update **exige `update_id`**
  (400 sin él) y la base guarda los atendidos de los dos últimos días en una tabla nueva,
  `finance.telegram_updates`.
- El saludo fijo del chat vinculado queda solo para `/start` y para un contacto compartido.
- `puente/` pasa a conectar también `telegram` con `ia` y con el core.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `telegram`: el chat vinculado habla con el asistente; botones para confirmar o descartar el grupo;
  contenido no admitido; fallo del modelo; updates repetidos; `update_id` obligatorio; el saludo fijo
  queda para `/start` y el contacto; un update con botón ya no se ignora.

## Fuera de alcance

- Álbumes de varias fotos: el bot sigue con un archivo por mensaje (decisión del epic, 16-10-2026).
  Cada foto de un álbum llega como un update aparte y se atiende sola.
- Video (epic, 13-10-2026).
- La indicación «escribiendo…» (`sendChatAction`) mientras el modelo piensa.
- Conversación con memoria: cada mensaje sigue siendo independiente para el modelo.
- Registrar el webhook en Telegram (`setWebhook`) y crear el bot: va en FIN-134, con el despliegue.

## Impact

- Código: módulo `telegram` (dominio, caso de uso, DTO del update, adapter de la Bot API y su stub,
  adapter R2DBC nuevo) y dos adapters nuevos en `puente/`.
- Base: tabla nueva `finance.telegram_updates` en `schema.sql` y en un `update/`. No toca tablas
  existentes. `test-data.sql` le da una cuenta a `telegram@` y limpia los updates atendidos.
- API: `POST /api/telegram/webhook` exige `update_id` y atiende `callback_query`. Contrato en
  `docs/api/telegram.md` y su fila en `contrato-api.md`.
- `bruno/telegram/`: requests nuevos que registran, repiten, confirman y descartan, observados por los
  pendientes y los movimientos de `telegram@`.
- `AGENTS.md`: la fila de `puente/` en la tabla de módulos.
