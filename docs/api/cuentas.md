# Contrato del API — cuentas

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `GET /api/accounts`

Las cuentas del usuario del token. **Bearer.**

**Query params**

| Param | Valores | Por defecto | Efecto |
|---|---|---|---|
| `includeInactive` | `true` / `false` | `false` | Incluye las cuentas desactivadas |

Cualquier otro valor (`1`, `yes`, `TRUE`) es 400 `VALIDATION_ERROR` en `includeInactive`.

**200 OK** — arreglo, vacío si el usuario no tiene cuentas. Primero las activas, luego por nombre
sin distinguir mayúsculas.

```json
[
  {
    "id": "0199a1b2-...",
    "name": "Tarjeta Visa",
    "type": "CREDIT",
    "currencyCode": "COP",
    "initialBalance": 0.0000,
    "currentBalance": -350000.0000,
    "creditLimit": 5000000.0000,
    "availableCredit": 4650000.0000,
    "statementDay": 15,
    "paymentDueDay": 30,
    "monthlyInterestRate": 2.1500,
    "chargesSingleInstallmentInterest": false,
    "isActive": true
  },
  {
    "id": "0199a1b3-...",
    "name": "Efectivo",
    "type": "CASH",
    "currencyCode": "COP",
    "initialBalance": 100000.0000,
    "currentBalance": 120000.0000,
    "creditLimit": null,
    "availableCredit": null,
    "statementDay": null,
    "paymentDueDay": null,
    "monthlyInterestRate": null,
    "chargesSingleInstallmentInterest": null,
    "isActive": true
  }
]
```

