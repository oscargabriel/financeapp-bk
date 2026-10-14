# Proposal

Issue **FIN-67** del tablero, epic **FIN-9 — Finanzas compartidas**. Depende de FIN-66 (partes
compartidas y su estado, en `Por revisar`). El front lo consume en FIN-72 («te debe / le debes por
persona»), que está bloqueado por este issue. Se tomó desde `pendiente`, a pedido del usuario.

## Why

FIN-66 guarda cada parte compartida con su estado, pero el API no muestra lo que dos personas se
deben: el usuario solo ve el estado de cada parte dentro de `shares` del gasto que pagó, y nada de lo
que él debe. La pantalla de afiliados (FIN-72) necesita por persona el saldo neto y el detalle de las
partes entre los dos.

## What Changes

Aplica estas decisiones del epic FIN-9, todas del 13-10-2026:

- cada parte es una fila propia con estado (pendiente, pagada, rechazada, anulada), y **la deuda
  abierta es la suma de las partes pendientes**;
- rechazar la parte anula la deuda, y quien pagó la ve rechazada;
- una transferencia compartida no genera deuda;
- cualquiera de los dos desvincula, y las partes abiertas siguen;
- **lo que se deben se ve por persona en la pantalla de afiliados**.

Dos rutas **Bearer** nuevas bajo `/api/affiliations`:

| Método | Ruta | Para qué |
|---|---|---|
| `GET` | `/api/affiliations/balances` | Una entrada por persona: la afiliación que la representa, su estado (`ACCEPTED` o `UNLINKED`), la persona y el saldo por moneda |
| `GET` | `/api/affiliations/{id}/shares` | Las partes entre el usuario y la persona de esa afiliación, en cualquier estado, con quién le debe a quién |

- **Quién sale en el saldo:** toda persona con una afiliación `ACCEPTED`, aunque no se deban nada
  (`balances: []`, a paz y salvo), y toda persona desvinculada con la que quede alguna parte
  pendiente. Una desvinculada sin nada pendiente no sale.
- **El saldo, por moneda:** `owedToMe` es la suma de las partes pendientes que la persona le debe al
  usuario, `iOwe` la de las que el usuario le debe a ella, y `net` = `owedToMe − iOwe`. Las monedas
  no se convierten entre sí: sale una entrada por cada moneda con partes pendientes.
- **El detalle** lista cada parte con descripción, fecha, monto, moneda, estado, fecha de resolución
  y `direction`: `OWED_TO_ME` si el usuario pagó el gasto, `I_OWE` si lo pagó la otra persona. Una
  parte rechazada sale `REJECTED` para los dos.
- **Solo se ven las partes en las que participa el usuario:** las de la otra persona con terceros no
  salen, aunque el usuario esté afiliado con los dos.
- El detalle se pide con cualquier afiliación `ACCEPTED` o `UNLINKED` del usuario con esa persona.
  Una inexistente o ajena es 404 `NOT_FOUND` en `id`, una en otro estado (solicitada, rechazada,
  cancelada) es 409 `INVALID_STATE` en `status`, y un id que no es UUID es 400 `VALIDATION_ERROR` en
  `id`, como en las demás rutas de afiliaciones.
- `contrato-api.md` documenta las dos rutas y quita «el saldo y el detalle de lo que se deben dos
  afiliados» de lo que el API todavía no tiene. `bruno/affiliate-balances/` las ejercita con usuarios
  creados en cada corrida.

## Fuera de alcance

- Filtrar los pendientes por persona y rechazarlos en lote: FIN-68.
- Saldar la deuda de otra forma que aprobando cada parte (un «pago total» o una compensación entre
  lo que se deben los dos): el epic no lo pide.
- Convertir el saldo a una sola moneda: el epic dice que la deuda es la suma de las partes, y cada
  parte está en la moneda en que se registró el gasto.
- Las transferencias compartidas en el detalle: no tienen partes ni generan deuda (decisión del
  epic).
- Paginar el detalle. Si el front lo necesita, va como tarea nueva.
- Ir desde una parte al movimiento que la originó: el gasto de la otra persona es ajeno, y el
  pendiente propio ya sale en `GET /api/transactions/pending`.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `compartidos`: se agregan el saldo por persona, el detalle de las partes entre dos personas y la
  autenticación de las dos rutas.

## Impact

- Módulo `core`: modelos del saldo y del detalle, dos puertos de entrada con sus casos de uso, un
  puerto de salida de consulta con su adapter R2DBC, y dos rutas nuevas en `AffiliationController`
  con sus DTOs. `ErrorCodes` no cambia.
- Sin cambios de esquema: usa `expense_shares` y `affiliations` de FIN-16 y sus índices por
  `(payer_id, status)` y `(debtor_id, status)`.
- `docs/api/contrato-api.md`, `bruno/affiliate-balances/` y su réplica en `bruno-personal/`. El
  comentario de `docs/database/test-data.sql` que lista los prefijos de `@bruno.local` suma el de la
  carpeta nueva.
- El front: FIN-72 ya está bloqueado por este issue y cubre la parte del contrato que cambia.
