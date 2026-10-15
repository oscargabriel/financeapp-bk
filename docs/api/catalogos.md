# Contrato del API — catálogos

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### Catálogos — `GET /api/catalogs/*`

Los valores que aceptan los formularios de cuenta y de movimiento, para que el cliente no los copie
de este documento. **Bearer** en las cuatro rutas. Ninguna tiene parámetros salvo `categories`.

**`GET /api/catalogs/account-types`** y **`GET /api/catalogs/transaction-types`** — 200 OK, en este
orden:

```json
[{ "code": "CASH", "description": "Efectivo" }]
```

| Catálogo | `code` | `description` |
|---|---|---|
| account-types | `CASH` | Efectivo |
| account-types | `DEBIT` | Cuenta débito |
| account-types | `CREDIT` | Tarjeta de crédito |
| account-types | `SAVINGS` | Cuenta de ahorros |
| account-types | `INVESTMENT` | Inversión |
| account-types | `OTHER` | Otra |
| transaction-types | `EXPENSE` | Gasto |
| transaction-types | `INCOME` | Ingreso |
| transaction-types | `TRANSFER` | Transferencia |

El API recibe y devuelve siempre el `code`; `description` es solo para mostrar.

**`GET /api/catalogs/currencies`** — 200 OK, las monedas activas ordenadas por código. Son las que
acepta `currencyCode` en `POST /api/accounts`. El alta de movimientos admite cuentas en cualquiera
de ellas: cada movimiento va en la moneda de su cuenta, y uno que llegue en otra moneda activa se
convierte y queda pendiente (ver [`POST /api/transactions`](transacciones.md#post-apitransactions)). Las
[series recurrentes](recurrentes.md#series-recurrentes) y las [compras en cuotas](cuotas.md#compras-en-cuotas) también admiten
cuentas en cualquiera de ellas, y van en la moneda de su cuenta.

```json
[{ "code": "COP", "name": "Peso colombiano", "symbol": "$" }]
```

**`GET /api/catalogs/categories`** — 200 OK, las categorías del usuario del token como
`{ "id", "name" }`, con el mismo orden y el mismo filtro `appliesTo` que
[`GET /api/categories`](categorias.md#get-apicategories), y el mismo 400 si el valor no es válido. Para mostrar
ícono o color, usa ese endpoint.

```json
[{ "id": "0199a1c0-...", "name": "Mercado" }]
```
