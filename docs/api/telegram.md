# Contrato del API — Telegram

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

Este endpoint no es para el front: lo llama Telegram, que entrega por webhook los mensajes que
recibe el bot. Se documenta para quien registre el webhook o pruebe el bot.

### `POST /api/telegram/webhook`

Recibe un [update](https://core.telegram.org/bots/api#update) de la Bot API (FIN-89). **Secreto del
webhook**: Telegram repite en cada update, en la cabecera `X-Telegram-Bot-Api-Secret-Token`, el
`secret_token` con el que se registró el webhook. Ni la Basic ni el Bearer sirven aquí, y el secreto
no sirve en ninguna otra ruta (ver [generalidades.md](generalidades.md#secreto-del-webhook--telegramwebhook)).

```
X-Telegram-Bot-Api-Secret-Token: <secreto>
```

Del update solo se leen estos campos; el resto se ignora.

| Campo | Obligatorio | Formato |
|---|---|---|
| `update_id` | no | Entero |
| `message` | no | Objeto. Sin él (un mensaje editado, un callback) el update se acepta y no se responde nada |
| `message.chat.id` | sí, si llega `message` | Entero: el chat al que responde el bot |

**200 OK**, sin cuerpo. Un mensaje de un chat que no está vinculado a un usuario recibe del bot, en
ese chat, el texto `Este chat todavía no está vinculado a una cuenta de FinanceApp.`, y no llega al
asistente. Hasta FIN-60 no existe la vinculación, así que todo chat está sin vincular; el texto es
el mismo para un mensaje de texto y para uno sin texto (foto, audio, contacto).

- El bot responde con `sendMessage` de la Bot API, no en el cuerpo de la respuesta.
- Si Telegram no acepta ese mensaje o no responde, el webhook responde 200 igual: Telegram reintenta
  todo update que no recibe 2xx. El fallo queda en el log con su status.
- Un update repetido (el mismo `update_id`) se atiende otra vez: no hay deduplicación.

| Status | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `JSON_PARSING_ERROR` | `body` | El cuerpo no es JSON |
| 400 | `VALIDATION_ERROR` | `message.chat` o `message.chat.id` | Llega `message` sin chat, o el chat sin id |
| 401 | `UNAUTHENTICATED` | `authorization` | Falta la cabecera del secreto o no coincide. Lleva `WWW-Authenticate: Telegram` |
