# Proposal

Issue **FIN-65** del tablero, epic **FIN-9 — Finanzas compartidas**. Es la segunda tarea del flujo
del epic, después del modelo de datos (FIN-16, en `Por revisar`). La pantalla de afiliados del front
(FIN-69) y compartir gastos (FIN-66) dependen de ella.

## Why

FIN-16 dejó la tabla `finance.affiliations`, pero ningún endpoint la usa: hoy un usuario no tiene
cómo relacionarse con otro. Compartir un gasto (FIN-66) exige que las dos personas estén afiliadas,
y el front necesita la pantalla donde se envían, aceptan y deshacen esas relaciones.

## What Changes

Aplica estas decisiones del epic FIN-9, todas del 13-10-2026:

- la otra persona es usuaria registrada, se identifica por su correo, y la afiliación pasa por
  solicitada, aceptada, rechazada, cancelada o desvinculada;
- una solicitud a un correo no registrado responde igual que a uno registrado: el API no revela qué
  correos tienen cuenta;
- cualquiera de los dos desvincula, y desde ahí no se comparte nada nuevo, pero los pendientes y las
  partes abiertas siguen.

En concreto, seis rutas **Bearer** nuevas bajo `/api/affiliations`:

| Método | Ruta | Para qué |
|---|---|---|
| `GET` | `/api/affiliations` | Las relaciones abiertas del usuario: solicitudes enviadas, recibidas y afiliaciones aceptadas |
| `POST` | `/api/affiliations` | Enviar una solicitud a un correo |
| `POST` | `/api/affiliations/{id}/accept` | Aceptar una recibida |
| `POST` | `/api/affiliations/{id}/reject` | Rechazar una recibida |
| `POST` | `/api/affiliations/{id}/cancel` | Cancelar una enviada que sigue abierta |
| `POST` | `/api/affiliations/{id}/unlink` | Desvincular una afiliación aceptada |

- Cada relación sale con la otra persona (correo, nombre y apellido), el estado y si el usuario la
  envió o la recibió (`direction`).
- **El nombre de la otra persona no sale en una solicitud enviada y sin aceptar**, ni siquiera si
  el correo tiene cuenta. Si saliera, el remitente sabría al enviar qué correos están registrados.
  Sale en las recibidas, porque quien la recibe ya sabe que el remitente existe, y en las aceptadas.
- Enviarse una solicitud a sí mismo es 400. Repetirla con alguien con quien ya hay una relación
  abierta que el usuario ve (en cualquier sentido) es 409. Una relación en la que el usuario no
  participa es 404, igual que una inexistente. Una acción que no corresponde al estado o al papel
  del usuario (aceptar la propia, cancelar una aceptada) es 409.
- El listado solo trae las abiertas (`REQUESTED`, `ACCEPTED`). Una rechazada, cancelada o
  desvinculada deja de salir, y entre las mismas personas se puede enviar una solicitud nueva.
- `contrato-api.md` documenta las seis rutas, y `bruno/affiliations/` las ejercita con usuarios
  creados en cada corrida.

### Desvincular y compartir

Del criterio «desde ese momento no se puede compartir nada nuevo entre ellos; los pendientes y las
partes abiertas que ya existían siguen», este change cumple lo que existe hoy: desvincular solo cambia
el estado de la afiliación, y no toca `transactions` ni `expense_shares`. Todavía no hay forma de
compartir. Que el alta de un gasto compartido rechace a un desvinculado es un criterio de FIN-66
(«una persona no afiliada o desvinculada … rechaza el alta entera»), y se hace allí, contra el estado
`ACCEPTED` que este change deja escrito.

## Fuera de alcance

- Compartir gastos o transferencias, y verificar la afiliación al compartir: FIN-66.
- Lo que se deben por persona en la pantalla de afiliados: FIN-67.
- Avisar por correo de una solicitud: fuera de alcance del epic. La solicitud solo se ve en la app.
- Que una solicitud a un correo no registrado le llegue a la persona si se registra después: el
  modelo de FIN-16 decidió que no se resuelve (su `design.md`, decisión 2).
- Listar el historial de relaciones cerradas. Si el front lo pide, va como tarea nueva.

## Capabilities

### New Capabilities

- `afiliaciones`: solicitudes de afiliación entre usuarios por correo, sus transiciones (aceptar,
  rechazar, cancelar, desvincular) y el listado de las relaciones abiertas, bajo `/api/affiliations`.

### Modified Capabilities

Ninguna.

## Impact

- Módulo `core`: modelo `Affiliation` y sus enums, puertos de entrada y de salida, tres casos de
  uso, `AffiliationController` con sus DTOs y `AffiliationR2dbcAdapter`. Un `ErrorCodes` no cambia:
  se usan `VALIDATION_ERROR`, `DUPLICATE_RESOURCE`, `NOT_FOUND` e `INVALID_STATE`.
- Sin cambios de esquema: usa la tabla y los índices de FIN-16. Tampoco cambian `test-data.sql` ni
  la demo, salvo el comentario que lista los prefijos de correo que `bruno/` registra.
- `docs/api/contrato-api.md`, `bruno/affiliations/` y su réplica en `bruno-personal/`.
- `docs/database/modelo-datos.md`: la sección de finanzas compartidas pasa a citar los endpoints.
