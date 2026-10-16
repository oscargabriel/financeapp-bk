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
| `message.chat.type` | no | Texto. Solo `private` se atiende; cualquier otro, o ninguno, es un chat no privado (FIN-60) |
| `message.from.id` | no | Entero: quien escribe. Sin él, ningún contacto cuenta como propio |
| `message.contact.phone_number` | no | Texto. Telegram lo manda con o sin `+`; el bot se lo agrega si falta |
| `message.contact.user_id` | no | Entero: de quién es el contacto. Solo vincula si es el `from.id` |

**200 OK**, sin cuerpo. El bot contesta en el mismo chat con `sendMessage` de la Bot API, no en el
cuerpo de la respuesta. Ningún mensaje llega todavía al asistente: eso es FIN-61.

**Vincular el chat** (FIN-60). Un chat privado sin vincular recibe un botón «Compartir mi número»
(`request_contact`), que manda el contacto propio del que lo toca. Con ese contacto, el chat queda
vinculado al usuario si el teléfono está en la lista de admitidos y es el `phone` de exactamente un
usuario activo. La lista se mantiene a mano, como la del alta, y un teléfono de la lista no admite
el alta. Desde ahí, en cada mensaje, el chat cuenta como vinculado solo si su usuario sigue activo y
su `phone` sigue admitido: quitar el teléfono de la lista corta el acceso desde el siguiente
mensaje, y cambiar el `phone` desde el perfil desvincula el chat. Un usuario tiene un solo chat: el
último que vinculó. El resultado se ve en `telegramLinked` de [`GET /api/users/me`](usuarios.md).

**Solo chats privados.** En un grupo, supergrupo o canal el chat es de todos sus miembros, así que
el bot nunca lo vincula ni lo trata como vinculado.

| Mensaje | Respuesta del bot | Teclado |
|---|---|---|
| De un chat no privado, traiga o no un contacto | `Este bot solo atiende chats privados.` | — |
| De un chat vinculado, traiga o no un contacto | `Hola, <nombre>. Este chat ya está vinculado a tu cuenta de FinanceApp.` | — |
| Sin contacto, de un chat sin vincular (texto, foto, audio) | `Este chat todavía no está vinculado a una cuenta de FinanceApp. Comparte tu número con el botón de abajo para vincularlo.` | Botón «Compartir mi número» |
| Con un contacto ajeno (sin `user_id`, o distinto del `from.id`) | `Solo puedo vincular este chat con tu propio número. Compártelo con el botón de abajo.` | Botón «Compartir mi número» |
| Con el contacto propio y un teléfono que no vincula | `No pude vincular este chat con ese número.` | Quita el botón |
| Con el contacto propio y un teléfono que vincula | `Hola, <nombre>. Este chat quedó vinculado a tu cuenta de FinanceApp.` | Quita el botón |

«Un teléfono que no vincula» es cualquiera de estos casos, con el mismo texto para no revelar cuál:
no tiene formato E.164 tras agregarle el `+`, no está admitido, no es el `phone` de ningún usuario
activo, o es el de más de uno. Los teléfonos no quedan en los logs.

- Si la base no responde, el bot no contesta y el webhook responde 200 igual. El fallo queda en el
  log con el tipo de la excepción.
- Si Telegram no acepta ese mensaje o no responde, el webhook responde 200 igual: Telegram reintenta
  todo update que no recibe 2xx. El fallo queda en el log con su status.
- Un update repetido (el mismo `update_id`) se atiende otra vez: no hay deduplicación.

| Status | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `JSON_PARSING_ERROR` | `body` | El cuerpo no es JSON |
| 400 | `VALIDATION_ERROR` | `message.chat` o `message.chat.id` | Llega `message` sin chat, o el chat sin id |
| 401 | `UNAUTHENTICATED` | `authorization` | Falta la cabecera del secreto o no coincide. Lleva `WWW-Authenticate: Telegram` |
