# Contrato del API — movimientos compartidos

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### Movimientos compartidos

Un gasto o una transferencia se comparte con personas **afiliadas** (ver [Afiliaciones](afiliaciones.md#afiliaciones))
indicando en el alta `shares`, una entrada por persona (FIN-66, epic FIN-9):

```json
[
  {
    "type": "EXPENSE",
    "accountId": "0199a1b3-...",
    "categoryId": "0199a1c0-...",
    "amount": 90000,
    "description": "Cena",
    "shares": [
      { "affiliationId": "0199e1a2-...", "amount": 30000 },
      { "affiliationId": "0199e1a3-...", "amount": 30000 }
    ]
  }
]
```

| Campo | Tipo | Reglas |
|---|---|---|
| `affiliationId` | string (UUID) | El `id` de una afiliación `ACCEPTED` del usuario, de [`GET /api/affiliations`](afiliaciones.md#get-apiaffiliations). Una inexistente, ajena, mal formada o en otro estado (solicitada, rechazada, cancelada, desvinculada) da el mismo error |
| `amount` | number | Mayor que cero, hasta 4 decimales. En la moneda en que llega el `amount` del elemento: su `currencyCode`, o la de la cuenta |

- Cualquier número de personas, **sin repetir ninguna**.
- En un gasto, las partes **suman como mucho el total**: lo que sobra es lo que pone quien paga. En
  una transferencia, **suman exactamente el total**.
- Un `INCOME` con partes es un error.
- Todo error de una parte rechaza **el lote entero**, como cualquier error del alta.

#### Gasto compartido

Quien paga registra el total en su cuenta y su categoría: su saldo baja como en cualquier gasto, y el
gasto sale en su respuesta con `shares`. Cada persona recibe un **gasto pendiente** por su parte,
sin cuenta ni categoría, con la descripción y la fecha del gasto y `sharedBy` con quien pagó.
**Mientras esa parte esté pendiente, es deuda** de esa persona con quien pagó.

- **Pagar la parte** es aprobar ese pendiente con su cuenta y su categoría de gasto
  ([`POST /api/transactions/{id}/approve`](transacciones.md#movimientos-pendientes) con cuerpo). Su gasto queda
  confirmado y mueve su saldo, la parte queda `PAID`, y a quien pagó le llega un **ingreso pendiente**
  por el monto de la parte, sin cuenta ni categoría, con la fecha de la aprobación y `sharedBy` con
  quien le pagó. Quien pagó lo aprueba igual, con una cuenta y una categoría de ingreso, o lo
  rechaza: la parte sigue `PAID`.
- **Rechazar la parte** (`POST /api/transactions/{id}/reject`, descartar su grupo o incluirla en un
  rechazo en lote) la deja `REJECTED`: deja de ser deuda, quien pagó la ve rechazada y su gasto no
  cambia.
- Mientras el gasto tenga partes pendientes, **su monto y su tipo no cambian** (`PATCH` responde 409
  `INVALID_STATE` en ese campo). La descripción, la fecha, la cuenta y la categoría sí, pero la parte
  conserva la descripción y la fecha con que se compartió.
- **Borrarlo** (`DELETE`, o rechazarlo si quedó pendiente por venir en otra moneda) anula sus partes
  pendientes (`VOIDED`) y sus pendientes desaparecen; las pagadas y las rechazadas no cambian.
- Las partes no se cambian después del alta.

Un gasto compartido en otra moneda que su cuenta se convierte y entra pendiente para quien paga,
como todo lo convertido. Las partes quedan en la moneda en que se registró, y cada persona la
convierte a la de su cuenta al aprobar.

#### Transferencia compartida

Una `TRANSFER` con `shares` manda dinero a otras personas, no a una cuenta propia. No lleva
`destinationAccountId` ni `destinationAmount`, y sí `categoryId` de una categoría de gasto: **en los
libros de quien la hace queda como un `EXPENSE`** de esa categoría, por el total, con `shares` en
`null`. Cada persona recibe un **ingreso pendiente** por su monto, sin cuenta ni categoría, con
`sharedBy` con quien transfirió. **No genera deuda.**

Nada une el gasto de quien transfirió con esos ingresos: borrarlo o modificarlo no los retira ni los
cambia. Si el dinero no llegó, quien lo recibe rechaza el ingreso. A quién se transfirió queda en la
descripción.

#### Lo compartido en la respuesta

| Campo | En qué movimiento | Forma |
|---|---|---|
| `sharedBy` | Lo que otra persona compartió con el usuario: su parte de un gasto, el ingreso de una transferencia o el de una parte pagada | `{email, firstName, lastName}` de esa persona, con sus datos actuales |
| `sharedFrom` | La parte de un gasto y el ingreso de una parte pagada, mientras el gasto exista | `{description, amount, currencyCode}`: la descripción con que se compartió y el total del gasto, en la moneda de la parte |
| `shares` | El gasto de quien pagó | Lista de `{id, person: {email, firstName, lastName}, amount, currencyCode, status}`, una por parte, con `status` `PENDING`, `PAID`, `REJECTED` o `VOIDED` |

```json
{
  "id": "0199b2d0-...",
  "type": "EXPENSE",
  "accountId": null,
  "categoryId": null,
  "amount": 30000,
  "currencyCode": "COP",
  "description": "Cena",
  "status": "PENDING",
  "groupId": "0199b2d1-...",
  "sharedBy": { "email": "ana@correo.com", "firstName": "Ana", "lastName": "Paga" },
  "sharedFrom": { "description": "Cena", "amount": 90000, "currencyCode": "COP" },
  "shares": null
}
```

Desvincular a una persona no cambia nada de lo que ya existía entre las dos: sus pendientes se
aprueban o se rechazan igual, y el ingreso del pago sigue llegando. Desde ese momento no se comparte
nada nuevo con esa afiliación. Un pendiente compartido es solo de quien lo recibe: para quien lo
compartió es un movimiento ajeno (404).

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `[i].shares[j].amount` | La parte no trae monto, es cero o negativa, o tiene más de 4 decimales |
| 400 | `VALIDATION_ERROR` | `[i].shares[j].affiliationId` | Falta, o no es una afiliación `ACCEPTED` del usuario |
| 400 | `VALIDATION_ERROR` | `[i].shares[j]` | La parte es `null` |
| 400 | `VALIDATION_ERROR` | `[i].shares` | Una persona repetida, partes que superan el total del gasto o no suman el de la transferencia, o partes en un `INCOME` |
| 400 | `VALIDATION_ERROR` | `[i].destinationAccountId` / `[i].destinationAmount` | Una transferencia compartida los trae |
| 400 | `VALIDATION_ERROR` | `[i].categoryId` | Una transferencia compartida no trae categoría, o trae una que no es de gasto |
