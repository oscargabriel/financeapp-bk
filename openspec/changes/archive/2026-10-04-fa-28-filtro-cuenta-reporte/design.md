# Design

## Context

El reporte de FA-63 arma su SQL con un `SELECT` base filtrado por `t.user_id` y por el rango en la
zona del usuario. A eso le suma fragmentos fijos cuando llega un filtro (`category_id = ANY(...)`,
`type = ANY(...)`), con los valores siempre por bind. Un movimiento tiene `account_id` (origen) y,
solo si es transferencia, `destination_account_id`. Los totales se calculan en Java sobre la lista
que devuelve la consulta.

## Goals / Non-Goals

**Goals:**
- Que el filtro responda a la pregunta "qué movimientos tocaron esta cuenta".
- No cambiar la respuesta ni el comportamiento sin `accountId`.

**Non-Goals:**
- Darle sentido (entra o sale) a los montos, o calcular el saldo de la cuenta. Eso es un extracto por
  cuenta, fuera de alcance según la propuesta.

## Decisions

### Una transferencia entra si la cuenta es su origen o su destino

El fragmento es `AND (t.account_id = ANY(:accountIds) OR t.destination_account_id = ANY(:accountIds))`.

- **Descartado: filtrar solo por `account_id`.** Es lo que haría un filtro ingenuo sobre la columna.
  Pero el filtro de Efectivo perdería el retiro de cajero, y el de la tarjeta perdería su pago, que
  es el movimiento que más se quiere ver al conciliar. Para el usuario, esos movimientos son de las
  dos cuentas.
- Como es un `OR` dentro de un mismo `WHERE`, una transferencia entre dos cuentas filtradas sale una
  sola vez. No hace falta `DISTINCT`.

### Los totales no cambian de regla

Una transferencia que entra suma su `amountBase` a `TRANSFER` igual que una que sale. Con el filtro
de una sola cuenta, `TRANSFER` mide cuánto se movió por ella, no su saldo neto.

- **Descartado: totales con signo según el sentido.** Rompería el requisito *Totales del reporte*,
  que suma `amountBase` sin signo. Además el resultado dependería de cuántas cuentas lleguen en el
  filtro: con las dos cuentas de una transferencia, ¿suma o resta? Es la pregunta de un extracto, no
  de este filtro.

### Una cuenta ajena se filtra en el SQL, sin consultarla antes

El filtro va junto a `t.user_id = :userId`, así que un id de otro usuario no encuentra nada y la
respuesta es 200 con la lista vacía, igual que `categoryId` en FA-63.

- **Descartado: validar antes que la cuenta sea del usuario y responder 404.** Cuesta otra consulta y
  le revela a quien pregunta si ese id existe. Además se apartaría de cómo se comporta `categoryId`.

### Un conjunto más en el puerto y en el filtro, en el mismo orden

`GetTransactionReportPort.get` y `TransactionReportFilter` reciben `Set<UUID> accountIds` después de
`categoryIds`. Vacío significa "sin filtro", igual que los otros dos.

- **Descartado por ahora: cambiar el puerto para que reciba un record de consulta.** Con seis
  parámetros la firma sigue siendo legible, y el record exigiría mover a otro lugar la validación del
  rango, que hoy hace el caso de uso al construir `TransactionReportFilter`. Si llega un cuarto
  filtro, ese es el momento.

## Risks / Trade-offs

- **El `OR` sobre dos columnas puede impedir que se usen los índices por cuenta.** Esos índices son
  `ix_transactions_account_date` e `ix_transactions_destination_account`. → La consulta ya acota por
  `user_id` y rango con `ix_transactions_user_date`, y el `OR` se evalúa sobre las filas de un usuario
  en unos días. No hace falta un índice nuevo.
- **Un cliente puede leer `TRANSFER` de una sola cuenta como si fuera su saldo neto.** → El contrato
  del API dice explícitamente que los totales no llevan signo.
