## Context

Ver `proposal.md` para el porqué. Lo que el código ya resuelve:

- `recurrences` e `installment_purchases` guardan `currency_code`, y `Recurrence.ocurrencia` y el alta
  de cuotas ya copian esa moneda a cada movimiento. Hoy siempre es COP porque
  `ReferenciasDelUsuario.cuentaEnCop` no deja pasar otra.
- `InstallmentPlan` fija la escala en 0 (`ESCALA`) para el capital y el interés.
- El mínimo de capital por cuota lo valida `@MontoParaLasCuotas` en el request, como
  `amount >= installmentCount`: un peso entero por cuota.
- `CurrencyQueryPort.findActive()` ya devuelve las monedas activas con `decimalPlaces` (FA-51).
- El PATCH de un movimiento ya rechaza una cuenta en otra moneda con «La cuenta tiene que estar en X,
  la moneda del movimiento» (FA-51, decisión 7).

## Goals / Non-Goals

**Goals**
- La moneda de la serie o de la compra sale siempre de la cuenta; ningún cuerpo la trae.
- En COP el resultado de todo cálculo queda idéntico al de hoy.

**Non-Goals**
- Unificar la regla de moneda del PATCH de series con la del PATCH de movimientos en una sola clase:
  comparten el mensaje, no el código.

## Decisions

### 1. El mínimo de capital por cuota pasa del request al caso de uso

La regla pasa a ser `amount >= installmentCount × unidad mínima`, donde la unidad mínima es
`10^-decimal_places` de la moneda de la tarjeta. El request no conoce la tarjeta, así que la regla se
mueve a `PlanDeCompra`, que ya tiene la tarjeta y va a tener la moneda. El mensaje nombra la unidad:
«El monto tiene que alcanzar para al menos 0.01 USD de capital por cuota» (1 COP en pesos).
`@MontoParaLasCuotas` se borra.

Es lo que pide `AGENTS.md`: lo que necesita la base va en el caso de uso. La consecuencia es que este
error ya no sale junto con los errores de formato del cuerpo, sino con los de la tarjeta y la
categoría. La spec no pedía que saliera junto con ellos: «Varios errores juntos» usa `amount` ausente.

**Descartado**
- Dejar la regla en el request con 1 unidad entera. Una compra de 5 USD a 12 cuotas sería 400 aunque
  cada cuota lleve 0.41 USD de capital, y el mensaje seguiría diciendo «1 peso».
- Dejar en el request un mínimo relajado (`amount >= installmentCount × 0.0001`) y la regla real en el
  caso de uso. Serían dos reglas para lo mismo, con dos mensajes.

### 2. La moneda de la cuenta tiene que estar activa en el alta

Las altas de series y de cuotas leen `findActive()` junto con las cuentas y categorías. Una cuenta cuya
moneda no está en esa lista es 400 en `accountId` con «La moneda de la cuenta no esta activa». Las
cuotas necesitan sus `decimalPlaces` para el plan, y la tarea pide «cualquier moneda activa» para las
series. El PATCH de una serie no lee el catálogo: la cuenta nueva tiene que estar en la moneda de la
serie, que no cambia.

Hoy no hay forma de desactivar una moneda desde el API, y una cuenta solo se crea en una moneda activa.
El caso solo aparece si se desactiva una moneda a mano en la base.

**Descartado**: usar 2 decimales por defecto, como `ConversorDeMontos` con una moneda inactiva. En un
movimiento convertido el monto queda pendiente y se ajusta. En las cuotas, la escala decide la deuda
que se registra, y adivinarla dejaría cuotas confirmadas con un redondeo que nadie eligió.

### 3. `InstallmentPlan` recibe la escala

El record gana un componente `int decimals`, y `ESCALA` desaparece. El capital se divide hacia abajo a
esa escala y el interés se redondea con la mitad hacia arriba, igual que hoy. El constructor corto, el
que usan los tests, sigue siendo el de una tarjeta en COP (escala 0) que no cobra interés a 1 cuota.

**Descartado**: pasarle el `Currency` entero. El plan solo necesita la escala, y así no depende del
catálogo.

## Risks / Trade-offs

- [Un `amount` con más decimales que la moneda, por ejemplo 100.555 USD a 3 cuotas] → la última cuota
  absorbe el resto con 3 decimales, como hoy pasa en COP con 1000.5. La validación de decimales por
  moneda queda fuera de alcance (ver `proposal.md`).
- [Una serie en USD con ocurrencias futuras] → `trg_transactions_usd_equivalent` les calcula
  `amount_base` como a cualquier movimiento en USD. `bru run` y `check-saldos.sql` lo verifican.
