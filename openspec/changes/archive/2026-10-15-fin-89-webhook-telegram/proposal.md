# Proposal

Origen: FIN-89, del epic FIN-11 (*Asistente y Telegram*). Sale de partir FIN-60 el 14-10-2026:
esta tarea deja el webhook recibiendo updates autenticados, y FIN-60 se queda con la vinculación
del chat.

## Why

El bot de Telegram del epic va desplegado en Cloud Run y multiusuario, y Telegram entrega los
mensajes por webhook. Hoy no hay código de Telegram, y las dos cadenas de `SecurityConfig` no dejan
ninguna ruta pública: Telegram no puede presentar ni la Basic compartida ni un JWT, así que el
webhook necesita una autenticación propia antes de que FIN-60 y FIN-61 le cuelguen la vinculación y
el asistente.

Decisiones del epic que aplica:

- *Telegram se crea pensando en que va desplegado y con varios usuarios* (06-10-2026).
- *Webhook autenticado con el secreto de Telegram, porque el bot va en Cloud Run* (13-10-2026).

Decidido al proponer (15-10-2026, con el usuario): el bot responde llamando a `sendMessage` de la
Bot API con el token, no en el cuerpo de la respuesta del webhook; y el texto para un chat sin
vincular es genérico, sin anunciar la vinculación por contacto, que no existe hasta FIN-60.

## What Changes

- Endpoint nuevo `POST /api/telegram/webhook`, que recibe los updates de Telegram.
- Una tercera cadena de seguridad, solo para esa ruta, que exige la cabecera
  `X-Telegram-Bot-Api-Secret-Token` con el secreto del webhook. Sin ella o con otro valor, 401 sin
  procesar el update. La Basic y el Bearer no sirven ahí, y el secreto no sirve en ninguna otra ruta.
- Un mensaje de un chat sin vincular recibe una respuesta fija del bot y no llega al asistente ni al
  modelo. Mientras no exista la vinculación (FIN-60), todo chat está sin vincular.
- Un update sin mensaje (editado, callback, etc.) se acepta con 200 y se ignora.
- Módulo nuevo `telegram`, con su cliente de la Bot API y un stub fuera de `prod` para `bruno/`.
- `TELEGRAM_BOT_TOKEN` y `TELEGRAM_WEBHOOK_SECRET` se leen sin default; el secreto se valida al
  arrancar. Ninguno de los dos aparece en los logs.
- `docs/despliegue.md` explica cómo cargar los dos secretos en el servicio y registrar el webhook.
- Contrato (`docs/api/telegram.md`, índice y autenticación de `generalidades.md`) y colección
  `bruno/telegram/`.

## Fuera de alcance

- Vincular el chat por teléfono (FIN-60).
- Pasar texto, audio o foto al asistente, la confirmación y las consultas (FIN-61).
- Deduplicar updates por `update_id` ante reintentos de Telegram: con una respuesta fija, un
  reintento solo repite el texto. Si FIN-61 lo necesita, lo decide ahí.
- Registrar el webhook desde la app al arrancar: se registra a mano una vez (`docs/despliegue.md`).

## Capabilities

### New Capabilities

- `telegram`: el bot de Telegram del lado del API — cómo entran los updates, cómo se autentican y
  qué recibe un chat según su estado.

### Modified Capabilities

Ninguna.

## Impact

- Código: módulo nuevo `com.oscargabriel.financeapp.telegram`; `SecurityConfig` pasa de dos a tres
  cadenas.
- Configuración: `application.yaml`, el de test, `application-local.yaml` y `application-prod.yaml`
  (sin versionar), `verificar-bruno.ps1` (`TELEGRAM_PROVEEDOR=stub`) y `bruno/.env`.
- Despliegue: dos secretos nuevos en Secret Manager y el registro del webhook en Telegram.
- Documentación: `AGENTS.md` (tres cadenas, módulo `telegram`, variables sin default, claves del
  perfil local, stubs del script), `docs/api/` y `docs/despliegue.md`.
