# Design

## Context

El porqué está en `proposal.md`. El modelo es el de FIN-16 (`design.md` de
`fin-16-modelo-finanzas-compartidas`): una fila de `finance.affiliations` por solicitud,
`addressee_id` solo si el correo tenía cuenta al enviarla, y dos índices únicos parciales
(`ux_affiliations_open_pair`, `ux_affiliations_open_email`). Las transiciones de estado quedaron para
la aplicación.

Lo que condiciona el contrato es la decisión del epic de no revelar qué correos tienen cuenta. El
alta de usuarios ya la cumple a medias: responde 403 a un correo no admitido aunque tenga cuenta
(`contrato-api.md`, `POST /api/auth/register`).

## Goals / Non-Goals

**Goals:**

- Que el remitente no distinga, por nada de lo que el API le devuelve al enviar ni al listar, si el
  correo tiene cuenta, mientras la otra persona no actúe.
- Que FIN-66 pueda preguntar si dos personas están afiliadas mirando solo `status = 'ACCEPTED'`.

**Non-Goals:**

- Ocultar el tiempo de respuesta: con cuenta, el envío hace una consulta más. La diferencia es de
  milisegundos sobre una base remota, y taparla costaría más de lo que protege.
- Que la otra persona no pueda revelar su cuenta: si acepta o rechaza, el remitente lo sabe. Es su
  decisión.

## Decisions

### 1. Una ruta por acción, con `POST`

```
POST /api/affiliations/{id}/accept   → 200 con la relación
POST /api/affiliations/{id}/reject   → 204
POST /api/affiliations/{id}/cancel   → 204
POST /api/affiliations/{id}/unlink   → 204
```

Es la forma de `POST /api/transactions/{id}/approve` y `/reject`, que el front ya consume. Aceptar
devuelve la relación porque sigue en el listado y ahora trae el nombre; las otras tres la sacan del
listado, como rechazar un pendiente.

- **Descartado: `PATCH /api/affiliations/{id}` con `{"status": …}`.** El cliente tendría que saber
  qué estado escribir en cada caso, y el 400 de un estado ilegal se confundiría con el 409 de una
  transición que no aplica.
- **Descartado: `DELETE /api/affiliations/{id}` para cancelar y desvincular.** Un `DELETE` que no
  borra la fila, y que hace dos cosas según el estado, se lee peor que dos rutas con nombre.

### 2. Una sola puerta para las cuatro acciones

`ChangeAffiliationPort.change(userId, id, AffiliationAction)`. El enum `AffiliationAction` del
dominio dice, por acción, quién la puede hacer (destinatario, remitente o cualquiera), desde qué
estado y a cuál lleva:

| Acción | Quién | Desde | Hacia |
|---|---|---|---|
| `ACCEPT` | destinatario | `REQUESTED` | `ACCEPTED` |
| `REJECT` | destinatario | `REQUESTED` | `REJECTED` |
| `CANCEL` | remitente | `REQUESTED` | `CANCELLED` |
| `UNLINK` | cualquiera | `ACCEPTED` | `UNLINKED` |

El caso de uso lee la relación filtrada por participación (vacía → 404), le pide al dominio si la
acción aplica (no → 409) y escribe con un `UPDATE … WHERE id = :id AND status = :desde`. Cero filas
quiere decir que otra petición la cambió entre la lectura y la escritura, y responde el mismo 409.

- **Descartado: cuatro puertos y cuatro casos de uso.** Repetirían la misma lectura, el mismo 404 y
  la misma escritura condicionada; lo único distinto es una fila de la tabla de arriba.
- **Descartado: un solo `UPDATE` con todas las condiciones, sin lectura previa** (como cancelar una
  serie). Cero filas no distinguiría el 404 de una ajena del 409 de un estado que no aplica, y el
  criterio pide los dos.

### 3. Participante con la acción equivocada: 409, no 404

Aceptar la solicitud que uno mismo envió, o cancelar una ya aceptada, responde 409 `INVALID_STATE`
en `status`. Solo quien no participa recibe 404.

- **Descartado: 404 también para el participante.** El participante ve la relación en su listado; un
  404 sobre algo que está viendo le diría que no existe, y el front no podría distinguirlo de un id
  equivocado. El 409 es lo que ya responde aprobar un movimiento confirmado.

