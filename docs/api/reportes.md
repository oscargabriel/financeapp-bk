# Contrato del API — reportes

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `GET /api/monthly-spending`

Gasto e ingreso por mes del usuario del token, con el gasto comparado con su meta mensual.
**Bearer.**

**Query params**

| Param | Formato | Por defecto |
|---|---|---|
| `from` | `YYYY-MM` | 11 meses antes de `to` |
| `to` | `YYYY-MM` | El mes actual |

Sin ninguno, la ventana son los **12 meses que terminan en el mes actual**. Con solo `from`, llega
hasta el mes actual. Con solo `to`, abarca los 12 meses que terminan ahí. El rango incluye los dos
extremos. Errores 400 `VALIDATION_ERROR`:

- Un mes mal escrito (`2026-9`, `2026-13`), en el campo `from` o `to` según cuál esté mal.
- `from` posterior a `to`, en el campo `from`.

**200 OK** — del mes más reciente al más antiguo:

```json
[
  {
    "periodMonth": "2026-09",
    "currencyCode": "COP",
    "totalSpent": 1830000.0000,
    "budgetAmount": 2500000.0000,
    "remaining": 670000.0000,
    "percentUsed": 73.20,
    "transactionCount": 42,
    "totalIncome": 5300000.0000
  },
  {
    "periodMonth": "2026-08",
    "currencyCode": "COP",
    "totalSpent": 2100000.0000,
    "budgetAmount": null,
    "remaining": null,
    "percentUsed": null,
    "transactionCount": 37,
    "totalIncome": 4500000.0000
  },
  {
    "periodMonth": "2026-07",
    "currencyCode": "COP",
    "totalSpent": 0,
    "budgetAmount": null,
    "remaining": null,
    "percentUsed": null,
    "transactionCount": 0,
    "totalIncome": 4500000.0000
  }
]
```

| Campo | Significado |
|---|---|
| `totalSpent` | Suma de los gastos (`EXPENSE`) confirmados del mes que ya ocurrieron, en la moneda base del usuario con la misma regla que `amountBase` del reporte de movimientos. Ingresos, transferencias, pendientes y programados no cuentan |
| `totalIncome` | Suma de los ingresos (`INCOME`) del mes con las mismas reglas que `totalSpent`: confirmados, ya ocurridos, en la moneda base del usuario. Gastos, transferencias, pendientes y programados no cuentan |
| `budgetAmount` | Meta de gasto total del mes en la moneda base del usuario, o `null` si no hay. Una meta guardada en otra moneda se convierte con la tasa del día 1 de su mes (FIN-29) |
| `remaining` | `budgetAmount - totalSpent`. Negativo si se pasó de la meta. `null` sin meta |
| `percentUsed` | Porcentaje consumido de la meta, con 2 decimales. Puede pasar de 100. `null` sin meta |
| `currencyCode` | La moneda base del usuario |
| `transactionCount` | Cuántos gastos tuvo el mes. Los ingresos no cuentan |

**A tener en cuenta**

- **Los meses vacíos no vienen.** Un mes sin gastos, sin ingresos y sin meta no aparece en el
  arreglo. Si el frontend dibuja una serie continua, tiene que rellenar los huecos con cero. Un mes
  con meta o con ingresos y sin gastos sí viene, con `totalSpent: 0` (FA-132).
- El mes de cada movimiento se decide con la **zona horaria del usuario** (la del registro), no con la
  del servidor ni la del navegador. Un gasto del 31 a las 23:00 en Bogotá es de ese mes aunque en
  UTC ya sea el siguiente.
- Hoy el API no tiene endpoint para crear metas, así que `budgetAmount` llega `null` hasta que se
  carguen por base.

### `GET /api/reports/transactions`

Movimientos confirmados del usuario del token en un rango de días, con sus totales por tipo y por
categoría. Los pendientes no salen hasta aprobarse. Los programados salen marcados con
`"scheduled": true` y no suman en los totales. **Bearer.**

**Query params**

| Param | Formato | Obligatorio |
|---|---|---|
| `from` | `YYYY-MM-DD` | Sí |
| `to` | `YYYY-MM-DD` | Sí |
| `categoryId` | UUID, uno o varios | No |
| `accountId` | UUID, uno o varios | No |
| `type` | `EXPENSE`, `INCOME` o `TRANSFER`, uno o varios, sin distinguir mayúsculas | No |

