# Contrato del API — compras en cuotas

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### Compras en cuotas

Una compra con tarjeta de crédito diferida a cuotas. Cada **cuota** es un gasto normal de la tarjeta,
con su `installment` (ver [`POST /api/transactions`](transacciones.md#post-apitransactions)), y queda
[programada](transacciones.md#movimientos-programados) hasta su fecha. **Bearer** en las cinco rutas.

**Solo en una tarjeta** `CREDIT` con `statementDay` (corte) y `paymentDueDay` (pago).

**Fechas.** El primer corte es el día de corte del mes de la compra si la compra cae ese día o antes
—**una compra del mismo día del corte entra en ese corte**—, y si no, el del mes siguiente. La primera
cuota vence el día de pago del mes del corte si el pago es posterior al corte, y del mes siguiente si
no; las demás, el día de pago de cada mes. Un día que el mes no tiene cae en su último día (31-ene,
28-feb, 31-mar). Cada cuota vence a las 00:00 de su día en la zona del usuario (`05:00:00Z` en Bogotá).
Con corte 20 y pago 5, una compra del 9 o del 20 de octubre vence el 5 de noviembre; una del 21, el 5
de diciembre.

**Capital e interés.** El capital de cada cuota es el total entre las cuotas, redondeado hacia abajo a
los decimales de la moneda de la tarjeta (pesos enteros en COP, centavos en USD); la última se lleva el
resto, de modo que los capitales suman el total exacto. El interés de cada cuota es la tasa mensual de la
tarjeta sobre el capital que falta antes de esa cuota, a esos mismos decimales, la mitad hacia arriba. Con tasa 0 o sin tasa no hay interés. Una compra a 1 cuota
no lleva interés, salvo en una tarjeta con `chargesSingleInstallmentInterest` en `true`: ahí la cuota
única cobra la tasa sobre el total, con el mismo redondeo (300.000 a 1 cuota con tasa 2 es una cuota
de 306.000). La tasa se copia en la compra: cambiar la de la tarjeta después no mueve las cuotas. La
opción de interés a 1 cuota no se copia, pero tampoco mueve las compras ya registradas: cada cuota
guarda su interés al registrarse.

| Cuota (1.200.000 a 3, tasa 2) | Capital | Interés | Total |
|---|---|---|---|
| 1 | 400.000 | 24.000 (2 % de 1.200.000) | 424.000 |
| 2 | 400.000 | 16.000 (2 % de 800.000) | 416.000 |
| 3 | 400.000 | 8.000 (2 % de 400.000) | 408.000 |

**El cupo.** Desde la compra, `availableCredit` de la tarjeta descuenta el capital de las cuotas que
todavía no llegan, aunque esas cuotas no cuenten en `currentBalance`. Al llegar una cuota, su capital
deja de estar comprometido y la cuota entera entra al saldo: el interés consume cupo cuando se cobra.
En el ejemplo, una tarjeta en cero con cupo 5.000.000 queda en 3.800.000 al comprar, y en 3.776.000
cuando vence la primera cuota.

**Cuotas sueltas.** Una cuota se corrige o se borra con `PATCH` o `DELETE` sobre
`/api/transactions/{id}` y sigue siendo de su compra, con su número. Un **pago adelantado** se
registra así: adelantando la fecha de la cuota o borrándola. Una cuota borrada cuenta como pagada.

#### `POST /api/installment-purchases/preview` y `POST /api/installment-purchases`

Los dos reciben el mismo cuerpo y aplican las mismas reglas:

```json
{
  "accountId": "0199a1b2-...",
  "categoryId": "0199a1c0-...",
  "amount": 1200000,
  "description": "Televisor",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `accountId` | Sí | Tarjeta `CREDIT` propia y activa, en una moneda activa, con día de corte y día de pago. La compra y sus cuotas van en la moneda de la tarjeta |
| `categoryId` | Sí | Categoría propia que aplique a gastos |
| `amount` | Sí | Total de la compra, sin interés. Mayor que cero, hasta 4 decimales, y no menor que `installmentCount` por la unidad mínima de la moneda de la tarjeta: cada cuota lleva al menos 1 peso, o 0.01 USD, de capital |
| `description` | Sí | Hasta 255 caracteres; se recorta. Es la de cada cuota |
| `purchaseDate` | Sí | `YYYY-MM-DD`, hoy o antes en la zona del usuario. Si es pasada, las cuotas vencidas cuentan de una vez |
| `installmentCount` | Sí | De 1 a 48 |
| `shares` | No | Solo para rechazarlo: una compra en cuotas no se comparte. Ausente, `null` o `[]`; con alguna parte es 400 en `shares` |

`preview` responde **200 OK** sin guardar nada:

```json
{
  "accountId": "0199a1b2-...",
  "amount": 1200000,
  "currencyCode": "COP",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3,
  "monthlyInterestRate": 2.0000,
  "totalInterest": 48000,
  "totalAmount": 1248000,
  "installments": [
    { "number": 1, "dueAt": "2026-11-05T05:00:00Z", "principal": 400000, "interest": 24000, "amount": 424000 },
    { "number": 2, "dueAt": "2026-12-05T05:00:00Z", "principal": 400000, "interest": 16000, "amount": 416000 },
    { "number": 3, "dueAt": "2027-01-05T05:00:00Z", "principal": 400000, "interest": 8000, "amount": 408000 }
  ]
}
```

El alta responde **201 Created** con la compra y su plan, cada cuota con el `transactionId` de su
movimiento:

```json
{
  "id": "019a3c20-...",
  "accountId": "0199a1b2-...",
  "categoryId": "0199a1c0-...",
  "amount": 1200000.0000,
  "currencyCode": "COP",
  "description": "Televisor",
  "purchaseDate": "2026-10-09",
  "installmentCount": 3,
  "monthlyInterestRate": 2.0000,
  "paidCount": 0,
  "remainingPrincipal": 1200000,
  "remainingAmount": 1248000,
  "nextInstallment": {
    "number": 1, "transactionId": "019a3c20-...", "dueAt": "2026-11-05T05:00:00Z", "amount": 424000
  },
  "installments": [
    { "number": 1, "transactionId": "019a3c20-...", "dueAt": "2026-11-05T05:00:00Z",
      "principal": 400000, "interest": 24000, "amount": 424000 }
  ]
}
```

| Campo | Significado |
|---|---|
| `monthlyInterestRate` | La tasa de la tarjeta al registrar la compra; `0` si no tenía |
| `paidCount` | `installmentCount` menos las cuotas que existen con fecha posterior al momento actual. Una cuota borrada a mano cuenta como pagada |
| `remainingPrincipal` | Capital de esas cuotas: lo que la compra tiene comprometido del cupo |
| `remainingAmount` | Lo que falta por pagar: el monto actual de esas cuotas, también si se editó a mano |
| `nextInstallment` | La cuota más próxima con fecha posterior al momento actual, o `null` si no queda ninguna |
| `installments` | Solo en el alta: el plan completo |

#### `GET /api/installment-purchases`

**200 OK** — arreglo de compras con la forma del alta, **sin** `installments`, de la próxima cuota a la
más lejana. Solo las **activas**: no canceladas y con alguna cuota por venir. Una compra cuyas cuotas
ya pasaron todas deja de aparecer. Sin compras activas, `[]`. Cada cuota está en
`reports/transactions` con su `installment`.

#### `PATCH /api/installment-purchases/{id}`

```json
{ "scope": "FUTURE", "description": "Televisor sala" }
```

`scope` es obligatorio: `FUTURE` cambia solo las cuotas con fecha posterior al momento actual; `ALL`,
también las pasadas. Se puede cambiar `description` y `categoryId`; ausente o `null` es "no cambia", y
al menos uno tiene que venir. Las cuotas del alcance reciben los cambios también si se editaron a mano.
Ningún saldo cambia. El monto, las cuotas, la tarjeta y la fecha no se editan: se cancela y se registra
de nuevo.

**200 OK** — la compra como quedó, sin `installments`.

#### `DELETE /api/installment-purchases/{id}`

**204 No Content.** Borra las cuotas futuras, conserva las pasadas con su `installment` y saca la compra
de la lista. El capital de las cuotas borradas vuelve al cupo. No lleva `scope`.

**Con la tarjeta desactivada (FIN-41)** la compra se lista, se edita y se cancela igual que con la
tarjeta activa, y sus cuotas siguen venciendo. Mientras tenga cuotas por venir, la tarjeta no se borra,
esté activa o no: 409 `RESOURCE_IN_USE` en `id` (ver [`DELETE /api/accounts/{id}`](cuentas.md#delete-apiaccountsid)).

**Errores de las cinco rutas**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | El del campo | Cualquier regla de la tabla del alta |
| 400 | `VALIDATION_ERROR` | `accountId` | La cuenta no es una tarjeta, o la tarjeta no tiene día de corte o de pago |
| 400 | `VALIDATION_ERROR` | `purchaseDate` | La fecha es posterior a hoy en la zona del usuario |
| 400 | `VALIDATION_ERROR` | `scope` | `PATCH` sin `scope`, o con un valor distinto de `FUTURE` y `ALL` |
| 400 | `VALIDATION_ERROR` | `body` | `PATCH` sin `description` ni `categoryId` |
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La compra no existe, es de otro usuario o ya está cancelada |
