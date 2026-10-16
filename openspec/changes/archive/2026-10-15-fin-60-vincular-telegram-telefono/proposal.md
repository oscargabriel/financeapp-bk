# Proposal

Origen: FIN-60, del epic FIN-11 (Asistente y Telegram). Llega después de FIN-59 (`phone` en E.164)
y de FIN-89 (webhook autenticado y respuesta fija a un chat sin vincular), y deja el terreno para
FIN-61, que conecta el chat vinculado con el asistente.

## Why

Hoy todo chat de Telegram está sin vincular: `users.telegram_chat_id` existe y nadie lo llena, así
que el bot no sabe de qué usuario es un mensaje. Para que FIN-61 registre y consulte movimientos
desde el chat, el chat tiene que quedar atado a un usuario de forma que solo el dueño del teléfono
pueda hacerlo y que el acceso se corte desde la lista de admitidos.

## What Changes

- La lista de admitidos (`finance.registration_allowlist`) acepta también teléfonos E.164. Sirven
  solo para el bot: el alta web los ignora.
- Un chat privado sin vincular recibe el texto fijo de FIN-89 con un botón para compartir el
  contacto. Al compartirlo, el chat queda vinculado si el contacto es el propio del que escribe, su
  teléfono está admitido y es el `phone` de exactamente un usuario activo. El bot lo saluda por su
  nombre y quita el botón.
- Si el contacto no es el propio, el bot lo dice y vuelve a ofrecer el botón. Si el teléfono no está
  admitido, no es de ningún usuario activo o es de más de uno, responde un mensaje genérico que no
  dice cuál falló, y no vincula nada.
- En cada mensaje, un chat cuenta como vinculado solo si su usuario sigue activo y su `phone` sigue
  admitido: quitar el teléfono de la lista corta el acceso desde el siguiente mensaje.
- Un chat vinculado recibe por ahora un saludo fijo con el nombre: lo que hace con el mensaje llega
  en FIN-61.
- El bot solo atiende chats privados: en un grupo o canal responde que solo atiende chats privados
  y nunca vincula. Un grupo vinculado dejaría actuar como el usuario a todos sus miembros.
- `GET` y `PATCH /api/users/me` agregan `telegramLinked`: verdadero si hay un chat vinculado y el
  teléfono sigue admitido. Es lo que hace observable la vinculación desde Bruno, que no ve lo que
  responde el bot (decisión del usuario en la propuesta, 15-10-2026), y lo que el front podrá mostrar.
- Cambiar el `phone` desde el perfil desvincula el chat: el chat se verificó con el teléfono
  anterior.
- Los teléfonos no aparecen en los logs.

Decisiones del epic que aplica:

- «El chat se vincula compartiendo el contacto propio en Telegram. El teléfono tiene que estar en la
  lista de admitidos (que pasa a aceptar teléfonos, solo para el bot) y ser el `phone` de un usuario
  registrado, en formato internacional» (13-10-2026).
- «Telegram se crea pensando en que va desplegado y con varios usuarios» (06-10-2026): por eso un
  chat por usuario, y el bloqueo de los grupos.
- «Mientras la app esté en prueba, cambiar el formato del teléfono no necesita migración»
  (13-10-2026): un `phone` guardado antes de FIN-59 sin `+` no se corrige; ese usuario no puede
  vincular hasta que lo actualice desde el perfil.

## Fuera de alcance

- Lo que el bot hace con el mensaje de un chat vinculado (texto, audio, foto, confirmación,
  consultas): FIN-61.
- Desvincular el chat a pedido del usuario, desde el bot o desde el perfil, salvo por el cambio de
  `phone`.
- Mostrar `telegramLinked` en el front: si se quiere, es un issue `Frontend`.
- Mantener la lista de admitidos desde el API: sigue a mano con SQL.
- Video (fuera de alcance del epic).

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `telegram`: el chat sin vincular recibe el botón de contacto; la vinculación por el contacto
  propio, sus respuestas y su corte por la lista de admitidos; solo chats privados; teléfonos fuera
  de los logs.
- `usuarios`: la lista de admitidos acepta teléfonos y el alta los ignora; el perfil gana
  `telegramLinked`; cambiar el `phone` desvincula el chat.

## Impact

- Módulo `telegram`: el DTO del update lee `from.id`, `chat.type` y `contact`; el caso de uso decide
  la respuesta; un puerto de salida nuevo con su adapter R2DBC propio para resolver y vincular el
  chat; el puerto del bot manda también el teclado (pedir contacto o quitarlo).
- Módulo `core`: la consulta y el `UPDATE` del perfil calculan `telegramLinked` y limpian el chat al
  cambiar el `phone`; `UserProfile` y su respuesta ganan el campo.
- Base: el `CHECK` de `registration_allowlist` acepta teléfonos (`schema.sql` y un update nuevo, sin
  recarga de datos); `test-data.sql` agrega usuarios `telegram*@financeapp.local` y sus teléfonos
  admitidos.
- Contrato: `docs/api/telegram.md` (campos que se leen, respuestas del bot) y `docs/api/usuarios.md`
  (`telegramLinked`). El cambio del perfil es aditivo para el front.
- `bruno/telegram/` y `bruno/users/`, y su réplica en `bruno-personal/`.