Los filtros admiten varios valores repitiendo el parámetro (`?type=EXPENSE&type=INCOME`) o
separados por coma (`?type=EXPENSE,INCOME`). Varios juntos se combinan con Y. El rango incluye los
dos extremos. Errores 400 `VALIDATION_ERROR`, en el campo del parámetro:

- Falta `from` o `to`, o no viene como `YYYY-MM-DD` (`2026-10-1`, `2026/10/01`), o el día no existe
  (`2026-02-30`).
- `from` posterior a `to`, en el campo `from`.
- Un `categoryId` o un `accountId` que no es UUID, o un `type` fuera de los tres valores.

**200 OK** — `transactions` del más reciente al más antiguo:

```json
{
  "from": "2026-10-01",
  "to": "2026-10-31",
  "currencyCode": "COP",
  "transactions": [
    {
      "id": "0192a3b4-...",
      "type": "TRANSFER",
      "accountId": "0192a3b4-...",
      "destinationAccountId": "0192a3b4-...",
      "categoryId": null,
      "categoryName": null,
      "amount": 100.0000,
      "currencyCode": "USD",
      "amountBase": 410000.0000,
      "destinationAmount": 410000.0000,
      "description": "Cambio de dólares",
      "notes": null,
      "occurredAt": "2026-10-10T20:00:00Z",
      "scheduled": false,
      "recurrenceId": null,
      "installment": null
    },
    {
      "id": "0192a3b4-...",
      "type": "EXPENSE",
      "accountId": "0192a3b4-...",
      "destinationAccountId": null,
      "categoryId": "0192a3b4-...",
      "categoryName": "Mercado",
      "amount": 85000.0000,
      "currencyCode": "COP",
      "amountBase": 85000.0000,
      "destinationAmount": null,
      "description": "Carne y verduras",
      "notes": null,
      "occurredAt": "2026-10-02T15:00:00Z",
      "scheduled": false,
      "recurrenceId": null,
      "installment": null
    }
  ],
  "totalsByType": [
    { "type": "EXPENSE", "total": 85000.0000, "count": 1 },
    { "type": "INCOME", "total": 0, "count": 0 },
    { "type": "TRANSFER", "total": 410000.0000, "count": 1 }
  ],
  "totalsByCategory": [
    { "categoryId": "0192a3b4-...", "categoryName": "Mercado", "total": 85000.0000, "count": 1 }
  ],
  "net": -85000.0000
}
```

