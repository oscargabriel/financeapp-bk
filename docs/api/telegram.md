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
| `update_id` | sí | Entero. Telegram siempre lo manda; sin él no hay cómo saber si el update ya se atendió (FIN-61) |
| `message` | no | Objeto. Sin él ni `callback_query` (un mensaje editado, un cambio de miembros) el update se acepta y no se responde nada |
| `message.chat.id` | sí, si llega `message` | Entero: el chat al que responde el bot |
| `message.chat.type` | no | Texto. Solo `private` se atiende; cualquier otro, o ninguno, es un chat no privado (FIN-60) |
| `message.from.id` | no | Entero: quien escribe. Sin él, ningún contacto cuenta como propio |
| `message.contact.phone_number` | no | Texto. Telegram lo manda con o sin `+`; el bot se lo agrega si falta |
| `message.contact.user_id` | no | Entero: de quién es el contacto. Solo vincula si es el `from.id` |
| `message.text`, `message.caption` | no | Texto. El pie de una foto, nota de voz o audio cuenta como su texto (FIN-61) |
| `message.photo[].file_id`, `.file_size` | no | La foto. Se toma el último tamaño, el más grande, como `image/jpeg` |
| `message.voice.file_id`, `.file_size`, `.mime_type` | no | La nota de voz. Sin `mime_type`, `audio/ogg` |
| `message.audio.file_id`, `.file_size`, `.mime_type` | no | El audio |
| `callback_query` | no | Objeto: el toque de un botón Confirmar o Descartar (FIN-61) |
| `callback_query.id` | sí, si llega `callback_query` | Texto: con él se cierra la espera del botón |
| `callback_query.message.message_id`, `.chat.id`, `.chat.type` | no | El mensaje del botón y su chat. Sin ellos el botón no hace nada más que cerrar la espera |
| `callback_query.data` | no | Texto: `confirmar:<groupId>` o `descartar:<groupId>` |

**200 OK**, sin cuerpo. El bot contesta en el mismo chat con `sendMessage` de la Bot API, no en el
cuerpo de la respuesta, en texto plano y sin pasar de 4096 caracteres.

**Un update se atiende como mucho una vez** (FIN-61). Antes de nada se anota su `update_id` en
`finance.telegram_updates`, que guarda los de los dos últimos días: un update repetido, también un
botón, responde 200 sin contestar nada ni llamar al asistente. Se anota antes de procesar, así que
si algo falla a mitad el usuario vuelve a mandar el mensaje.

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
| De un chat vinculado, con `/start` (con o sin parámetro) o con un contacto | `Hola, <nombre>. Este chat ya está vinculado a tu cuenta de FinanceApp.` | — |
| De un chat vinculado, con otro contenido | Ver *Hablar con el asistente* | — |
| Sin contacto, de un chat sin vincular (texto, foto, audio) | `Este chat todavía no está vinculado a una cuenta de FinanceApp. Comparte tu número con el botón de abajo para vincularlo.` | Botón «Compartir mi número» |
| Con un contacto ajeno (sin `user_id`, o distinto del `from.id`) | `Solo puedo vincular este chat con tu propio número. Compártelo con el botón de abajo.` | Botón «Compartir mi número» |
| Con el contacto propio y un teléfono que no vincula | `No pude vincular este chat con ese número.` | Quita el botón |
| Con el contacto propio y un teléfono que vincula | `Hola, <nombre>. Este chat quedó vinculado a tu cuenta de FinanceApp.` | Quita el botón |

«Un teléfono que no vincula» es cualquiera de estos casos, con el mismo texto para no revelar cuál:
no tiene formato E.164 tras agregarle el `+`, no está admitido, no es el `phone` de ningún usuario
activo, o es el de más de uno. Los teléfonos no quedan en los logs.

**Hablar con el asistente** (FIN-61). Un texto, una nota de voz, un audio o una foto de un chat
privado vinculado llegan al asistente como el usuario del chat y por el canal de Telegram: lo que
registra queda con `origin` `TELEGRAM`. El texto o el pie va como texto; la foto más grande, la nota
de voz o el audio, como archivo. El bot descarga el archivo de la Bot API (`getFile`) y no lo
guarda. El tipo y el tope de 5 MB los decide el asistente, como en
[`POST /api/assistant/messages`](asistente.md); si Telegram ya declara más de 5 MB, el bot ni lo
descarga.

