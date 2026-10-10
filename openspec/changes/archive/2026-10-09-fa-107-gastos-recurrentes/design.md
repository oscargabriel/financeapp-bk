# Design

## Context

- Un movimiento `CONFIRMED` con `occurred_at > now()` ya está programado (FA-106): no cuenta en el saldo
  vigente ni en los totales, y empieza a contar solo al llegar su fecha. Las ocurrencias de una serie
  son movimientos normales que se apoyan en eso.
- Cloud Run escala de 0 a 1 (FA-48) y no hay ningún planificador: un `@Scheduled` no corre con la
  instancia apagada, y en Neon tampoco hay `pg_cron` mientras el cómputo está suspendido.
- Solo hay movimientos en COP (`ReferenciasDelUsuario.MONEDA_UNICA`). La serie hereda esa regla.
- La zona del usuario está en `users.timezone` y ya corta los días de los reportes.

## Goals / Non-Goals

**Goals:** el contrato de `/api/recurrences`, la regla de fechas probada en el dominio, la puesta al día
de las series sin fin sin depender de un proceso, y una edición en grupo cuyo efecto en los saldos lo
resuelva el trigger que ya existe.

**Non-Goals:** cuotas (FA-108), transferencias recurrentes, cambiar el fin de una serie.

## Decisions

### Contrato

`POST /api/recurrences` → 201 con la serie:

```json
{
  "type": "EXPENSE",
  "accountId": "…", "categoryId": "…",
  "amount": 44900, "description": "Netflix",
  "frequency": "MONTHLY", "interval": 1, "dayOfMonth": 15,
  "startDate": "2026-10-15",
  "endDate": null, "occurrences": null
}
```

| Campo | Regla |
|---|---|
| `type` | Obligatorio, `EXPENSE` o `INCOME`. |
| `accountId`, `categoryId` | Obligatorios. Cuenta propia, activa y en COP; categoría activa y compatible con el tipo. Mismos mensajes que el alta de movimientos. |
| `amount` | Obligatorio, mayor que 0, con el formato de montos de siempre. |
| `description` | Obligatoria, hasta 255 caracteres, como en los movimientos. |
| `frequency` | Obligatoria, `WEEKLY` o `MONTHLY`. |
| `interval` | Opcional, 1 por defecto. De 1 a 52 en `WEEKLY` y de 1 a 12 en `MONTHLY`. |
| `dayOfWeek` | `MONDAY`…`SUNDAY`. Obligatorio en `WEEKLY`, prohibido en `MONTHLY`. |
| `dayOfMonth` | De 1 a 31. Obligatorio en `MONTHLY`, prohibido en `WEEKLY`. |
| `startDate` | Obligatoria, `YYYY-MM-DD`. Puede ser pasada: las ocurrencias hasta hoy cuentan de una vez. |
| `endDate` | Opcional, `YYYY-MM-DD`, no anterior a `startDate`. Incluida. |
| `occurrences` | Opcional, de 1 a 500. No va junto con `endDate`. |

Una serie no puede crear más de 500 ocurrencias de una vez, el mismo tope que el alta en lote (FA-26):
el error va sobre `endDate` si la serie tiene fin, y sobre `startDate` si no lo tiene y empieza tan
atrás que las ocurrencias hasta hoy pasan del tope. Una serie que no tendría ninguna ocurrencia antes de su `endDate`
(un lunes entre un miércoles y el jueves siguiente) también es error sobre `endDate`.

La respuesta de una serie:

```json
{
  "id": "…", "type": "EXPENSE", "accountId": "…", "categoryId": "…",
  "amount": 44900.0000, "currencyCode": "COP", "description": "Netflix",
  "frequency": "MONTHLY", "interval": 1, "dayOfWeek": null, "dayOfMonth": 15,
  "startDate": "2026-10-15", "endDate": null, "occurrences": null,
  "nextOccurrenceAt": "2026-10-15T05:00:00Z"
}
```

`nextOccurrenceAt` es la ocurrencia existente más próxima con fecha posterior al momento actual, en UTC.
Si la serie sin fin no tiene ninguna, porque el usuario borró a mano la próxima, es la fecha de la que
la serie va a crear a continuación.

