# Design

## Context

- Un movimiento `CONFIRMED` con `occurred_at > now()` está programado (FA-106): no cuenta en el saldo
  vigente ni en los totales hasta su fecha. Las cuotas se apoyan en eso, como las ocurrencias de FA-107.
- `availableCredit` hoy es `creditLimit + currentBalance` (saldo vigente), calculado en `Account`. El
  saldo vigente sale de `AccountR2dbcAdapter`, y la misma `Account` alimenta `GET /api/accounts` y
  `GET /api/reports/balance`.
- La tarjeta guarda `statementDay`, `paymentDueDay` (1–31, opcionales) y `monthlyInterestRate`
  (porcentaje mensual, 0–10, opcional, FA-105).
- Solo hay movimientos en COP (`ReferenciasDelUsuario.MONEDA_UNICA`), cuyo `decimal_places` es 0.
- FA-107 dejó el alcance `FUTURE`/`ALL` (`RecurrenceScope`) y la medianoche en la zona del usuario.

## Goals / Non-Goals

**Goals:** el contrato de `/api/installment-purchases`, el plan de cuotas (fechas, capital e interés)
probado en el dominio, un cupo disponible que baja por el total desde la compra, y la edición en grupo y
la cancelación con la semántica de FA-107.

**Non-Goals:** validar el cupo, cambiar el monto o el número de cuotas de una compra, recalcular cuando
cambian los datos de la tarjeta, compras futuras.

## Decisions

### Contrato

`POST /api/installment-purchases/preview` → 200 con el plan, sin guardar nada.
`POST /api/installment-purchases` → 201 con la compra y su plan. Los dos reciben:

```json
{
  "accountId": "…", "categoryId": "…",
  "amount": 1200000, "description": "Televisor",
  "purchaseDate": "2026-10-09", "installmentCount": 3
}
```

| Campo | Regla |
|---|---|
| `accountId` | Obligatorio. Cuenta propia, activa y en COP, con los mensajes de siempre. Además `CREDIT` y con `statementDay` y `paymentDueDay`. |
| `categoryId` | Obligatorio. Categoría activa y compatible con gastos. |
| `amount` | Obligatorio, mayor que 0, con el formato de montos de siempre. Al menos 1 peso por cuota: no menor que `installmentCount`. |
| `description` | Obligatoria, hasta 255 caracteres. |
| `purchaseDate` | Obligatoria, `YYYY-MM-DD`, hoy o antes en la zona del usuario. |
| `installmentCount` | Obligatorio, de 1 a 48. |

La respuesta de la simulación:

```json
{
  "accountId": "…", "amount": 1200000, "currencyCode": "COP",
  "purchaseDate": "2026-10-09", "installmentCount": 3, "monthlyInterestRate": 2,
  "totalInterest": 48000, "totalAmount": 1248000,
  "installments": [
    {"number": 1, "dueAt": "2026-11-05T05:00:00Z", "principal": 400000, "interest": 24000, "amount": 424000},
    {"number": 2, "dueAt": "2026-12-05T05:00:00Z", "principal": 400000, "interest": 16000, "amount": 416000},
    {"number": 3, "dueAt": "2027-01-05T05:00:00Z", "principal": 400000, "interest": 8000,  "amount": 408000}
  ]
}
```

La compra, en `GET`, `PATCH` y como base del `POST`:

```json
{
  "id": "…", "accountId": "…", "categoryId": "…",
  "amount": 1200000, "currencyCode": "COP", "description": "Televisor",
  "purchaseDate": "2026-10-09", "installmentCount": 3, "monthlyInterestRate": 2,
  "paidCount": 0, "remainingPrincipal": 1200000, "remainingAmount": 1248000,
  "nextInstallment": {"number": 1, "transactionId": "…", "dueAt": "2026-11-05T05:00:00Z", "amount": 424000}
}
```

- `paidCount` es `installmentCount` menos las cuotas que existen con fecha posterior al momento actual.
  Una cuota borrada a mano cuenta como pagada: es la forma de registrar un pago adelantado.
