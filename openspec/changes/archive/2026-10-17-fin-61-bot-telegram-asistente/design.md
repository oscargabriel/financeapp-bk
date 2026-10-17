# Design

## Context

El módulo `telegram` hoy recibe el update (`TelegramWebhookController` → `AtenderUpdatePort`), vincula
el chat (`ChatsVinculadosPort`, consultas propias sobre `finance.users`) y contesta con
`BotDeTelegramPort.enviar` (`sendMessage`, o su stub que solo loguea). Un chat vinculado recibe un
saludo fijo. El asistente (`ia`) expone `AssistantPort.atender(userId, canal, AssistantMessage)`, que ya
recibe el canal `TELEGRAM` (FIN-112) y una lista de archivos (FIN-113), y devuelve un `AssistantReply`
con el `intent` y su `group`, `report` o `balance`. Confirmar y descartar un grupo es del core:
`TransactionPort.approveGroup` y `rejectGroup` (FIN-58), con 404 sobre `groupId` si el grupo no tiene
movimientos del usuario y 409 sobre `status` si ninguno sigue pendiente.

Un módulo no importa a otro: `telegram` solo puede importar `compartido`.

## Goals / Non-Goals

**Goals:**
- El chat vinculado usa el mismo asistente que la web, sin una segunda copia de sus reglas.
- Confirmar o descartar desde el chat con los botones de Telegram, sin poder hacerlo dos veces.
- Que un reenvío de Telegram no registre ni conteste dos veces, también con más de una instancia.

**Non-Goals:**
- Lo de *Fuera de alcance* en `proposal.md`.
- Cambiar el asistente: ni su puerto, ni sus textos, ni sus límites.

## Decisions

### 1. Dos puertos de salida de `telegram`, cumplidos en `puente/`

`telegram` declara `AsistenteDelBotPort` (manda el texto y el archivo de un usuario, recibe una
`RespuestaDelAsistente` propia del módulo) y `GruposDelBotPort` (confirma o descarta un grupo de un
usuario). Los cumplen `AsistenteDelBotAdapter` (llama a `AssistantPort`) y `GruposDelBotAdapter`
(llama a `TransactionPort`), los dos en `puente/`, que hasta ahora solo conectaba `ia` con el core.

Descartado: que `telegram` llame directo a `ia` y al core. Es lo que `AGENTS.md` prohíbe, y el bot
quedaría acoplado a los records del asistente. También descartado: pasar confirmar y descartar por el
asistente, que no tiene esas operaciones y sumaría un salto sin nada que decidir.

**Cambia `AGENTS.md`**: la fila de `puente/` pasa a «Los adaptadores que conectan `ia` y `telegram`
con el core, y `telegram` con `ia`», y puede importar `core`, `ia` y `telegram`. Es tarea del change.

`RespuestaDelAsistente` es un `sealed interface` con `Texto` (aclaración o no soportado: solo el
`message`), `Registro` (el id del grupo y sus movimientos), `Movimientos` (el reporte), `Saldo` y
`ArchivoNoAdmitido`. El puente traduce `AssistantReply` a uno de ellos y nada más.

### 2. El tipo del archivo lo sigue decidiendo `ia`

El bot conoce el mime que declara Telegram (`image/jpeg` para una foto, el `mime_type` de la nota de
voz o del audio) y los bytes descargados; el puente arma el `AssistantFile` con `AssistantFileType.de`.
Si el tipo no se reconoce, o `AssistantFile` rechaza el tamaño, el puente devuelve `ArchivoNoAdmitido`
sin llamar al asistente.

Descartado: copiar en `telegram` la lista de tipos y firmas. Sería una tercera copia de una regla de
`ia` que ya tiene dos clientes. El tope de 5 MB sí se repite en `telegram` (`TOPE_ARCHIVO`), pero solo
para no descargar lo que Telegram ya declara más grande: quien rechaza sigue siendo `AssistantFile`.

### 3. Los updates atendidos se guardan en la base

Tabla `finance.telegram_updates (update_id BIGINT PRIMARY KEY, received_at TIMESTAMPTZ NOT NULL
DEFAULT now())`. Antes de nada, el caso de uso pide `UpdatesAtendidosPort.primeraVez(updateId)`: el
adapter (`TelegramUpdateR2dbcAdapter`) borra las filas de más de dos días e inserta con
`ON CONFLICT DO NOTHING`; si no insertó, el update ya se atendió y no se hace nada más.

Se registra **antes** de procesar: si el modelo falla a mitad, el bot ya pidió reintentar y el usuario
manda el mensaje de nuevo. Es «como mucho una vez», que es lo que pide el criterio.

Descartado:
- Un `Set` en memoria: se pierde al reiniciar y no se comparte si el servicio escala a más de una
  instancia.
- Guardar el último `update_id` y descartar los menores: Telegram puede entregar en paralelo y fuera de
  orden.
- Responder 200 al instante y procesar después: en Cloud Run la CPU se corta al responder.

Los dos días: Telegram guarda los updates no entregados hasta 24 horas, y tras una semana sin updates
elige el siguiente `update_id` al azar, así que una fila vieja podría tapar un update nuevo.