| El asistente… | Respuesta del bot | Teclado |
|---|---|---|
| Registra | `Registré N movimiento(s) sin confirmar:` y cada uno numerado, con tipo, cuenta (`origen → destino` en una transferencia), categoría, monto (`45.000 COP`), descripción y fecha `dd/mm/aaaa` en la zona del usuario. Cierra con `Si no los confirmas aquí, quedan en Pendientes de la web.` | Botones «Confirmar» y «Descartar» del grupo |
| Consulta movimientos | El rango, cuántos son, ingresos, gastos y neto, y una línea por movimiento (fecha · tipo · monto · descripción · cuenta · categoría), hasta 20. Si hay más: `y N más. La lista completa está en la web.` | — |
| Consulta el saldo | El rango, ingresos, gastos, neto, neto desde siempre, y una línea por cuenta: `Billetera: 120.000 COP` | — |
| Pide una aclaración o no hace lo pedido | Su `message`, tal cual | — |

Si una lista no cabe en 4096 caracteres, el mensaje muestra los primeros que quepan y dice cuántos
faltan; los botones de un registro siguen actuando sobre todo el grupo.

| Mensaje de un chat vinculado | Respuesta del bot |
|---|---|
| Sin texto, nota de voz, audio, foto ni contacto (video, documento, sticker, ubicación) | `Solo entiendo texto, notas de voz, audios y fotos.` Sin llamar al asistente |
| Con un archivo de más de 5 MB o de un tipo que el asistente no admite | `No pude leer ese archivo. Manda una foto o una nota de voz de hasta 5 MB.` Sin llamar al modelo |
| Si el modelo falla, Telegram no entrega el archivo, o no se puede leer el chat o anotar el update | `No pude procesar tu mensaje. Intenta de nuevo en un momento.` Sin crear nada |

**Los botones Confirmar y Descartar.** Sus datos son `confirmar:<groupId>` y `descartar:<groupId>`.
El usuario sale siempre del chat vinculado, nunca del botón: el core solo confirma un grupo con
movimientos de ese usuario. Todo botón cierra primero la espera del cliente
(`answerCallbackQuery`).

| Botón | Respuesta del bot | Botones del mensaje |
|---|---|---|
| Confirmar, con el grupo pendiente | `Listo, guardé N movimiento(s).` | Los quita |
| Descartar, con el grupo pendiente | `Descarté los movimientos. No se guardó nada.` | Los quita |
| Con un grupo que ya no tiene pendientes del usuario (ya resuelto, o de otro) | `Esos movimientos ya no están pendientes.` | Los quita |
| Si confirmar o descartar falla por otra causa (una regla del alta, la base) | `No pude guardar los movimientos ahora. Intenta de nuevo o revísalos en Pendientes de la web.` | Los deja |
| De un chat no privado, o sin vincular | El texto de chat no privado o de chat sin vincular, sin tocar el grupo | — |
| Con datos que no son de Confirmar ni de Descartar, o sin su mensaje | Nada | — |

Quitar los botones usa `editMessageReplyMarkup`; si falla, el mensaje con el resultado sale igual.

- Si Telegram no acepta un mensaje o no responde, el webhook responde 200 igual: Telegram reintenta
  todo update que no recibe 2xx. El fallo queda en el log con su status, sin la URL con el token.
- Ni los teléfonos ni el texto del usuario quedan en los logs: un fallo deja solo el tipo de la
  excepción.

| Status | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `JSON_PARSING_ERROR` | `body` | El cuerpo no es JSON |
| 400 | `VALIDATION_ERROR` | `updateId` | Falta `update_id` |
| 400 | `VALIDATION_ERROR` | `message.chat` o `message.chat.id` | Llega `message` sin chat, o el chat sin id |
| 400 | `VALIDATION_ERROR` | `callbackQuery.id` | Llega `callback_query` sin `id` |
| 401 | `UNAUTHENTICATED` | `authorization` | Falta la cabecera del secreto o no coincide. Lleva `WWW-Authenticate: Telegram` |
