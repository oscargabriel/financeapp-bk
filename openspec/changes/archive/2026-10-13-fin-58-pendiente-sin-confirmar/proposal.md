# Proposal

Issue **FIN-58** del tablero, epic **FIN-11 — Asistente y Telegram**. Es la tercera tarea del flujo
del epic. El bot de Telegram (FIN-61) y compartir gastos (FIN-66) dependen de ella, y el correo
(FIN-12) va a usar el mismo mecanismo.

## Why

Hoy un movimiento queda pendiente según por dónde entró: la spec ata `PENDING` al origen `TELEGRAM`,
y el asistente registra un solo movimiento por mensaje, que se aprueba de a uno. El bot necesita
mostrar todo lo que entendió de un mensaje y confirmarlo o descartarlo con un botón. El correo y las
cuentas compartidas necesitan crear pendientes sin hacerse pasar por el asistente.

## What Changes

Aplica estas decisiones del epic FIN-11, todas del 13-10-2026:

- un movimiento queda pendiente porque no se ha confirmado, no por su origen;
- el asistente traduce el mensaje a cuenta, categoría, monto y descripción y lo devuelve para
  confirmar; un mensaje puede traer varios movimientos, que se confirman o se descartan juntos, y al
  confirmar la respuesta dice que quedaron guardados;
- lo que no se confirma en el chat queda en los pendientes de la web.

En concreto:

- **Grupo de pendientes.** Todo pendiente pertenece a un grupo, identificado por un `groupId`: los
  movimientos que entraron juntos sin confirmar. El grupo se guarda en la base y viaja en la
  respuesta de cada movimiento (`groupId`, `null` si entró confirmado).
- **Dos rutas Bearer nuevas** en el core:

  | Método | Ruta | Para qué |
  |---|---|---|
  | `POST` | `/api/transactions/groups/{groupId}/approve` | Confirmar los pendientes del grupo: todos o ninguno |
  | `POST` | `/api/transactions/groups/{groupId}/reject` | Descartar los pendientes del grupo |

  Confirmar aplica las validaciones del alta (cuenta activa, categoría viva que aplica al tipo,
  saldo no negativo sobre el efecto conjunto) y responde 200 con los movimientos confirmados. Si una
  falla, responde 409 con cada error indexado y no confirma ninguno. Un grupo de otro usuario, uno
  inexistente o uno ya descartado es 404, y uno sin pendientes porque ya se confirmó es 409; ninguno
  cambia nada.
- **El estado inicial lo decide quien registra**, no el origen. El alta web entra confirmada salvo lo
  que convierte de otra moneda, que sigue entrando pendiente, ahora en un grupo con los demás
  convertidos de ese mismo lote. El asistente registra pendiente y conserva el origen `TELEGRAM`.
- **Aprobar uno a uno valida también la categoría**: hoy solo valida las cuentas y el saldo. Así el
  pendiente suelto y el grupo aplican las mismas reglas.
- **El asistente registra varios movimientos por mensaje.** El modelo llama `crear_movimiento` una vez
  por movimiento. Si alguno no se puede resolver, pide aclarar y no crea ninguno. **BREAKING** para el
  cliente del asistente: el campo `transaction` de la respuesta se reemplaza por
  `group: {id, transactions: [...]}`, y `message` pide confirmar o descartar. El front no usa este
  endpoint; FIN-61 todavía no existe.
- `contrato-api.md`: las rutas nuevas, `groupId` en la respuesta del movimiento, la respuesta nueva
  del asistente, y «Aprobar o rechazar pendientes en lote» sale de «Lo que el API todavía no tiene».

## Fuera de alcance

- El bot de Telegram, sus botones y el texto «se guardaron» que muestra el chat: FIN-61. Este change
  deja la ruta que el bot va a llamar y la respuesta con lo confirmado.
- Aprobar un pendiente sin cuenta o sin categoría eligiéndolas al aprobar: FIN-66. Hoy ningún
  pendiente se crea sin ellas.
- Crear pendientes desde el correo: FIN-12.
- Audio e imagen en el asistente: FIN-57.
- Corregir cómo el contrato describe `report` en la respuesta del asistente: FIN-64. Este change solo
  reescribe la parte de `transaction` → `group`.
- Confirmar desde el asistente con un mensaje de texto («sí, guárdalos»): cada mensaje sigue siendo
  independiente.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: el estado inicial deja de depender del origen; grupos de pendientes con
  `groupId`; confirmar y descartar un grupo; aprobar uno a uno valida la categoría.
- `asistente`: registra uno o varios movimientos pendientes en un grupo y los devuelve en `group`;
  todo o nada al resolver los nombres.

## Impact

- Módulo `core`: `Transaction` gana `groupId`; `CreateTransactionsPort` recibe el estado inicial y
  `TransactionOrigin` pierde `estadoInicial`; dos puertos y casos de uso nuevos para el grupo; la
  validación de aprobación pasa a una clase que comparten el pendiente suelto y el grupo;
  `TransactionController`, `TransactionResponse` y `TransactionR2dbcAdapter`.
- Módulo `ia`: `AssistantDecision`, `AssistantReply`, `AssistantUseCase`, el puerto de registro y su
  puente, `FuncionesDelAsistente`, `GeminiAssistantAdapter`, `StubAssistantAdapter`, la instrucción y
  `AssistantMessageResponse`.
- Base: columna `transactions.group_id` con índice, y un `CHECK` que exige grupo a todo pendiente.
  Dos updates en `docs/database/update/`, uno antes y otro después del despliegue, más `schema.sql`,
  `test-data.sql` y `modelo-datos.md`. El update modifica `transactions`: reinicia los datos locales.
- `bruno/assistant/`, `bruno/pending/`, `bruno/exchange-rates/` y su réplica en `bruno-personal/`.
- Contrato: el front usa la lista y la aprobación de pendientes, que ganan `groupId`. Al cerrar se
  aplica *El contrato* de `tareas-jira`.