El webhook pasa a exigir `update_id` (400 sin él): sin él no hay cómo deduplicar, y Telegram siempre
lo manda.

### 4. Botones inline con el id del grupo

El registro sale con `inline_keyboard` de dos botones cuyo `callback_data` es `confirmar:<groupId>` y
`descartar:<groupId>` (46 bytes, bajo el tope de 64). El usuario sale siempre del chat vinculado, nunca
del botón: un cliente modificado puede mandar cualquier `callback_data`, y el core solo confirma un
grupo con movimientos de ese usuario (un grupo ajeno da 404 y se responde como «ya no están
pendientes»).

Tras confirmar o descartar, el bot quita los botones con `editMessageReplyMarkup` y manda el resultado
como mensaje nuevo. Dos toques rápidos llegan como dos updates distintos: el segundo encuentra el
grupo sin pendientes (409) y responde «ya no están pendientes». Toda respuesta a un botón empieza con
`answerCallbackQuery`, para que el cliente deje de mostrar la espera; si falla, se loguea y se sigue.

Descartado: editar el texto del mensaje original con el resultado. Deja un solo mensaje, pero si la
edición falla el usuario no ve nada; un mensaje nuevo es el mismo `sendMessage` que ya funciona.

`TecladoDelBot` pasa de `enum` a `sealed interface` con `NINGUNO`, `PEDIR_CONTACTO` y `QUITAR` como
constantes y un record `ConfirmarODescartar(UUID grupo)`, porque el teclado nuevo lleva un dato.

### 5. Qué es «ya no pendiente»

`GruposDelBotAdapter` traduce a `GrupoYaNoPendienteException` (de `telegram`) el 404 de `approveGroup`
o `rejectGroup` y su 409 con field `status`. Cualquier otro error (un 409 de una regla con el índice
del pendiente, la base caída) sale tal cual, y el bot responde «No pude guardar…» y deja los botones,
para que se pueda reintentar o resolver en la web.

### 6. Los textos los arma `telegram`

`TextosDelBot` (clase sin estado en `application/usecase`, como `ResolutorDeNombres` en `ia`) arma el
registro, el saldo y la lista: montos con el formato de `es-CO` y el código de la moneda, fechas
`dd/MM/yyyy` en la zona del usuario. La zona sale de `users.timezone`, que `usuarioDelChat` pasa a leer
(`UsuarioDelBot` suma `zona`). Sin `parse_mode`: el texto va plano, así que una descripción con `*` o
`<` no rompe el envío ni necesita escaparse.

La lista se corta en 20 movimientos y todo texto en 4096 caracteres, el tope de `sendMessage`: se
agregan líneas mientras quepan junto al cierre «y N más…».

Descartado: mandar el `message` del asistente también en el registro y en las consultas. Es un
resumen pensado para la web (sin tildes, sin la lista), y el criterio pide cada campo.

### 7. Descargar de la Bot API

`BotDeTelegramPort` suma `descargar(fileId)` (`getFile` y después `GET /file/bot<token>/<file_path>`),
`cerrarEspera(callbackId)` y `quitarBotones(chatId, messageId)`. La URL base sigue saliendo de
configuración. El `file_path` lo devuelve Telegram y se valida contra `^[A-Za-z0-9_\-/.]+$` sin `..`
antes de usarlo, para que la ruta no salga de `/file/bot<token>/`. La descarga se limita a
`TOPE_ARCHIVO` en memoria. Los errores se loguean como hoy: solo el status o el tipo, nunca la URL con
el token.

El stub (`StubTelegramBotAdapter`) devuelve unos bytes fijos según el `file_id`: `stub-jpeg` una
cabecera JPEG, `stub-ogg` una cabecera Ogg; cualquier otro falla como si Telegram no lo entregara. Así
`bruno/telegram/` ejerce la foto y la nota de voz con el asistente stub, que solo mira el texto.

### 8. Qué llega al asistente

Texto: `text`, o el `caption` de una foto, nota de voz o audio. Archivo: el último `PhotoSize` de
`photo` (Telegram los ordena de menor a mayor) como `image/jpeg`, o `voice`/`audio` con su `mime_type`.
`/start` (con o sin parámetro) no va al asistente: es lo primero que manda todo chat nuevo. Un mensaje
sin texto, archivo ni contacto es «contenido no aceptado» sin mirar qué trae, así que cualquier tipo
nuevo de Telegram cae ahí.

## Risks / Trade-offs

- [Telegram reintenta mientras el modelo piensa] → el update ya quedó registrado al entrar, así que el
  reintento no hace nada. El tiempo máximo es el del modelo (20 s) más la descarga (5 s).
- [Registrar antes de procesar pierde el mensaje si la instancia muere a mitad] → el usuario no recibe
  respuesta y lo vuelve a mandar. Se acepta frente a registrar dos veces.
- [`puente/` crece con un tercer módulo] → sigue solo traduciendo; la regla de qué es «ya no
  pendiente» es la del core (404 y 409 sobre `status`), copiada como traducción.
- [La tabla es nueva] → el `update/` solo la crea: no toca tablas existentes y no obliga a recargar la
  base local. Va antes del despliegue, porque la app nueva la usa en el primer update.