`GET /api/recurrences` → 200 con las series activas, ordenadas por `nextOccurrenceAt`. Una serie es
activa si no está cancelada y tiene ocurrencias por venir: siempre, si no tiene fin; si lo tiene, mientras
le quede alguna ocurrencia con fecha posterior al momento actual.

`PATCH /api/recurrences/{id}` → 200 con la serie. El cuerpo lleva `scope` obligatorio (`FUTURE` o
`ALL`) y al menos uno de `amount`, `description`, `categoryId`, `accountId`, `frequency`, `interval`,
`dayOfWeek`, `dayOfMonth`. Ausente o `null` es «no cambia», como en el PATCH de movimientos.

`DELETE /api/recurrences/{id}` → 204.

Errores: 400 `VALIDATION_ERROR` con el campo de cada regla, todos juntos; 404 `NOT_FOUND` sobre `id`
para una serie inexistente, ajena o cancelada, y para un id que no es UUID 400 sobre `id`, como en
movimientos; 401 sin token o con el Basic. No hay códigos nuevos.

**Descartado:** la periodicidad anidada en un objeto (`schedule: {…}`). Plano sigue el estilo de los
demás cuerpos y deja cada error con un `field` de un nivel. **Descartado:** el `scope` como query
param. Así una edición sin alcance se rechaza con el mismo 400 de un campo del cuerpo, y el `DELETE`
no lo necesita, porque cancelar siempre conserva lo pasado.

### Regla de fechas

La regla de una serie es (`frequency`, `interval`, día, fecha de inicio). La primera ocurrencia es la
primera fecha igual o posterior al inicio que cae en el día pedido; las siguientes, cada `interval`
semanas o meses. En `MONTHLY` el día se ancla al pedido y no al de la ocurrencia anterior: 31 da
31-ene, 28-feb (29 en bisiesto), 31-mar, 30-abr. Cada ocurrencia es el día a las 00:00 en la zona del
usuario, así que la de hoy ya ocurrió y cuenta: «futura» es lo que tiene fecha posterior a hoy en esa
zona, como pide la tarea, y se decide con `occurred_at > now()` igual que en FA-106.

**Descartado:** usar la hora del alta o del mediodía. Con las 00:00, «ocurrió» y «es de hoy o
antes» son la misma pregunta, y el resultado no depende de la hora a la que se creó la serie.

### Las series sin fin se ponen al día al leer

Una serie sin fin mantiene creadas sus ocurrencias hasta hoy más la siguiente. Al llegar la fecha de la
siguiente, la que sigue tiene que existir sin que nadie la cree. Como no hay proceso que corra a tiempo,
**la puesta al día se hace al leer**: antes de responder, los casos de uso de `GET /api/accounts`,
`GET /api/reports/balance`, `GET /api/reports/transactions`, `GET /api/monthly-spending` y
`GET /api/recurrences` crean las ocurrencias que les falten a las series sin fin del usuario. Es lo que
FA-106 hizo con el saldo: lo que el usuario ve está al día cuando lo mira, aunque el servicio haya
estado apagado.

La serie guarda cuántas ocurrencias de su regla actual lleva creadas. La puesta al día lee las series
sin fin activas del usuario —una consulta, y nada más si no tiene—, calcula en Java cuáles faltan y las
inserta junto con el contador nuevo en una transacción. El `UPDATE` del contador exige el valor que
leyó: si dos peticiones la ponen al día a la vez, la segunda actualiza cero filas, deshace sus inserts y
sigue. Por el mismo contador, una ocurrencia borrada a mano no vuelve: la serie crea desde la
siguiente a la última que creó, no desde las que existen.

