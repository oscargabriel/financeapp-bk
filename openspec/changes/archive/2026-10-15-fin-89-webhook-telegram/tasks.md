# Tasks

## 1. Configuración del bot

Skills: `java-security`.

- [x] 1.1 `application.yaml`: bloque `telegram` con `proveedor` (`${TELEGRAM_PROVEEDOR:telegram}`), `bot-token` (`${TELEGRAM_BOT_TOKEN}`), `webhook-secret` (`${TELEGRAM_WEBHOOK_SECRET}`), `base-url` (`${TELEGRAM_BASE_URL:https://api.telegram.org}`) y `timeout` (`${TELEGRAM_TIMEOUT:5s}`); test primero en `CloudRunConfigTest`: las dos credenciales sin default y el resto con su default
- [x] 1.2 `src/test/resources/application.yaml`: el mismo bloque con un secreto de pruebas válido y `base-url` a `localhost:65535`; duplicar el secreto en un `TelegramMother` de `telegram/support/`. Se verifica con la suite arrancando sin `PlaceholderResolutionException`
- [x] 1.3 `application-local.yaml` y `application-prod.yaml` (sin versionar): las claves `telegram.bot-token` y `telegram.webhook-secret`; `bruno/.env`: `TELEGRAM_WEBHOOK_SECRET` con el mismo valor que el local; `bruno/environments/local.yml`: variable `telegramSecret` desde `process.env`, `secret: false` como `basicPass`

## 2. La cadena del secreto

Skills: `java-security`, `java-exceptions`, `bruno-cli`.

- [x] 2.1 Requests de `bruno/telegram/` escritos antes y fallando: `webhook-sin-secreto` (401), `webhook-secreto-equivocado` (401), `webhook-con-basic` (401), `webhook-con-bearer` (401), `status-con-secreto` y `cuentas-con-secreto` (401), con `folder.yml` que explique por qué el folder no hereda auth
- [x] 2.2 Tests de integración (`TelegramWebhookIT` en `telegram/infrastructure/adapter/in/web`, `@MockitoBean` del `BotDeTelegramPort`): sin cabecera y con valor equivocado dan 401 `UNAUTHENTICATED`/`authorization` y el puerto no se llama; Basic y Bearer válidos contra el webhook dan 401; en `JwtSecurityIT`, el secreto contra `/status` y `/accounts` da 401
- [x] 2.3 Tercera cadena en `SecurityConfig` (órdenes 0, 1, 2), convertidor de la cabecera y comparación en tiempo constante, entry point con esquema `Telegram`, excepción con mensaje fijo; validación del formato del secreto con un test unitario de `SecurityConfig` (o de la clase que la haga) para corto, carácter inválido y válido, comprobando que el mensaje no lleva el valor. Verificación: 2.2 en verde

## 3. El webhook y la respuesta fija

Skills: `java-architect`, `java-exceptions`, `bruno-cli`.

- [x] 3.1 Requests de `bruno/telegram/` escritos antes y fallando: `webhook-mensaje-texto` (200), `webhook-mensaje-sin-texto` (200), `webhook-sin-mensaje` (200), `webhook-no-json` (400 `JSON_PARSING_ERROR`/`body`), `webhook-mensaje-sin-chat` (400 `VALIDATION_ERROR`/`message.chat`)
- [x] 3.2 Dominio: `MensajeDeTelegram`, `AtenderUpdatePort`, `BotDeTelegramPort`; `AtenderUpdateUseCaseTest` primero: envía el texto fijo al chat del mensaje, y un error del puerto termina la cadena vacía sin propagarse
- [x] 3.3 `AtenderUpdateUseCase` (`@Service`) en verde con 3.2
- [x] 3.4 `TelegramWebhookController` en `POST /telegram/webhook` con `TelegramUpdateRequest` (`@Valid`, snake_case con `@JsonProperty`); casos en `TelegramWebhookIT`: mensaje de texto y sin texto llaman al puerto con el chat y responden 200, sin mensaje responde 200 sin llamarlo, no-JSON y sin chat dan 400 con su code y field, y un error del puerto responde 200
- [x] 3.5 `TelegramBotApiAdapter` con `TelegramBotApiAdapterTest` contra un `HttpServer` del JDK: ruta `/bot<token>/sendMessage`, cuerpo `chat_id` y `text`; con status de error y sin servidor, la cadena falla y el log capturado no contiene el token
- [x] 3.6 `StubTelegramBotAdapter` (`@Profile("!prod")`, `telegram.proveedor=stub`) con `StubTelegramBotAdapterTest`; `verificar-bruno.ps1` exporta `TELEGRAM_PROVEEDOR=stub` junto a los otros tres. Verificación: los requests de 2.1 y 3.1 en verde con `verificar-bruno.ps1 -Objetivo telegram`
- [x] 3.7 Replicar en `bruno-personal/` un request del webhook con el secreto (ruta, cabecera, cuerpo), sin ejecutarlo

## 4. Documentación

- [x] 4.1 `docs/api/telegram.md` (ruta, autenticación, cuerpo, respuestas) y su fila en `contrato-api.md`; en `generalidades.md`, la autenticación del webhook junto a Basic y Bearer
- [x] 4.2 `docs/despliegue.md`: los dos secretos en *Secretos*, `TELEGRAM_PROVEEDOR`/`TELEGRAM_BASE_URL` si hace falta en *Variables*, y una sección para registrar el webhook (`setWebhook` con `secret_token`, `getWebhookInfo`, `deleteWebhook`) sin dejar el token en el historial de la terminal, y su rotación
- [x] 4.3 `AGENTS.md`: módulo `telegram` en la tabla, tres cadenas en *Configuración y arranque*, las dos variables en la lista sin default, las claves del perfil local y `TELEGRAM_PROVEEDOR=stub` en el párrafo de stubs de `verificar-bruno.ps1`

## 5. Verificación

- [x] 5.1 `.\gradlew.bat build` en verde, con el conteo de tests y la cobertura de línea
- [x] 5.2 `pwsh -NoProfile -File .claude/scripts/verificar-bruno.ps1 -RecargarDatos` en verde, con los conteos de requests y aserciones
