# Proposal

Origen: FIN-112, del epic FIN-11 (Asistente y Telegram). Bloquea a FIN-113, FIN-115 y FIN-116
(Frontend: Pendientes muestra «Desde el chat») y a FIN-61 (el bot, que va a llamar al puerto ya
cambiado).

## Why

La web va a tener un chat con el asistente sobre el mismo `POST /api/assistant/messages`, y todo lo
que ese endpoint registra entra hoy con `origin: TELEGRAM`. Pendientes no podría distinguir lo del
chat web de lo del bot, y cuando FIN-61 conecte el bot al mismo puerto, los dos canales quedarían
mezclados para siempre en la base.

Decisiones del epic que aplica:

- «Lo que registra el chat web tiene su propio `origin` y en Pendientes muestra «Desde el chat»,
  también en el filtro por canal. El bot sigue con `TELEGRAM`» (16-10-2026).
- «La web tiene un chat con el asistente que usa el mismo `POST /api/assistant/messages` [...]. Va
  primero que el bot (FIN-61), que se hace sobre el puerto ya cambiado» (16-10-2026): el puerto
  recibe el canal desde este change.
- «Un movimiento queda pendiente porque no se ha confirmado, no por su origen» (13-10-2026):
  `WEB_CHAT` no decide el estado.
- «La demo trae pendientes de cada origen» (15-10-2026): la demo suma pendientes `WEB_CHAT`.

## What Changes

- **Contrato**: el valor nuevo de `origin` es `WEB_CHAT`. `WEB` ya son los formularios de la web y
  `CHAT` a secas también describiría a Telegram; `WEB_CHAT` dice las dos cosas y cabe en la columna
  (`VARCHAR(10)`).
- `POST /api/assistant/messages`, en JSON y en multipart, registra con `"origin": "WEB_CHAT"`. Así
  sale en `GET /api/transactions/pending` y en las respuestas que traen `origin` (aprobación uno a
  uno y por grupo, modificación), y se conserva al aprobar.
- El puerto de entrada del asistente recibe el canal (`WEB_CHAT` o `TELEGRAM`) y lo pasa hasta el
  alta. El controlador HTTP pasa siempre `WEB_CHAT`; el bot de FIN-61 pasará `TELEGRAM`. Hasta
  FIN-61, ningún endpoint produce `TELEGRAM`: los pendientes `TELEGRAM` que ya hay en la base, en los
  datos de prueba y en la demo siguen igual.
- La base admite `WEB_CHAT`: `ck_transactions_origin` en `schema.sql` y un update nuevo en
  `docs/database/update/`, que en Neon va **antes** del despliegue, porque la app nueva lo inserta.
- `demo-data.sql` deja a `demo@` y a `dev@` un grupo pendiente `WEB_CHAT` junto a los dos de
  Telegram.
- `docs/api/transacciones.md` agrega la fila de `WEB_CHAT` y cambia la de `TELEGRAM` al bot;
  `docs/api/asistente.md` dice qué origen pone cada canal. `contrato-api.md` no cambia: no hay
  endpoint nuevo ni cambia la descripción de ninguno.
- `bruno/assistant/` espera `WEB_CHAT` en lo que registra el endpoint.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: el requisito *Origen de un movimiento* admite `WEB_CHAT`, dice que lo produce el
  asistente por HTTP y que `TELEGRAM` queda para el bot; el escenario de aprobar lo del asistente
  pasa a `WEB_CHAT`.
- `asistente`: el requisito *Crear un movimiento desde el asistente* registra con
  `"origin": "WEB_CHAT"`.

## Fuera de alcance

- El bot que llama al asistente con `TELEGRAM`: es FIN-61.
- Mostrar «Desde el chat» en Pendientes y en su filtro: es FIN-116, del front.
- El chat web mismo y sus adjuntos: FIN-113, FIN-114, FIN-115 y FIN-117.
- Reetiquetar como `WEB_CHAT` los pendientes `TELEGRAM` que ya existen: el endpoint se ha usado solo
  para probar, y en la base no hay forma de saber por cuál canal entraron.

## Impact

- Código: `TransactionOrigin` (core), un enum de canal en el dominio de `ia`, `AssistantPort`,
  `AssistantUseCase`, `PendingMovementPort`, `AssistantController` y `PendienteDelAsistenteAdapter`
  (puente), con sus tests.
- Base: `schema.sql` y `docs/database/update/20261017_01_origen_chat_web.sql`. Solo amplía el check:
  no reinicia los datos de la base local. En Neon: update, despliegue y después recarga de la demo.
- Datos: `demo-data.sql`. `test-data.sql` no cambia: `bruno/assistant/` ya produce `WEB_CHAT` por
  HTTP contra la base.
- Bruno: `bruno/assistant/` (las aserciones de `origin` y la documentación de `folder.yml`).
  `bruno-personal/`: no cambia ningún request (ruta, cuerpo ni autenticación), así que no hay nada
  que replicar.
- Contrato: `docs/api/transacciones.md` y `docs/api/asistente.md`. Cambia el valor de `origin` en
  las respuestas de lo que registra el asistente: el front lo trae con `npm run sincronizar-contrato`.