- `remainingAmount` y `remainingPrincipal` suman el monto y el capital de esas cuotas futuras, con el
  monto actual de cada una (también si se editó a mano). `remainingPrincipal` es lo que la compra tiene
  comprometido del cupo.
- `nextInstallment` es la cuota existente más próxima con fecha posterior al momento actual, o `null`.

El `POST` responde la compra más `installments`, el plan con el `transactionId` de cada cuota:
`{number, transactionId, dueAt, principal, interest, amount}`.

`GET /api/installment-purchases` → 200 con las compras activas, ordenadas por la fecha de su próxima
cuota. Activa es no cancelada y con al menos una cuota existente posterior al momento actual.

`PATCH /api/installment-purchases/{id}` → 200 con la compra. Cuerpo: `scope` obligatorio (`FUTURE` o
`ALL`) y al menos uno de `description` y `categoryId`. Ausente o `null` es «no cambia».

`DELETE /api/installment-purchases/{id}` → 204.

Errores: 400 `VALIDATION_ERROR` con el campo de cada regla, todos los de formato juntos; 404 `NOT_FOUND`
sobre `id` para una compra inexistente, ajena o cancelada; 400 sobre `id` si no es UUID; 401 sin token o
con el Basic. No hay códigos nuevos.

**Descartado:** un `dryRun` en el `POST` para simular. Mezcla un 200 sin efecto con un 201 en la misma
ruta. **Descartado:** el plan completo en `GET` y `PATCH`. El front pidió para el listado el resumen
(pagadas, próxima, lo que falta); cada cuota ya está en `reports/transactions` con su `installment`.

### Fechas de las cuotas

Con `statementDay` C y `paymentDueDay` P de la tarjeta:

1. El corte de un mes es el día C de ese mes, o su último día si el mes es más corto.
2. El primer corte es el del mes de la compra si la compra cae ese día o antes; si no, el del mes
   siguiente. **Una compra hecha el mismo día del corte entra en ese corte**, como en los extractos.
3. La primera cuota vence el día P del mes del primer corte si P > C, y del mes siguiente si P ≤ C. Un P
   que el mes no tiene cae el último día del mes.
4. Las siguientes vencen el día P de cada mes siguiente, con el día anclado a P y no al de la cuota
   anterior: con P 31 dan 31-ene, 28-feb, 31-mar.

Cada cuota es el día de vencimiento a las 00:00 en la zona del usuario, como las ocurrencias de FA-107:
una cuota de hoy ya ocurrió. Si la compra es de hace meses, las cuotas hasta hoy cuentan de una vez.

Ejemplo, C 20 y P 5: una compra del 9 o del 20 de octubre vence el 5 de noviembre; una del 21 de
octubre, el 5 de diciembre.

**Descartado:** que el corte del mismo día no incluya la compra. La tarea dice «primer corte posterior a
la compra»; aquí se lee como el primer corte que la incluye, que es lo que hace el extracto.

### Capital e interés

- Capital: `amount / N` redondeado hacia abajo a pesos enteros para las N−1 primeras; la última se lleva
  el resto. La suma da exactamente `amount`, y la última nunca es menor que las demás. Por eso `amount`
  tiene que ser al menos N: ninguna cuota queda con capital cero.
- Interés de la cuota k: la tasa mensual sobre el capital pendiente antes de esa cuota (`amount` menos el
  capital de las k−1 anteriores), redondeado a pesos enteros, mitad hacia arriba. Con 1 cuota, o tasa 0,
  no hay interés.
- La tasa es la de la tarjeta al registrar la compra, y se copia en la compra: cambiarla después no
  mueve las cuotas creadas. Una tarjeta sin tasa (`null`) se toma como 0.
- El monto de cada cuota es capital más interés.

Pesos enteros porque `decimal_places` de COP es 0 y es la única moneda de los movimientos. Con más
monedas, el redondeo pasa a la escala de la moneda.

**Descartado:** redondear el capital mitad hacia arriba. Con un `amount` apenas mayor que N, la última
cuota quedaría en cero o negativa. **Descartado:** cuota fija (sistema francés). El usuario pidió
capital fijo.

