# Design

## Context

El asistente registra lo que el usuario le dicta con el alta en lote del core
(`PendienteDelAsistenteAdapter` → `CreateTransactionsPort`, origen `TELEGRAM`, estado `PENDING`). Un
mensaje deja un grupo: todos sus movimientos comparten `group_id`. Cada movimiento lleva cuenta, y
los gastos e ingresos categoría: sin categoría el asistente pide aclarar y no registra nada. Sin
`fecha`, `occurredAt` es el instante del mensaje; con `fecha`, ese día a las 12:00 en la zona de la
aplicación. No tienen `shared_by_user_id` ni `share_id`: eso es lo que los separa de los pendientes
compartidos en el filtro `sharedBy` (FIN-73).

La demo ya tiene, de FIN-30, pendientes compartidos fechados en días hacia atrás desde `now()`.

## Escenario

| Grupo | Mensaje | Movimiento | Cuenta | Categoría | Monto | `occurred_at` |
|---|---|---|---|---|---|---|
| A | hace 1 día | Gasto «Mercado del fin de semana» | Mastercard Oro | Mercado | 86.400 | el del mensaje |
| A | | Gasto «Gasolina» | Mastercard Oro | Transporte | 120.000 | dos días antes del mensaje, 12:00 Bogotá |
| A | | Transferencia «Recarga Nequi» | Bancolombia Nómina → Nequi | — | 200.000 | el del mensaje |
| B | hace 3 horas | Gasto «Almuerzo» | Efectivo | Restaurantes | 28.000 | el del mensaje |

`created_at` de cada fila es el instante del mensaje. Montos en COP, tasa 1, `amount_base` igual al
monto. Los ids de grupo son fijos, con el prefijo libre `e000000<u>`: `e000000<u>-…-001` el A y
`…-002` el B.

## Decisions

### Fechas en días hacia atrás desde `now()`, no por mes

Los movimientos confirmados de la demo se fechan por mes (`date_trunc('month', …) + desfase`) y se
filtran con `occurred_at <= now()`. Para los pendientes se descarta: el criterio pide «los últimos
días», y fechados por mes, una carga el día 1 los dejaría en el futuro (filtrados o programados) o a
casi un mes. Se sigue el patrón de lo compartido de FIN-30, `now() - INTERVAL`, que siempre queda en
el pasado y reciente.

### La transferencia va a Nequi

Se descartó llevarla a Ahorros o a Efectivo, que ya reciben transferencias fijas cada mes: con Nequi
la pantalla muestra una transferencia que se distingue de las de siempre, y aprobarla le da a Nequi
su primer movimiento. Mientras siga pendiente, Nequi conserva el saldo cero que la demo usa como caso
de cuenta vacía. El comentario de *Cuentas* se ajusta.

### Ids de grupo fijos

Podrían ser `gen_random_uuid()`, como los de lo compartido, pero un grupo con varias filas necesita
el mismo id en todas, y un `\set` con prefijo propio lo resuelve sin una CTE. Además deja reconocer
los grupos de la demo por su id al depurar desde el front.

## Risks / Trade-offs

- Las filas copian lo que escribe el alta en lote. Si cambia cómo guarda los pendientes del
  asistente, la demo se queda vieja sin que nada falle; es el mismo riesgo aceptado en FIN-30 para lo
  compartido.
- Aprobar el grupo A desde el front exige saldo y cupo suficientes en la nómina y la tarjeta (*Saldo
  suficiente al aprobar un pendiente*). La tarea 2.2 lo comprueba aprobándolo contra el API; si no
  alcanza, se bajan los montos.