**Descartado:** un `WebFilter` en la cadena del JWT que la corra en cada petición. Cubriría
cualquier lectura futura sin acordarse, pero mete una escritura en la base dentro del filtro de
seguridad, se la cobra también a los POST y a los PATCH, y obliga a montar la base en todos los slices
web. **Descartado:** Cloud Scheduler contra un endpoint interno. Agrega infraestructura y una ruta con
otra credencial, y en local y en Bruno no correría. **Descartado:** dejar materializado un horizonte (un
año de ocurrencias) en las series sin fin. Contradice la decisión del usuario de tener solo la próxima,
y el horizonte igual se acaba.

El precio es que la puesta al día depende de que esos cinco casos de uso la llamen. Una lectura nueva
de saldos o movimientos tiene que llamarla también, y `modelo-datos.md` y la spec lo dicen.

### Edición en grupo

- `scope: FUTURE` aplica los cambios de plantilla (`amount`, `description`, `categoryId`, `accountId`)
  a la serie y a sus ocurrencias con `occurred_at > now()`. `ALL` también a las pasadas. Los saldos los
  corrige `trg_transactions_sync_balance`, que ya revierte la fila vieja y aplica la nueva.
- Las ocurrencias editadas a mano dentro del alcance también reciben el cambio: la edición en grupo
  manda sobre la suelta. Una que el usuario movió a una fecha pasada queda fuera de `FUTURE`.
- **Cambiar la periodicidad rehace las futuras, con cualquier `scope`**: borra las ocurrencias con
  `occurred_at > now()` y la regla nueva empieza mañana, en la zona del usuario. Las pasadas no se
  mueven de fecha: ya ocurrieron. Una serie con `occurrences` conserva el total: las de la regla anterior
  hasta hoy se descuentan, y la regla nueva crea las que falten. Una con `endDate` crea hasta esa fecha;
  una sin fin, hasta la siguiente.
- Se valida la serie resultante, como en el PATCH de movimientos: pasar a `MONTHLY` exige `dayOfMonth`
  en el parche, y un `dayOfWeek` en una serie que queda mensual es error.

La serie guarda, además de la regla actual, cuántas ocurrencias cuentan de las reglas anteriores, para
que el tope de `occurrences` sobreviva a los cambios de periodicidad.

**Descartado:** que `FUTURE` respete las ocurrencias editadas a mano. Exigiría marcar cada fila como
«desligada» y explicar en la respuesta cuáles no cambiaron. Para corregir una sola, el PATCH del
movimiento sigue disponible después de la edición en grupo.

### Cancelación

`DELETE` borra las ocurrencias con `occurred_at > now()` y marca la serie `CANCELLED` en una
transacción. La fila de la serie se conserva, para que las ocurrencias pasadas sigan diciendo de qué
serie son. Una serie cancelada responde 404 a `PATCH` y `DELETE`, igual que una inexistente.

### Persistencia

`finance.recurrences` guarda la plantilla, la regla (`frequency`, `interval_count`, `day_of_week` ISO
1–7, `day_of_month`, `start_date`), el fin (`end_date` u `occurrence_limit`), los contadores
(`generated_count` de la regla actual y `prior_count` de las anteriores) y `status`.
`finance.transactions.recurrence_id` apunta a la serie con `NO ACTION DEFERRABLE INITIALLY DEFERRED`,
como las demás referencias del movimiento, y lleva un índice parcial `WHERE recurrence_id IS NOT NULL`.
Las ocurrencias entran con `origin = 'WEB'`, el origen de la serie. No hace falta un origen nuevo:
`recurrenceId` ya dice de dónde salen.

## Risks / Trade-offs

- **Una lectura nueva que olvide la puesta al día** muestra saldos sin las ocurrencias atrasadas de
  las series sin fin. Mitigación: la spec lista las lecturas, y `modelo-datos.md` lo deja como regla.
- **Las lecturas de un usuario con series sin fin cuestan una consulta más**, y una escritura cuando
  hay algo atrasado. Son pocas filas por usuario.
- **Una serie que empieza muy atrás** crea de una vez todo lo que ya pasó y mueve el saldo de
  inmediato. Es lo pedido: registrar una suscripción que ya se viene cobrando. El tope de 500 corta el
  caso absurdo.
- **Cambiar la zona del usuario** no mueve las ocurrencias ya creadas. Las nuevas usan la zona nueva.
