# Design

## Context

`SecurityConfig` (en `compartido`) tiene dos cadenas: la Basic con un `securityMatcher` de rutas
fijas (`@Order(0)`) y la del JWT que recoge el resto (`@Order(1)`). No hay ruta pública. Telegram
solo puede autenticarse con la cabecera `X-Telegram-Bot-Api-Secret-Token`, que repite en cada update
el `secret_token` dado al registrar el webhook (1 a 256 caracteres de `A-Za-z0-9_-`).

Los clientes HTTP salientes del proyecto (`GeminiAssistantAdapter`, `GoogleIdTokenVerifierAdapter`,
`ExchangeRateApiAdapter`) siguen un mismo patrón: `WebClient` propio, URL base fija de
configuración, proveedor elegido por propiedad y un stub con `@Profile("!prod")` que
`verificar-bruno.ps1` activa. Sus tests levantan un `HttpServer` del JDK como servidor falso.

## Goals / Non-Goals

**Goals:**
- Que nada de lo que FIN-60 y FIN-61 cuelguen del webhook pueda ejecutarse sin el secreto.
- Dejar listo el cliente de la Bot API que FIN-61 reutiliza (`sendMessage`, y después `getFile`).

**Non-Goals:**
- Tabla o puerto de vinculación: los crea FIN-60. Aquí no hay forma de saber si un chat está
  vinculado, y todos reciben la respuesta fija.

## Decisions

### 1. Módulo nuevo `telegram`, sin dependencia de `ia` ni de `core`

`com.oscargabriel.financeapp.telegram` con `domain/`, `application/` e `infrastructure/`, como pide
*Módulos* de `AGENTS.md`. En esta tarea no importa nada de otro módulo: el caso de uso
(`AtenderUpdateUseCase`, puerto de entrada `AtenderUpdatePort`) solo decide qué responder y lo envía
por el puerto de salida `BotDeTelegramPort`. Que un chat sin vincular «nunca llega al asistente» se
cumple por construcción: no hay camino al asistente. FIN-60 agrega el puerto de vinculación y
FIN-61 el puente hacia `ia`.

*Descartado:* ponerlo dentro de `ia`. El bot es otro canal de entrada, no parte del asistente; la
tabla de módulos ya anunciaba Telegram como el primer módulo nuevo.

### 2. Una tercera cadena de seguridad en `SecurityConfig`

Cadena con `securityMatcher` `POST /telegram/webhook`, Basic deshabilitado, sin resource server, y
un `AuthenticationWebFilter` cuyo convertidor lee la cabecera y cuyo `ReactiveAuthenticationManager`
la compara con el secreto con `MessageDigest.isEqual` (tiempo constante). Comparte `comun(...)` con
las otras dos. Órdenes: Basic `0`, Telegram `1`, JWT `2`; la del JWT sigue siendo la última porque
no tiene matcher.

El 401 lo escribe una instancia más de `UnauthenticatedEntryPoint`, con el mismo cuerpo que las
otras. El esquema que anuncia en `WWW-Authenticate` es `Telegram`: Telegram no lo lee, pero un 401
sin esa cabecera no es válido según HTTP. La excepción de autenticación lleva un mensaje fijo, sin el
valor recibido, porque el entry point la pasa al log.

El secreto se lee en `SecurityConfig` (`telegram.webhook-secret`), igual que la credencial Basic, y
ahí se valida su formato al construir la cadena: 32 a 256 caracteres de `A-Za-z0-9_-`. El alfabeto
es el que Telegram acepta en `setWebhook`; el mínimo de 32 es nuestro, como el de `JWT_SECRET`. El
mensaje del fallo no incluye el valor.

*Descartado:* ruta con `permitAll` y comparar el secreto en el controlador. Abriría la primera ruta
pública del API y dejaría el rechazo después de leer el cuerpo; con la cadena, el 401 sale antes de
que el update se lea. *Descartado también:* que el módulo `telegram` declare su propia cadena. Las
cadenas comparten `comun(...)` y el orden entre ellas importa; repartirlas entre módulos haría que
el orden dependiera de números elegidos en archivos distintos.

