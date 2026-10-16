# Tasks

## 1. Teléfonos en la lista de admitidos y escenario de datos

- [x] 1.1 `docs/database/schema.sql`: el `CHECK` de `registration_allowlist` acepta correo, dominio o teléfono E.164 (`^\+[1-9][0-9]{6,14}$`), y los `COMMENT` de la tabla y de `entry` dicen que un teléfono solo sirve para el bot; el mismo cambio como `docs/database/update/20261015_01_telefonos_admitidos.sql`, con encabezado que diga que no reinicia los datos y que va antes o después del despliegue. Verificación: aplicar el update en la base local y correr la comparación de esquemas de `docs/database/modelo-datos.md` sin diferencias
- [x] 1.2 `docs/database/test-data.sql`: usuarios `telegram@financeapp.local` (`phone` `+573009990001`, sin chat) y `telegram-par1@` y `telegram-par2@financeapp.local` (los dos con `+573009990002`), con la contraseña del escenario; la lista admite esos dos teléfonos; `prueba@` conserva su chat `123456789` con su `phone` sin admitir. Verificación: `psql -v ON_ERROR_STOP=1 -f docs/database/cargar-datos-local.sql` sin errores
- [x] 1.3 `AGENTS.md`: `telegram*@financeapp.local` en la fila «Back» de la tabla de usuarios de *Base de datos*, y que `bruno/telegram/` solo pasa con `test-data.sql` recién cargado, como `bruno/pending/`

## 2. `telegramLinked` en el perfil y desvincular al cambiar el celular

Skills: `java-architect`, `bruno-cli`.

- [x] 2.1 Requests de Bruno escritos antes y fallando: `bruno/users/ver-perfil.yml` exige la clave `telegramLinked` en `false`; `bruno/telegram/perfil-chat-sin-admitir.yml` (perfil de `prueba@` con `telegramLinked` `false`, porque su `phone` no está admitido); `bruno/users/` o `bruno/auth/` un alta con correo no admitido y `phone` `+573009990001` que da 403 `REGISTRATION_NOT_ALLOWED`/`email`
- [x] 2.2 `UserProfile` y `UserProfileResponse` ganan `telegramLinked`; tests primero en los de `GetUserProfileUseCase`, `UpdateUserProfileUseCase` y el slice o IT del `UserController` que fijan la forma del perfil (las claves exactas, sin el id del chat)
- [x] 2.3 `UserR2dbcAdapter`: `PERFIL_ACTIVO` y el `RETURNING` de `ACTUALIZAR_PERFIL` calculan `telegram_linked` (chat no nulo y `phone` en la lista), con un comentario que nombra la copia del bot; `ACTUALIZAR_PERFIL` pone `telegram_chat_id` en `NULL` cuando el `phone` es distinto del guardado. Verificación: los requests de 2.1 en verde con `verificar-bruno.ps1 -RecargarDatos`
- [x] 2.4 `docs/api/usuarios.md`: `telegramLinked` en el ejemplo y su significado, y que cambiar el `phone` desvincula el chat; nota en la lista de admitidos de que un teléfono no admite el alta

## 3. El bot: teclado, chats privados y vinculación

Skills: `java-architect`, `java-exceptions`, `java-logging`, `bruno-cli`.

- [x] 3.1 Requests de `bruno/telegram/` escritos antes y fallando, en este orden: login de `telegram@` y de `telegram-par1@`; `webhook-contacto-ajeno` (contacto con el teléfono de `telegram@` y `user_id` distinto del `from.id`) seguido de `perfil-tras-contacto-ajeno` con `telegramLinked` `false`; `webhook-contacto-en-grupo` (contacto propio desde un chat `group`) y su perfil en `false`; `webhook-contacto-propio` (sin `+` en `phone_number`) y `perfil-vinculado` con `true`; `webhook-contacto-repetido` con el teléfono de los dos `telegram-par*` y el perfil de `telegram-par1@` en `false`; `parche-celular-desvincula` (`PATCH` del `phone` de `telegram@`) con `telegramLinked` `false` en la respuesta. Todos los webhooks dan 200; el `folder.yml` explica que la vinculación se observa por el perfil
- [x] 3.2 Dominio: `MensajeDeTelegram` con chat, si es privado, `from.id` y contacto opcional (`userId`, teléfono); `RespuestaDelBot(texto, teclado)` y el enum de teclado; `UsuarioDelBot(id, nombre)`; `ChatsVinculadosPort` (`usuarioDelChat(chatId)`, `vincular(chatId, telefono)`); `BotDeTelegramPort.enviar(chatId, RespuestaDelBot)`
- [x] 3.3 `AtenderUpdateUseCaseTest` primero, un caso por respuesta de la spec `telegram`: chat no privado (con y sin contacto, sin consultar el puerto de chats), vinculado (saludo con el nombre, también con un contacto), sin vincular sin contacto (texto y botón), contacto ajeno y sin `user_id` (sin llamar a `vincular`), teléfono mal formado (genérico sin llamar a `vincular`), `vincular` vacío (genérico), vinculado con éxito (saludo y quitar el botón, con el `+` agregado al teléfono), y errores de los dos puertos que terminan vacíos; un log capturado que no contiene el teléfono en el fallo y en el éxito
- [x] 3.4 `AtenderUpdateUseCase` en verde con 3.3
- [x] 3.5 `TelegramUpdateRequest` lee `message.from.id`, `message.chat.type` y `message.contact` (`phone_number`, `user_id`), sin validaciones nuevas; casos en `TelegramWebhookIT`: un contacto llega al caso de uso con sus campos, un chat sin `type` llega como no privado, y los casos de FIN-89 siguen en verde
- [x] 3.6 `TelegramBotApiAdapter` manda `reply_markup` según el teclado (botón `request_contact` con `one_time_keyboard` y `resize_keyboard`, `remove_keyboard`, o sin `reply_markup`); `TelegramBotApiAdapterTest` comprueba el cuerpo de los tres. `StubTelegramBotAdapter` loguea el texto y el teclado, y su test lo fija
- [x] 3.7 `TelegramChatR2dbcAdapter` (`telegram/infrastructure/adapter/out/persistence`): resolver el chat (usuario activo, no borrado, `phone` admitido, con comentario que nombra la copia del perfil) y vincular en dos pasos dentro de un `TransactionalOperator` (único usuario con el teléfono admitido; liberar el chat de las otras filas y guardarlo), logueando solo el tipo de una excepción. Verificación: los requests de 3.1 en verde con `verificar-bruno.ps1 -RecargarDatos`
- [x] 3.8 `docs/api/telegram.md`: los campos que se leen (`from.id`, `chat.type`, `contact`), la tabla de respuestas del bot con su teclado, que solo atiende chats privados y que el resultado de la vinculación se ve en `telegramLinked`; la fila del índice en `contrato-api.md` si su descripción cambia
- [x] 3.9 Replicar en `bruno-personal/` el webhook con un contacto (ruta, cabecera del secreto, cuerpo) y la clave `telegramLinked` donde se lea el perfil, sin ejecutarlo

## 4. Verificación

- [x] 4.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea
- [x] 4.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos de requests y aserciones y `check-saldos.sql` sin cuentas descuadradas