| Campo | Significado |
|---|---|
| `currencyCode` | La moneda base del usuario: la de `amountBase` y la de todos los totales |
| `amount` / `currencyCode` del movimiento | El monto en la moneda de la cuenta origen |
| `amountBase` | El mismo monto en la moneda base. Si el movimiento ya está en esa moneda, es su `amount` tal cual; si no, se convierte con la tasa de su fecha, no con la de hoy. Es lo que suman los totales |
| `destinationAmount` | Solo en una `TRANSFER` entre cuentas de monedas distintas: lo que entró al destino, en la moneda del destino, tal como se registró. `null` en todo lo demás, incluidas las transferencias en la misma moneda, donde entra el mismo `amount`. No suma en ningún total |
| `scheduled` | `true` si el movimiento está programado: sale en la lista, pero no en los totales ni en `net` |
| `recurrenceId` | La serie de la que el movimiento es ocurrencia, o `null`. Sirve para mostrar juntas las ocurrencias de una serie |
| `installment` | `{purchaseId, number, count}` si el movimiento es una cuota de una [compra en cuotas](cuotas.md#compras-en-cuotas) ("3 de 12"), o `null` |
| `sharedBy`, `sharedFrom`, `shares` | Lo compartido del movimiento, con la forma de la respuesta de un movimiento (ver [Lo compartido en la respuesta](compartidos.md#lo-compartido-en-la-respuesta)). `shares` dice a quien pagó el estado de cada parte |
| `totalsByType` | Una entrada por tipo consultado (los tres sin filtro de tipo), aunque sea en cero. Sin los programados |
| `totalsByCategory` | Solo las categorías con movimientos ya ocurridos, de mayor a menor total |
| `net` | Total de `INCOME` menos total de `EXPENSE`. Puede ser negativo |

**A tener en cuenta**

- **Un rango sin movimientos no es 404**: responde 200 con `transactions` vacía, los tipos en cero
  y `totalsByCategory` vacía.
- **Las transferencias no tienen categoría.** Con filtro de categoría quedan fuera, y nunca entran
  en `totalsByCategory`.
- Un `categoryId` bien formado que no es del usuario no da error: el reporte sale vacío.
- Los movimientos de una categoría borrada siguen saliendo, con su nombre, en `transactions` y en
  `totalsByCategory`.
- **`accountId` toma la cuenta como origen o como destino.** El filtro de una cuenta trae también
  las transferencias que le llegan, y una transferencia entre dos cuentas filtradas sale una sola
  vez. Un `accountId` bien formado que no es del usuario, igual que una categoría ajena, deja el
  reporte vacío.
- **Los totales no llevan signo.** Con filtro de cuenta, `TRANSFER` suma lo que entró y lo que
  salió por ella: mide cuánto se movió, no su saldo neto.
- El día de cada movimiento se decide con la **zona horaria del usuario**, igual que el mes en
  `monthly-spending`: un gasto del 31 a las 21:30 en Bogotá es del 31 aunque en UTC ya sea el 1.
- No hay paginación: el rango entero viene en una respuesta.
- **`net` sigue a los filtros.** Se calcula sobre la lista devuelta: un tipo que el filtro deja
  fuera cuenta como cero, así que con `type=EXPENSE` el neto es el gasto con signo negativo, y con
  `type=TRANSFER` es cero.

### `GET /api/reports/balance`

Cuánto entró menos cuánto salió, en un rango y desde siempre, junto con las cuentas activas del
usuario del token. **Bearer.**

**Query params**

| Param | Formato | Obligatorio |
|---|---|---|
| `from` | `YYYY-MM-DD` | Junto con `to` |
| `to` | `YYYY-MM-DD` | Junto con `from` |

Van los dos o ninguno. Sin ninguno, el rango es el mes en curso. Errores 400 `VALIDATION_ERROR`, en
el campo del parámetro:

- Viene uno solo: el error va en el que falta.
- Un día que no viene como `YYYY-MM-DD` o que no existe.
- `from` posterior a `to`, en el campo `from`.

**200 OK**

```json
{
  "currencyCode": "COP",
  "period": {
    "from": "2026-10-01",
    "to": "2026-10-31",
    "income": 5300000.0000,
    "expense": 1905500.0000,
    "net": 3394500.0000
  },
  "allTime": {
    "income": 14300000.0000,
    "expense": 5170500.0000,
    "net": 9129500.0000
  },
  "accounts": [
    {
      "id": "0192a3b4-...",
      "name": "Bancolombia",
      "type": "DEBIT",
      "currencyCode": "COP",
      "currentBalance": 12375000.0000,
      "creditLimit": null,
      "availableCredit": null
    },
    {
      "id": "0192a3b4-...",
      "name": "Visa",
      "type": "CREDIT",
      "currencyCode": "COP",
      "currentBalance": -658000.0000,
      "creditLimit": 5000000.0000,
      "availableCredit": 4342000.0000
    }
  ]
}
```

| Campo | Significado |
|---|---|
| `currencyCode` | La moneda base del usuario: la de `period` y `allTime` |
| `period` | Ingresos, gastos y neto del rango, con las fechas que se usaron |
| `allTime` | Lo mismo con todos los movimientos del usuario, sin importar el rango |
| `net` | `income` menos `expense`. Puede ser negativo |
| `accounts` | Las cuentas activas por nombre, cada una con su saldo en **su** moneda. Las desactivadas no salen |
| `availableCredit` | En una tarjeta, lo que queda por gastar: igual que en `GET /accounts`, `creditLimit` + `currentBalance` menos el capital de las cuotas de [compras en cuotas](cuotas.md#compras-en-cuotas) que todavía no llegan |

**A tener en cuenta**

- **Las transferencias no cuentan** como ingreso ni como gasto: mover plata entre cuentas propias,
  incluido pagar la tarjeta, no cambia el neto. Los gastos pagados con tarjeta sí cuentan.
- **Los pendientes no cuentan** hasta aprobarse, ni en `period` ni en `allTime`.
- **Los programados no cuentan** hasta su fecha, ni en `period`, ni en `allTime`, ni en el
  `currentBalance` de las cuentas: un rango futuro con solo programados sale en cero.
- **Las cuentas no se suman.** Pueden estar en monedas distintas, así que la respuesta no trae un
  saldo total.
- Una tarjeta sin cupo cargado trae `creditLimit` y `availableCredit` en `null`. Se completa con
  `PATCH /api/accounts/{id}`.
- El día de cada movimiento se decide con la zona horaria del usuario, como en
  `reports/transactions`. El mes por defecto sale del reloj del servidor, que hoy está en la misma
  zona.
