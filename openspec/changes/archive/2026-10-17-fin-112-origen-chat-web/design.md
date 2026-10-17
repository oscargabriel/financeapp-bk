# Design

## Context

El origen lo decide hoy el puente: `PendienteDelAsistenteAdapter.register` llama al alta del core
con `TransactionOrigin.TELEGRAM` fijo. Entre el controlador y el puente, el canal no viaja:
`AssistantPort.atender(userId, mensaje)` y `PendingMovementPort.register(userId, movements)` no lo
conocen. `ia` no puede importar `TransactionOrigin`, que es del core.

## Goals / Non-Goals

**Goals:** que el canal entre por el puerto de entrada del asistente y llegue hasta el alta, para
que FIN-61 solo tenga que llamar al puerto con `TELEGRAM`.

**Non-Goals:** que el modelo o el texto del mensaje influyan en el canal; cualquier cambio en el
módulo `telegram/`, que todavía no llama al asistente.

## Decisions

### `WEB_CHAT` como valor de `origin`

Descartados `CHAT` (Telegram también es un chat, y el front tendría que explicar cuál) y
`ASSISTANT` (el bot también es el asistente: nombraría el mecanismo, no el canal, que es lo que
Pendientes muestra). `WEB_CHAT` mide 8 caracteres y la columna admite 10.

### El canal es un parámetro del puerto, no un campo del mensaje

`AssistantPort.atender(UUID userId, AssistantChannel canal, AssistantMessage mensaje)`. Igual que el
usuario, el canal lo fija quien llama según por dónde entró la petición, y nada de lo que diga el
mensaje lo cambia. Meterlo en `AssistantMessage` mezclaría lo que escribió el usuario con lo que
decide el adaptador de entrada, y `AssistantMessage` es lo que recibe el modelo. El precio es tocar
las llamadas de los tests de `AssistantUseCaseTest` y `AssistantControllerTest`, que son mecánicas.

### Un enum propio de `ia` y la traducción en el puente

`AssistantChannel { WEB_CHAT, TELEGRAM }` en `ia/domain/model`. `PendingMovementPort.register`
recibe el canal, y el puente lo traduce a `TransactionOrigin` con un `switch` explícito, no por
`name()`: los dos enums pueden divergir (el core tiene `WEB`, `IMPORT` y `EMAIL`, que no son canales
del asistente) y el `switch` sin `default` no compila si aparece un canal sin origen. Es la regla de
`AGENTS.md`: `ia` declara lo que necesita y `puente/` solo traduce.

Descartado pasar `TransactionOrigin` a `compartido`: el origen es del modelo del core, y moverlo
solo para esto abriría la puerta a mover cada tipo que un módulo necesite del otro.

## Risks / Trade-offs

- [La app nueva inserta `WEB_CHAT` y Neon todavía no lo admite] → el update va **antes** del
  despliegue, y su encabezado lo dice. Al revés que FIN-101, cuyo `EMAIL` no lo producía la app.
- [La app vieja no sabe leer `WEB_CHAT`] → solo pasa si se revierte el despliegue con filas
  `WEB_CHAT` ya creadas. La reversión del update borra antes esas filas, igual que la de `EMAIL`.
- [Los pendientes `TELEGRAM` que ya hay en Neon salieron de probar el endpoint] → se quedan como
  están (ver *Fuera de alcance* de `proposal.md`).

## Migration Plan

En Neon: aplicar `20261017_01_origen_chat_web.sql`, desplegar (merge a `main`) y después recargar la
demo de `demo@`, que trae filas `WEB_CHAT`. En local, el update no reinicia datos: basta con
aplicarlo y correr `cargar-datos-local.sql` para ver la demo nueva.