### 4. El nombre no sale en una solicitud enviada y abierta

`person` trae `email`, `firstName` y `lastName`:

| Relación | `email` | Nombre y apellido |
|---|---|---|
| Enviada, `REQUESTED` | el correo escrito (`addressee_email`) | `null`, tenga o no cuenta |
| Recibida, `REQUESTED` | el correo actual del remitente | los del remitente |
| `ACCEPTED`, en cualquier sentido | el correo actual de la otra persona | los de la otra persona |

Si una enviada trajera el nombre cuando el correo tiene cuenta, el remitente sabría en el acto qué
correos están registrados. Quien recibe sí ve al remitente, porque ya sabe que existe. En una
aceptada se usa el correo actual de `users`, no el escrito: la persona pudo cambiarlo en su perfil.

- **Descartado: devolver `addresseeRegistered` o el nombre.** Es exactamente lo que el epic prohíbe.

### 5. El duplicado se mide solo sobre lo que el usuario ve

Antes de insertar, el caso de uso pregunta si el usuario tiene ya una relación abierta con esa
persona entre las que puede ver:

- una enviada por él a ese correo, `REQUESTED`;
- con el usuario de ese correo, si tiene cuenta: una enviada por él con ese `addressee_id`, una
  recibida de él (`addressee_id` = el usuario), o una `ACCEPTED` en cualquier sentido.

Si la hay, 409 `DUPLICATE_RESOURCE` en `email`. Como el usuario ya ve esa relación en su listado, el
409 no le dice nada nuevo sobre si el correo tiene cuenta. Una solicitud vieja de B a un correo de A
cuando A no tenía cuenta queda sin destinatario, A no la ve, y no cuenta.

Los dos índices únicos de FIN-16 frenan la carrera de dos envíos simultáneos. La violación de
cualquiera de los dos se traduce en el adapter al mismo 409, como hace `UserR2dbcAdapter` con el
correo repetido.

- **Descartado: confiar solo en los índices.** `ux_affiliations_open_pair` no ve las solicitudes a
  correos sin cuenta, y el 409 sin la consulta previa saldría como un error de la base traducido
  sin mensaje propio.

### 6. A sí mismo se detecta por id, no por correo

El destinatario se resuelve con `UserRepositoryPort.findActiveByEmail`, que ya filtra los usuarios
borrados y desactivados. Si el id resuelto es el del usuario, 400 `VALIDATION_ERROR` en `email`. Así
no hace falta leer el correo del perfil, y un usuario desactivado cuenta como correo sin cuenta, en
línea con el login.

- **Descartado: un método nuevo `findActiveIdByEmail`.** Devolvería lo mismo que
  `findActiveByEmail` sin el hash; el puerto ya tiene la consulta con el filtro correcto.

### 7. El listado solo trae lo abierto

`GET /api/affiliations` devuelve `REQUESTED` y `ACCEPTED`, de la más reciente a la más antigua por
`createdAt`. Lo cerrado no sale: la pantalla del front muestra relaciones sobre las que se puede
actuar, y lo que se deben (FIN-67) va por persona afiliada, no por solicitud histórica.

- **Descartado: listar también las cerradas con un filtro.** Ningún criterio lo pide, y una rechazada
  en el listado del remitente sería el único lugar donde el historial importa. Queda como tarea nueva
  si el front la necesita.

## Risks / Trade-offs

- **Una solicitud huérfana en sentido contrario.** B le envió una a `a@` cuando A no tenía cuenta; A
  se registra y le envía una a B. B ve su enviada sin destinatario y la recibida de A a la vez.
  → Mitigación: no rompe nada; B cancela la suya o acepta la de A. Es consecuencia de la decisión 2
  de FIN-16 (no se resuelve después).
- **El remitente se entera de la cuenta cuando la otra persona actúa.** Una rechazada desaparece de
  su listado; una aceptada trae el nombre.
  → Mitigación: es la acción de la otra persona, no algo que el remitente pueda provocar.
- **Correo cambiado.** Si B cambia su correo, una solicitud nueva al correo viejo queda sin
  destinatario.
  → Mitigación: igual que cualquier correo sin cuenta: nadie la recibe y el remitente la cancela.