### El cupo

Cada cuota guarda su capital en `transactions.installment_principal`. `finance.committed_credit(cuenta)`
suma el capital de las cuotas confirmadas de la cuenta con `occurred_at > now()`, y:

```
availableCredit = creditLimit + saldo vigente − committed_credit
```

Al registrar la compra, todas las cuotas futuras comprometen su capital: el cupo baja por el total, y el
saldo vigente no cambia. Al llegar una cuota, su capital sale de lo comprometido y la cuota entera entra
al saldo: el cupo baja además por el interés, que es cuando se cobra. Cancelar borra las futuras, y su
capital vuelve al cupo. Editar una cuota a mano no cambia su capital: si se adelanta a hoy, sale de lo
comprometido y entra al saldo con su monto nuevo.

**Descartado:** registrar la compra como un gasto por el total y las cuotas como pagos. Contaría la
compra dos veces en los reportes de gasto y contradice la decisión de que cada cuota es el gasto.
**Descartado:** calcular el capital en SQL a partir de la compra. Repetiría la regla de redondeo en dos
lugares; guardado en la fila, lo que cuenta es lo que se creó.

### Edición en grupo y cancelación

- `scope: FUTURE` aplica `description` o `categoryId` a la compra y a sus cuotas con
  `occurred_at > now()`; `ALL`, también a las pasadas. Como en FA-107, las cuotas editadas a mano dentro
  del alcance reciben el cambio. Ningún saldo cambia: ni monto ni cuenta están en el parche.
- `DELETE` borra las cuotas con `occurred_at > now()` y marca la compra `CANCELLED`, en una transacción.
  La fila se conserva para que las pasadas sigan diciendo de qué compra son. Una compra cancelada
  responde 404 a `PATCH` y `DELETE`.
- Una compra sin cuotas por venir pero no cancelada sale del listado y todavía se puede editar con
  `ALL` o cancelar.

El enum `RecurrenceScope` pasa a llamarse `GroupScope`: es el mismo alcance para los dos recursos.

**Descartado:** cambiar monto, número de cuotas o tarjeta en el `PATCH`. Rehace el plan entero; es más
claro cancelar y registrar de nuevo.

### El movimiento dice de qué compra es

`installment: {purchaseId, number, count}` en la respuesta de un movimiento y en el ítem de
`reports/transactions`, o `null`. `count` es el `installmentCount` de la compra, también después de
cancelarla. Las lecturas de movimientos hacen `LEFT JOIN` a la compra para leerlo. El cliente no lo
envía: en el cuerpo se ignora, igual que `recurrenceId`.

Una cuota editada o borrada a mano con `/api/transactions/{id}` sigue en su compra: el `UPDATE` de un
movimiento no toca las columnas de la cuota.

### Persistencia

- `finance.installment_purchases`: `user_id`, `account_id`, `category_id`, `amount`, `currency_code`,
  `description`, `purchase_date`, `installment_count` (1–48), `monthly_interest_rate` (la copiada, no
  nula), `status` (`ACTIVE`/`CANCELLED`) y las fechas de auditoría.
- `finance.transactions` gana `installment_purchase_id` (FK `NO ACTION DEFERRABLE INITIALLY DEFERRED`,
  como las demás), `installment_number` e `installment_principal`, con un `CHECK` de que van los tres o
  ninguno, y un índice parcial por compra y fecha.
- Las cuotas entran como `EXPENSE`, `CONFIRMED`, `origin = 'WEB'`, con la descripción de la compra.

## Risks / Trade-offs

- **El interés consume cupo al cobrarse**, no al comprar: el cupo de una compra a 3 cuotas baja
  1.200.000 el día de compra y 24.000 más el día de la primera cuota. Es como lo cobra la tarjeta.
- **Una cuota editada a mano conserva su capital**: si el usuario la baja por debajo del capital, el
  cupo comprometido sigue siendo el capital hasta su fecha. Para pagar por adelantado lo natural es
  adelantar la fecha o borrarla.
- **La tarjeta cambia de corte o de pago** después de la compra: las cuotas creadas no se mueven.
