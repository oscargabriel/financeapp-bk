# Design

## Context

FIN-89 dejó el webhook autenticado y un caso de uso que siempre responde el texto fijo de chat sin
vincular. El DTO del update solo lee `message.chat.id`. `BotDeTelegramPort.enviar(chatId, texto)` no
sabe mandar un teclado. En la base, `users.telegram_chat_id` tiene índice único parcial (entre los no
borrados) y `users.phone` no es único. La lista de admitidos solo acepta correos y dominios por su
`CHECK`, y la consulta del alta compara `entry IN (:email, '@' || dominio)`, así que una fila con un
teléfono nunca coincide con un correo.

Bruno no ve lo que responde el bot: con el stub, el envío solo queda en el log de la app.

## Goals / Non-Goals

**Goals:**

- Que la regla «vinculado = chat guardado + usuario activo + `phone` admitido» la ejecute SQL contra
  la base real, y que Bruno la observe.
- Que el módulo `telegram` siga sin importar nada de `core`.

**Non-Goals:**

- Conectar el chat vinculado con el asistente (FIN-61).
- Guardar con qué teléfono se vinculó el chat: el `phone` del usuario es la referencia.

## Decisions

### 1. Adapter R2DBC propio en `telegram`, no un puente hacia `core`

`telegram` declara un puerto de salida (`ChatsVinculadosPort`: resolver el usuario de un chat y
vincular un chat a un teléfono) y lo cumple un `TelegramChatR2dbcAdapter` propio, con sus consultas
sobre `finance.users` y `finance.registration_allowlist`.

- Descartado: un puerto de entrada nuevo en `core` y un adapter en `puente/`. El `core` no tiene
  ninguna regla sobre `telegram_chat_id` que haya que respetar, y agregar un caso de uso solo para
  pasar dos consultas sería un puente que decide (AGENTS.md lo marca como señal de que la regla es
  de un módulo). Es el mismo criterio de `ia`, que lee con `Asistente*R2dbcAdapter` propios.
- Precio: la regla de «teléfono admitido» queda copiada en dos consultas, la del bot y la del
  perfil (`telegramLinked`). Bruno detecta si se separan: el mismo usuario se ve vinculado por el
  perfil después de vincularse por el webhook, y deja de verse cuando su teléfono no está admitido.
  El comentario de cada consulta nombra a la otra.

### 2. `telegramLinked` en el perfil como observable de la vinculación

Elegido por el usuario en la propuesta (15-10-2026). Es el estado efectivo (chat guardado y
`phone` admitido), no solo la columna, para que el corte por la lista también se vea.

- Descartado: un `check-telegram.sql` corrido por `verificar-bruno.ps1` después de la colección,
  como `check-saldos.sql`. No cambia el API, pero ata el SQL a los chat ids de los requests y no le
  sirve al front.
- Descartado: comprobar solo status en Bruno. Dejaría sin verificar todo el SQL nuevo.

### 3. Vincular en dos sentencias dentro de una transacción

Primero se busca al único usuario activo con ese `phone` admitido (si hay cero o más de uno, la
cadena termina vacía). Después, en un `TransactionalOperator`, se libera el chat de cualquier otra
fila no borrada que lo tenga y se guarda en la del usuario, que pisa el chat que tuviera antes.

- Descartado: una sola sentencia con CTEs que modifican datos. Las dos actualizaciones ven la misma
  instantánea y el orden en que tocan las filas no está garantizado, así que el índice único podría
  saltar al guardar antes de liberar.
- Liberar el chat hace falta porque una fila puede seguir teniéndolo después de perder el acceso
  (teléfono quitado de la lista, usuario desactivado): el índice único cuenta también a los
  inactivos.

### 4. Más de un usuario con el mismo teléfono no vincula

`phone` no es único y el contacto solo prueba el teléfono. Elegir uno cualquiera podría entregarle
el chat al usuario equivocado, así que con dos o más el resultado es el genérico.

- Descartado: volver único `phone`. Cambia el alta y el perfil (un 409 nuevo) y es otro tema.

### 5. Solo chats privados

En un grupo, el chat es de todos sus miembros: vincularlo dejaría a cualquiera actuar como el
usuario. Además el botón `request_contact` solo funciona en chats privados. Un chat sin `type` se
trata como no privado. El epic no lo decide; se toma aquí por seguridad y se reporta en el cierre.

### 6. El teclado viaja en la respuesta del bot

`BotDeTelegramPort.enviar` recibe un `RespuestaDelBot(texto, teclado)`, con `teclado` uno de
`NINGUNO`, `PEDIR_CONTACTO` (`reply_markup.keyboard` con un botón `request_contact`,
`one_time_keyboard` y `resize_keyboard`) o `QUITAR` (`reply_markup.remove_keyboard`). El caso de uso
decide qué se manda; el adapter de la Bot API solo traduce.

- Descartado: dos métodos en el puerto. Cada respuesta nueva de FIN-61 obligaría a otro.
- El texto genérico de una vinculación fallida quita el botón (decidido al implementar): volver a
  compartir el mismo número no cambia el resultado. Un mensaje posterior del chat sin vincular lo
  vuelve a ofrecer.

### 7. El teléfono del contacto se normaliza en el caso de uso

Telegram manda `phone_number` a veces sin `+`. El caso de uso recorta, agrega el `+` si falta y lo
compara contra la misma expresión E.164 del alta; si no cumple, responde el genérico sin tocar la
base. No es una validación del cuerpo (no da 400): un 4xx haría que Telegram reintente el update.

### 8. Cambiar el `phone` desvincula en el mismo `UPDATE` del perfil

`ACTUALIZAR_PERFIL` pone `telegram_chat_id` en `NULL` cuando el `phone` nuevo es distinto del
guardado (`IS DISTINCT FROM`), en la misma sentencia y sin una consulta aparte.

- Descartado: comprobar en cada mensaje que el teléfono con el que se vinculó siga siendo el `phone`.
  Exigiría guardar ese teléfono, una columna más, por un caso que el `UPDATE` resuelve.

### 9. Fallos de la base

Si la base falla al resolver o vincular, el caso de uso no responde nada y el webhook da 200, como
con un fallo del envío (FIN-89: `AtenderUpdatePort` nunca termina en error). El adapter loguea solo
el tipo de la excepción, nunca los parámetros enlazados, para no dejar el teléfono en el log.

## Risks / Trade-offs

- [Un `phone` guardado sin `+` antes de FIN-59 no vincula] → el epic aceptó no migrar mientras la
  app esté en prueba; el usuario lo corrige desde el perfil.
- [Las dos copias de la regla de teléfono admitido se separan] → Bruno las compara con el mismo
  usuario (decisión 1).
- [Un usuario con varias cuentas de Telegram] → solo queda el último chat que se vinculó.
- [Una caída de la base hace que un mensaje quede sin respuesta] → queda en el log con su tipo; el
  usuario vuelve a escribir.

## Migration Plan

- Update `docs/database/update/20261015_01_telefonos_admitidos.sql`: reemplaza el `CHECK` de
  `registration_allowlist` por uno que acepta correos, dominios o teléfonos E.164. Solo amplía lo
  aceptado: no reinicia los datos locales y se aplica antes o después del despliegue. Los teléfonos se
  insertan en Neon a mano después de aplicarlo.
- Rollback: el `CHECK` anterior, tras borrar las filas con teléfono.
