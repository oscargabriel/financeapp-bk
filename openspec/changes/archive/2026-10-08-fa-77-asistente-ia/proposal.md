# Proposal

Origen: FA-77, de la etapa 14 dividida el 06-10-2026. Texto tal como llegó (dictado): «La primera
etapa es que utilice una API de Google AI Studio, de Gemini; todavía no voy a definir el modelo,
pero va a ser el multimodal. […] En la primera etapa va a ser para definir el [prompt] y cómo van a
estar establecidas las reglas del modelo IA. Y en esta primera etapa la entrada va a ser por un
endpoint, en un JSON dentro del body un parámetro `data` que va a tener todo el texto como si se
recibiera ingresando a Telegram. El modelo va a ser multimodal pero de momento solamente se va a
manejar texto. […] Solamente tendría tres usos: crear una transacción, un movimiento; consultar
movimientos y consultar saldo.»

Decidido con el usuario el 08-10-2026, al proponer:

- Bruno prueba el flujo completo con un stub del modelo que se activa con `ASISTENTE_PROVEEDOR=stub`.
- Una sola llamada a Gemini por petición: el modelo elige la función y sus argumentos, y la
  respuesta al usuario la arma el back con textos fijos.
- El modelo recibe los nombres de las cuentas y categorías activas del usuario, sin ids ni saldos.
- `GEMINI_MODEL` no tiene default, igual que la key.

## Why

La etapa 15 conecta Telegram. Antes hace falta el contrato con el modelo: el prompt, las reglas,
las funciones que puede pedir y qué hace el back con cada una. Este change lo deja expuesto en un
endpoint, con el texto que llegaría de Telegram, y deja que lo que cree el asistente entre pendiente
de aprobación (FA-76).

## What Changes

- `POST /api/assistant/messages` con `{"data": "<texto>"}`, autenticado con JWT. Responde 200 con
  `intent`, un `message` para el usuario y, según el caso, el movimiento creado, el reporte o el
  saldo.
- Cliente de Gemini (`generateContent` con function calling) sobre `WebClient`, sin dependencias
  nuevas. Las tres funciones son `crear_movimiento`, `consultar_movimientos` y `consultar_saldo`.
- Un stub del modelo para Bruno, que nunca carga con el perfil `prod`.
- El dominio modela el origen de un movimiento (`WEB`, `TELEGRAM`, `IMPORT`). El alta guarda el
  origen en `transactions.origin`, y un origen `TELEGRAM` entra `PENDING`. La respuesta de un
  movimiento incluye `origin`.
- `GEMINI_API_KEY` y `GEMINI_MODEL` sin default. Van en el `application.yaml` de test, en
  `CloudRunConfigTest`, en la lista de `application-local.yaml` de `AGENTS.md` y en
  `docs/despliegue.md`.
- `ErrorCodes.EXTERNAL_SERVICE_ERROR` para una falla de Gemini: 502 con el formato común, lanzado
  como `BadRequestException`, que el handler ya traduce.
- `verificar-bruno.ps1` arranca la app con `ASISTENTE_PROVEEDOR=stub`.

## Capabilities

### New Capabilities

- `asistente`: el endpoint, las tres funciones, los rechazos y los errores.

### Modified Capabilities

- `transacciones`: el origen de un movimiento, en el alta y en la respuesta.

## Fuera de alcance

- Telegram, el despliegue multiusuario, las imágenes y el audio: etapa 15.
- Parametrizar los textos de respuesta y filtrar la entrada del usuario: son features propios,
  según las notas de la etapa. Si aparecen al implementar, van como tareas nuevas.
- Memoria de conversación: cada petición es independiente. Una aclaración («¿en qué cuenta?») se
  contesta con un mensaje nuevo y completo.
- Más de un movimiento por mensaje: el asistente crea a lo sumo uno.
- Monedas distintas de la de la cuenta: el movimiento va en la moneda de su cuenta.
- Crear el secret de Gemini en el servicio de Cloud Run: es configuración del servicio, y
  `docs/despliegue.md` dice que tiene que existir antes de promover a `main`.

## Impact

- Código nuevo: el puerto de entrada y el caso de uso del asistente, el puerto de salida del
  modelo con sus dos adapters (Gemini y stub), el controlador, los DTOs y el prompt versionado en
  `src/main/resources/asistente/`.
- Código que cambia: `Transaction` y `CreateTransactionsPort`/`UseCase` (origen),
  `TransactionR2dbcAdapter` (escribe y lee `origin`), `TransactionResponse` y `ErrorCodes`.
- Configuración: `application.yaml` de main y de test, y `CloudRunConfigTest`.
- La base no cambia: `transactions.origin` existe desde el esquema inicial, con su CHECK.
- `bruno/assistant/` nueva y `verificar-bruno.ps1`. El request real se replica en `bruno-personal/`.
- **Despliegue**: sin `GEMINI_API_KEY` y `GEMINI_MODEL` el servicio no arranca. Promover a `main`
  exige crearlos antes en el servicio.