| Campo | Significado |
|---|---|
| `type` | `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`, `INVESTMENT` u `OTHER` |
| `initialBalance` | Saldo con el que la cuenta entró al sistema |
| `currentBalance` | Saldo vigente: `initialBalance` más el efecto de los movimientos que ya ocurrieron; los [programados](transacciones.md#movimientos-programados) no cuentan hasta su fecha. Lo calcula la base y el cliente nunca lo escribe. **En una tarjeta de crédito, negativo es deuda** |
| `creditLimit` | Solo en `CREDIT`: el cupo. `null` en los demás tipos o si la tarjeta no lo tiene |
| `availableCredit` | Solo en `CREDIT`: `creditLimit + currentBalance` menos el capital de las cuotas de [compras en cuotas](cuotas.md#compras-en-cuotas) que todavía no llegan. `null` en los demás tipos o si la tarjeta no tiene cupo |
| `statementDay`, `paymentDueDay` | Solo en `CREDIT`: día de corte y día de pago, 1 a 31. `null` en los demás tipos |
| `monthlyInterestRate` | Solo en `CREDIT`: tasa de interés **mensual en porcentaje** (`2.15` es el 2,15 % mensual, no 0.0215). `null` en los demás tipos o si la tarjeta no la tiene cargada |
| `chargesSingleInstallmentInterest` | Solo en `CREDIT`: `true` si la tarjeta cobra interés en las [compras a 1 cuota](cuotas.md#compras-en-cuotas), `false` si no. Siempre `true` o `false` en una tarjeta; `null` en los demás tipos |
| `isActive` | `false` si la cuenta está [desactivada](#patch-apiaccountsid). Solo sale en `false` con `includeInactive=true` o en la respuesta del parche que la desactivó |

### `POST /api/accounts`

Crea una cuenta del usuario del token. **Bearer.**

**Cuerpo**

| Campo | Tipo | Obligatorio | Reglas |
|---|---|---|---|
| `name` | string | sí | Hasta 80 caracteres. Único por usuario sin distinguir mayúsculas |
| `type` | string | sí | `CASH`, `DEBIT`, `CREDIT`, `SAVINGS`, `INVESTMENT`, `OTHER` |
| `currencyCode` | string | sí | 3 letras, debe existir y estar activa en el catálogo |
| `initialBalance` | number | no | Saldo con el que entra la cuenta. Por defecto `0`. **Solo `CREDIT`** puede ser negativo (la deuda actual de la tarjeta) |
| `creditLimit` | number | no | **Solo `CREDIT`.** Mayor que cero |
| `statementDay` | integer | no | **Solo `CREDIT`.** Día de corte, 1 a 31 |
| `paymentDueDay` | integer | no | **Solo `CREDIT`.** Día de pago, 1 a 31 |
| `monthlyInterestRate` | number | no | **Solo `CREDIT`.** Tasa mensual en porcentaje, de 0 a 10 con hasta 4 decimales. 0 es una tarjeta sin interés |
| `chargesSingleInstallmentInterest` | boolean | no | **Solo `CREDIT`.** Si la tarjeta cobra interés en las compras a 1 cuota. Por defecto `false` |
| `currentBalance` | — | **no se envía** | Si llega, es 400: el saldo vigente lo calcula el sistema. Para fijar el saldo de arranque se usa `initialBalance` |

En una cuenta que no es `CREDIT`, mandar `creditLimit`, `statementDay`, `paymentDueDay`,
`monthlyInterestRate` o `chargesSingleInstallmentInterest` es 400, uno por campo, y un
`initialBalance` negativo es 400 en `initialBalance`. `chargesSingleInstallmentInterest` es 400
también en `false`: fuera de una tarjeta no se envía.

El tope de 10 en la tasa existe para atrapar la tasa **efectiva anual** del extracto (por ejemplo
28,5) escrita en el campo mensual: como mensual daría cuotas con diez veces el interés real.

```json
{
  "name": "Tarjeta Visa",
  "type": "CREDIT",
  "currencyCode": "COP",
  "initialBalance": -350000,
  "creditLimit": 5000000,
  "statementDay": 15,
  "paymentDueDay": 30,
  "monthlyInterestRate": 2.15,
  "chargesSingleInstallmentInterest": true
}
```

**201 Created** — la cuenta con la misma forma de un elemento de `GET /accounts`. `currentBalance`
arranca igual a `initialBalance`.

**Errores:** 400 `VALIDATION_ERROR` por campo, 409 `DUPLICATE_RESOURCE` en `name` si ya existe una
cuenta del usuario con ese nombre.

### `PATCH /api/accounts/{id}`

Modifica una cuenta del usuario del token. **Bearer.** Se envían solo los campos que cambian. Una
cuenta desactivada se modifica igual que una activa.

**Parche**

| Campo | Tipo | Reglas |
|---|---|---|
| `name` | string | Hasta 80 caracteres, no en blanco. Se recorta. Único entre las cuentas vivas del usuario sin distinguir mayúsculas |
| `currencyCode` | string | 3 letras, debe existir y estar activa. **Solo si la cuenta no tiene movimientos** |
| `initialBalance` | number | Hasta 4 decimales. **Corre `currentBalance` en la misma diferencia**, con o sin movimientos. Negativo solo en `CREDIT` |
| `creditLimit` | number | **Solo `CREDIT`.** Mayor que cero |
| `statementDay` | integer | **Solo `CREDIT`.** 1 a 31 |
| `paymentDueDay` | integer | **Solo `CREDIT`.** 1 a 31 |
| `monthlyInterestRate` | number | **Solo `CREDIT`.** Tasa mensual en porcentaje, de 0 a 10 con hasta 4 decimales |
| `chargesSingleInstallmentInterest` | boolean | **Solo `CREDIT`.** Si la tarjeta cobra interés en las compras a 1 cuota. No mueve las compras ya registradas |
| `isActive` | boolean | `false` desactiva la cuenta y `true` la reactiva (ver abajo) |
| `currentBalance`, `type` | — | **No se envían.** Si llegan con valor, 400 en su campo |

- **Ausente o `null` es "no cambia".** El parche no vacía campos: no hay forma de quitarle el cupo,
  las fechas o la tasa a una tarjeta.
- Un parche sin ningún campo modificable (`{}`, o todo en `null`) es 400 en `body`.
- El tipo es el de la cuenta guardada: `creditLimit`, `statementDay`, `paymentDueDay`,
  `monthlyInterestRate` o `chargesSingleInstallmentInterest` sobre una cuenta que no es `CREDIT` son
  400, uno por campo.
- Corregir el saldo inicial no descuadra nada: si la cuenta arrancó en 200000 y gastó 50000 (saldo
  150000), pasar el inicial a 300000 deja el saldo en 250000. En una cuenta que no es `CREDIT`, bajarlo
  hasta dejar el saldo en negativo es 409 `INSUFFICIENT_FUNDS` en `initialBalance`: con esos mismos
  movimientos, el inicial puede bajar hasta 50000 (saldo 0), no más.
- Mandar la moneda que la cuenta ya tiene, en cualquier caja, no es un cambio y no da 409.
- Cambiar solo las mayúsculas del nombre de la propia cuenta no es un choque.

**Desactivar y reactivar (FA-68).** `{"isActive": false}` desactiva la cuenta y `{"isActive": true}`
la reactiva, con cualquier saldo: a favor, en deuda o en cero. El cambio de estado no toca el saldo
ni los movimientos. Pedir el estado que la cuenta ya tiene responde 200 sin cambios. Puede ir junto
con otros campos del parche, y si el parche se rechaza, el estado tampoco cambia.

**Sin series activas (FA-116).** Una cuenta activa sobre la que corre alguna serie activa —las de
`GET /api/recurrences`— no se desactiva: 409 `RESOURCE_IN_USE` en `isActive`, con la cantidad de
series en la `description`, y no se aplica nada del parche. Hay que cancelar cada serie con
`DELETE /api/recurrences/{id}` o moverla a otra cuenta con `PATCH /api/recurrences/{id}`. Una serie
con fin que ya terminó no impide nada.

**Las compras en cuotas no lo impiden (FIN-41).** Una tarjeta con compras activas se desactiva igual,
y las compras no cambian. Una compra no crea movimientos después del alta: sus cuotas ya existen y son
deuda contraída. Cada cuota sigue venciendo en su fecha y bajando el saldo de la tarjeta desactivada,
y la compra sigue en `GET /api/installment-purchases`, donde se edita y se cancela como siempre.

Mientras está desactivada:

- No sale en `GET /api/accounts` salvo con `includeInactive=true`, ni en `accounts` de
  `reports/balance`.
- No se puede elegir como origen ni destino de un movimiento nuevo, una serie o una compra en cuotas:
  400 `VALIDATION_ERROR` en ese campo. Los movimientos que ya tiene se corrigen y se borran como
  siempre.
- Un pendiente con esa cuenta no se puede aprobar: 409 `INVALID_STATE` (ver
  [Movimientos pendientes](transacciones.md#movimientos-pendientes)). Sí se puede rechazar.
- El asistente no la reconoce por su nombre.
- Se sigue pudiendo modificar y borrar.

Al reactivarla, todo vuelve a funcionar como antes.

```json
{
  "name": "Tarjeta Visa Oro",
  "creditLimit": 8000000,
  "paymentDueDay": 28
}
```

**200 OK** — la cuenta completa como quedó, con la misma forma que un elemento de
`GET /accounts`.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 400 | `VALIDATION_ERROR` | `body` | El parche no trae ningún campo modificable |
| 400 | `VALIDATION_ERROR` | el campo | Formato inválido, campo en blanco, campo de crédito o `initialBalance` negativo en una cuenta que no es `CREDIT`, o `currentBalance` o `type` en el cuerpo |
| 400 | `VALIDATION_ERROR` | `currencyCode` | La moneda nueva no existe o no está activa |
| 404 | `NOT_FOUND` | `id` | La cuenta no existe, está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `DUPLICATE_RESOURCE` | `name` | Otra cuenta viva del usuario ya usa ese nombre |
| 409 | `RESOURCE_IN_USE` | `currencyCode` | La cuenta tiene movimientos, como origen o destino. No cambia nada del parche |
| 409 | `RESOURCE_IN_USE` | `isActive` | Se pide desactivar una cuenta activa con series activas. No cambia nada del parche |
| 409 | `INSUFFICIENT_FUNDS` | `initialBalance` | El inicial nuevo dejaría el saldo de una cuenta que no es `CREDIT` por debajo de cero. No cambia nada del parche |

### `DELETE /api/accounts/{id}`

Borra una cuenta del usuario del token. **Bearer.** Es un borrado lógico: la fila se queda, con
todos sus movimientos. Una cuenta desactivada se borra igual que una activa.

**Solo una cuenta en cero.** Con `currentBalance` distinto de cero, a favor o en deuda, responde 409
y no borra nada. Para dejarla en cero: una transferencia a otra cuenta, o corregir su
`initialBalance` con `PATCH /api/accounts/{id}`.

**Sin movimientos programados (FA-115).** Si la cuenta es origen o destino de algún movimiento
[programado](transacciones.md#movimientos-programados) —confirmado con fecha futura—, responde 409 en `id`, con la
cantidad en la `description`, y no borra nada. Los programados no cuentan en `currentBalance`, así
que una cuenta en cero puede tenerlos. Para resolverlos:

- una serie: `DELETE /api/recurrences/{id}`, que se lleva sus ocurrencias futuras;
- una compra en cuotas: `DELETE /api/installment-purchases/{id}`, que se lleva sus cuotas por venir;
- un movimiento suelto, también una transferencia que llega a la cuenta: `DELETE /api/transactions/{id}`.

**Sin series activas (FA-116).** Una serie activa sobre la cuenta también impide el borrado, aunque
el usuario haya borrado a mano su ocurrencia programada. Es el mismo 409 en `id`: un solo error cuya
`description` dice cuántas series activas y cuántos programados tiene, por ejemplo «La cuenta tiene
1 serie activa y 1 movimiento programado». Se resuelve cancelando la serie.

Si la cuenta tiene saldo y además programados o series, la respuesta trae los dos errores. Los
pendientes no impiden el borrado: se borran con la cuenta.

Lo que pasa después:

- Deja de aparecer en `GET /api/accounts`, también con `includeInactive=true`.
- Su nombre queda libre para otra cuenta.
- Sus movimientos no cambian. `GET /api/reports/transactions` los sigue mostrando, también
  filtrando con `accountId` por la cuenta borrada.
- Un `PATCH /api/transactions/{id}` de uno de esos movimientos que no elige cuenta se acepta.
- Ningún movimiento nuevo, ni un PATCH, puede elegirla: es 400 en `accountId` o
  `destinationAccountId`, como con una cuenta que no existe.

**204 No Content** — sin cuerpo.

**Errores propios**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La cuenta no existe, ya está borrada o es de otro usuario: la respuesta es la misma |
| 409 | `RESOURCE_IN_USE` | `currentBalance` | La cuenta tiene saldo distinto de cero |
| 409 | `RESOURCE_IN_USE` | `id` | La cuenta es origen o destino de movimientos programados, o tiene series activas |
