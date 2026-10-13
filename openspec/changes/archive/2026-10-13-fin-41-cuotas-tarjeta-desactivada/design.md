# Design

## Context

- Las cuotas de una compra son movimientos `EXPENSE` `CONFIRMED` de la tarjeta, creados todos en el
  alta. Mientras su fecha no llega son programados: el saldo vigente los descuenta con
  `finance.scheduled_balance_delta`, que solo mira `occurred_at > now()`, no el estado de la cuenta.
- Desactivar una cuenta (`UpdateAccountUseCase`) solo cuenta las series activas (FA-116). No
  consulta las compras.
- `GET`, `PATCH` y `DELETE` de `/api/installment-purchases` filtran por usuario y estado de la
  compra, no por el de la tarjeta.
- Borrar una cuenta ya exige cero programados (FA-115), y las cuotas por venir lo son.

## Goals / Non-Goals

**Goals:** dejar escrita y verificada la regla para las cuotas de una tarjeta que se desactiva o se
borra.

**Non-Goals:** cambiar el comportamiento del código, que ya es el elegido.

## Decisions

### Dejar las compras, no rechazar la desactivación ni cancelarlas

Decidido con el usuario el 13-10-2026. El issue proponía la regla de FA-116, pero la razón de esa
regla no aplica a las cuotas:

- Una serie bloquea la desactivación porque **crea movimientos nuevos** sobre una cuenta que ya no
  los admite. Una compra no crea nada después del alta: sus cuotas ya existen. Por eso se parecen a
  los programados sueltos, y FA-116 dejó explícitamente que esos no impiden desactivar.
- Las cuotas son deuda ya contraída. Dejar de usar una tarjeta no la borra, y el saldo tiene que
  seguir reflejándola.
- Se mantiene la regla vigente de `cuentas`: el cambio de estado no toca el saldo ni los movimientos.

**Descartado: rechazar con 409, como las series.**
- La única salida sería cancelar la compra, y eso borra las cuotas futuras y devuelve su capital al
  cupo: la deuda desaparece del sistema sin haberse pagado.
- Una compra a 48 cuotas dejaría la tarjeta sin poder desactivarse cuatro años.

**Descartado: cancelar las compras al desactivar.**
- Mismo problema de la deuda que desaparece.
- Es irreversible: reactivar la tarjeta no devuelve las cuotas.
- El PATCH de la cuenta escribiría en otro recurso sin que nadie lo pida, el mismo argumento de
  FA-116.

### Borrar sigue con la regla de FA-115

No hace falta una regla nueva: una tarjeta con cuotas por venir tiene programados y el borrado
responde 409 en `id`. La diferencia con las series es que una compra no regenera cuotas. Si el
usuario las borra todas a mano, quedan como pagadas y la tarjeta se puede borrar sin dejar nada
colgando. La spec de `cuotas` gana el requisito para que quien lea la capacidad encuentre la regla
sin tener que buscarla en `cuentas`.

### Verificación sin cambios de código

No hay código nuevo, así que la verificación es `bruno/`. El vencimiento de una cuota con la tarjeta
desactivada no se puede provocar en una corrida, porque no hay reloj fijo contra la base real. Por
eso `test-data.sql` siembra en `cuotas@` la tarjeta `Visa guardada`, desactivada, con una compra a 3
cuotas de 100000 sin tasa:

- la primera cuota venció ayer;
- las otras dos son dentro de uno y de dos meses.

Las cuotas sembradas no siguen la regla de fechas de corte y pago, que no es lo que se prueba aquí.

No se agrega un test JUnit: `UpdateAccountUseCase` no depende de las compras, y un test que
compruebe que no las consulta probaría la ausencia de un colaborador. Si alguien agrega en el futuro
el bloqueo de FA-116 para compras, el request de `bruno/` que desactiva `Visa USD` falla.

## Risks / Trade-offs

- **[Deuda escondida]** Una tarjeta desactivada no sale en `GET /api/accounts` por defecto, y su
  saldo sigue bajando a medida que vencen las cuotas. → La compra sigue en
  `GET /api/installment-purchases`, que es donde se ve lo que falta por pagar, y la tarjeta sale con
  `includeInactive=true`. Marcar en la cuenta que tiene cuotas por venir queda fuera de alcance (ver
  `proposal.md`).
- **[Reportes]** Las cuotas que vencen siguen contando como gastos en los reportes del mes en que
  vencen, con la tarjeta desactivada. → Es lo correcto: es un gasto real del mes.