### 3. Responder con `sendMessage` de la Bot API, no en el cuerpo del webhook

Decidido con el usuario el 15-10-2026. Telegram permite contestar ejecutando un método en la
respuesta del webhook, lo que evita la llamada saliente y deja a Bruno ver el texto. Se descartó
porque FIN-61 necesita igual el cliente con el token (para `getFile` de audios y fotos), porque esa
forma no informa si el envío falló, y porque dejaría `TELEGRAM_BOT_TOKEN` sin uso en esta tarea
frente a un criterio que lo pide.

`TelegramBotApiAdapter` (`telegram.proveedor=telegram`) hace `POST
{base-url}/bot{token}/sendMessage` con `{"chat_id", "text"}`. La URL base es fija de configuración
(`TELEGRAM_BASE_URL`, default `https://api.telegram.org`), nunca de la petición. `StubTelegramBotAdapter`
(`@Profile("!prod")`, `telegram.proveedor=stub`) solo registra en el log el chat y el texto; con él,
`bruno/` verifica el status y que no se llama a Telegram, no el texto enviado. El texto lo cubre el
test del caso de uso.

### 4. El token no sale en los logs

El token va en la ruta de la URL, y `WebClientResponseException` y los errores de conexión de
Reactor Netty incluyen la URL en su mensaje. El adapter convierte todo error en un log con solo el
status (o el nombre simple de la excepción, si no hubo respuesta) y nunca registra
`getMessage()` ni la excepción entera. `GoogleIdTokenVerifierAdapter` ya hace lo mismo. Un test del
adapter comprueba que el log de un envío fallido no contiene el token.

### 5. Un fallo al enviar responde 200

El caso de uso absorbe el error del puerto (`onErrorResume`) y el webhook responde 200. Telegram
reintenta un update cuando el webhook no responde 2xx; hoy un reintento solo repetiría el texto,
pero con FIN-61 podría registrar dos veces el mismo movimiento. Dejar ese comportamiento fijado ahora
evita que FIN-61 tenga que cambiar el contrato.

### 6. Validación del update en el DTO

`TelegramUpdateRequest(updateId, message)` con `@JsonProperty` para el snake_case de Telegram; el
mensaje lleva `@Valid` y `chat` `@NotNull`, con `chat.id` `@NotNull`. Los campos que Telegram manda
y no se usan se ignoran. El caso de uso recibe un modelo de dominio (`MensajeDeTelegram(chatId)`),
no el DTO.

## Risks / Trade-offs

- [Bruno no ve el texto que responde el bot] → El test del caso de uso fija el texto; Bruno fija la
  autenticación y los status, que es lo que solo la app real verifica.
- [Una tercera cadena hace más fácil solapar matchers] → Tests de integración del par cruzado: Basic
  y Bearer válidos contra el webhook dan 401, y el secreto contra `/status` y `/accounts` da 401.
- [Un secreto filtrado deja inyectar updates falsos] → Solo producen la respuesta fija hasta FIN-60;
  se rota como cualquier secreto (`docs/despliegue.md`) y se vuelve a registrar el webhook.

## Migration Plan

1. Antes del despliegue: crear `telegram-bot-token` y `telegram-webhook-secret` en Secret Manager y
   enlazarlos al servicio como `TELEGRAM_BOT_TOKEN` y `TELEGRAM_WEBHOOK_SECRET`; sin ellos la
   revisión nueva no arranca.
2. Desplegar.
3. Registrar el webhook con `setWebhook` y comprobarlo con `getWebhookInfo` (procedimiento en
   `docs/despliegue.md`).

Revertir: revertir el commit en `main`. El webhook registrado queda apuntando a una ruta que da 401;
`deleteWebhook` lo quita.
