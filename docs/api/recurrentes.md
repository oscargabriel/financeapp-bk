# Contrato del API — series recurrentes

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### Series recurrentes

Un gasto o ingreso que se repite cada cierto número de semanas o de meses: una suscripción, una
cuota de gimnasio, el salario. Sus **ocurrencias** son movimientos normales, con el `recurrenceId` de
la serie, y quedan [programadas](transacciones.md#movimientos-programados) hasta su fecha. **Bearer** en las cuatro
rutas.

**Fechas.** Cada ocurrencia cae a las 00:00 de su día en la zona del usuario (`05:00:00Z` en Bogotá),
así que la de hoy ya ocurrió y cuenta. La primera es el primer día pedido desde `startDate`, incluido;
las siguientes caen cada `interval` semanas o meses. En una mensual el día se mantiene: con
`dayOfMonth` 31, un mes más corto la pone en su último día (31-ene, 28-feb, 31-mar, 30-abr).

**Con fin y sin fin.** Con `endDate` u `occurrences`, el alta crea todas las ocurrencias de una vez.
Sin fin, crea las que van hasta hoy y la siguiente. Las demás aparecen solas: al consultar
`GET /accounts`, `reports/balance`, `reports/transactions`, `monthly-spending` o la lista de series, el
servidor crea antes las que falten, aunque haya estado apagado.

**Cuenta y categoría válidas (FA-116).** Mientras una serie esté activa, su cuenta no se puede
desactivar ni borrar, y su categoría no se puede borrar: 409 en esas rutas. Una serie sin fin de
datos anteriores a esa regla, cuya cuenta ya está desactivada o borrada o cuya categoría ya está
borrada, no crea más ocurrencias y no sale en la lista. Si la cuenta se reactiva, la siguiente
consulta crea las que falten, como con cualquier serie atrasada.

**Ocurrencias sueltas.** Una ocurrencia se corrige o se borra con `PATCH` o `DELETE` sobre
`/api/transactions/{id}`, y sigue siendo de su serie. Una borrada a mano no vuelve a aparecer. Esas
dos rutas sí validan el [saldo](generalidades.md#saldo-no-negativo).

> **Advertencia: las series no validan el saldo.** El alta con un `startDate` pasado y un `PATCH`
> con `scope: ALL` que sube el monto o cambia la cuenta mueven el saldo de una vez, y pueden dejar
> por debajo de cero una cuenta que no es de crédito. Es una excepción provisional a
> [Saldo no negativo](generalidades.md#saldo-no-negativo), pendiente de análisis en FA-143. Tampoco se validan las
> ocurrencias que se crean solas al consultar.

#### `POST /api/recurrences`

```json
{
  "type": "EXPENSE",
  "accountId": "0199a1b3-...",
  "categoryId": "0199a1c0-...",
  "amount": 44900,
  "description": "Netflix",
  "frequency": "MONTHLY",
  "interval": 1,
  "dayOfMonth": 15,
  "startDate": "2026-10-15",
  "occurrences": 12
}
```

| Campo | Obligatorio | Regla |
|---|---|---|
| `type` | Sí | `EXPENSE` o `INCOME`. No hay transferencias recurrentes |
| `accountId` | Sí | Cuenta propia y activa, en una moneda activa. La serie y sus ocurrencias van en la moneda de la cuenta, que se devuelve en `currencyCode` |
| `categoryId` | Sí | Categoría propia que aplique al tipo |
| `amount` | Sí | Mayor que cero, hasta 4 decimales |
| `description` | Sí | Hasta 255 caracteres; se recorta |
| `frequency` | Sí | `WEEKLY` o `MONTHLY`. Anual es `MONTHLY` con `interval` 12 |
| `interval` | No | Cada cuántas semanas (1 a 52) o meses (1 a 12). Por defecto 1 |
| `dayOfWeek` | En `WEEKLY` | `MONDAY` a `SUNDAY`. No va en una mensual |
| `dayOfMonth` | En `MONTHLY` | 1 a 31. No va en una semanal |
| `startDate` | Sí | `YYYY-MM-DD`. Puede ser pasada: las ocurrencias hasta hoy cuentan de una vez |
| `endDate` | No | `YYYY-MM-DD`, incluida, no anterior a `startDate` |
| `occurrences` | No | Total de ocurrencias, de 1 a 500. No va junto con `endDate` |
| `shares` | No | Solo para rechazarlo: una serie no se comparte. Ausente, `null` o `[]`; con alguna parte es 400 en `shares` |

Una serie no crea más de 500 ocurrencias de una vez: con fin, el error va en `endDate`; sin fin y con
un inicio muy atrás, en `startDate`. Una serie que no tendría ninguna ocurrencia antes de su
`endDate` también es error en `endDate`.

**201 Created** — la serie:

```json
{
  "id": "019a2f10-...",
  "type": "EXPENSE",
  "accountId": "0199a1b3-...",
  "categoryId": "0199a1c0-...",
  "amount": 44900.0000,
  "currencyCode": "COP",
  "description": "Netflix",
  "frequency": "MONTHLY",
  "interval": 1,
  "dayOfWeek": null,
  "dayOfMonth": 15,
  "startDate": "2026-10-15",
  "endDate": null,
  "occurrences": 12,
  "nextOccurrenceAt": "2026-10-15T05:00:00Z"
}
```

`nextOccurrenceAt` es la ocurrencia más próxima con fecha posterior al momento actual, en UTC. Es
`null` en una serie con fin a la que ya no le queda ninguna. `startDate` es el inicio de la regla
actual: cambiar la periodicidad lo mueve (ver abajo).

#### `GET /api/recurrences`

**200 OK** — arreglo de series con la forma del alta, de la próxima ocurrencia a la más lejana. Solo
las **activas**: no canceladas y con ocurrencias por venir. Una sin fin lo está mientras su cuenta
esté activa y su categoría viva; una con fin deja de estarlo cuando pasa su última ocurrencia. Sin
series activas, `[]`. Son las mismas series que cuentan los 409 de desactivar o borrar una cuenta y de
borrar una categoría.

#### `PATCH /api/recurrences/{id}`

```json
{ "scope": "FUTURE", "amount": 49900 }
```

`scope` es obligatorio: `FUTURE` cambia solo las ocurrencias con fecha posterior al momento actual;
`ALL`, también las pasadas, y los saldos se recalculan. Lo demás es opcional, y ausente o `null` es
"no cambia": `amount`, `description`, `categoryId`, `accountId`, `frequency`, `interval`, `dayOfWeek`,
`dayOfMonth`. Al menos uno tiene que venir.

- Las ocurrencias del alcance reciben **solo los campos que trae el parche**, también las que se
  editaron a mano. Lo que el parche no trae, lo conservan.
- **Cambiar la periodicidad rehace las futuras, con cualquier `scope`**: se borran y se crean de nuevo
  con la regla nueva, que empieza mañana en la zona del usuario. Las pasadas no cambian de fecha. Una
  serie con `occurrences` conserva el total: las ya ocurridas se descuentan. Una con `endDate` llega
  hasta esa fecha; una sin fin queda con la siguiente.
- La regla se valida sobre la serie resultante: pasar a `MONTHLY` exige `dayOfMonth` en el parche, y
  un `dayOfWeek` en una serie que queda mensual es error.
- La moneda de la serie no cambia: un `accountId` nuevo tiene que estar en ella, o es 400 en
  `accountId` (FA-123).

**200 OK** — la serie como quedó.

#### `DELETE /api/recurrences/{id}`

**204 No Content.** Borra las ocurrencias futuras, conserva las pasadas con su `recurrenceId` y saca la
serie de la lista. No lleva `scope`.

**Errores de las cuatro rutas**

| HTTP | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | El del campo | Cualquier regla de las tablas de arriba |
| 400 | `VALIDATION_ERROR` | `scope` | `PATCH` sin `scope`, o con un valor distinto de `FUTURE` y `ALL` |
| 400 | `VALIDATION_ERROR` | `body` | `PATCH` sin ningún campo que modificar, además de `scope` |
| 400 | `VALIDATION_ERROR` | `id` | El id de la ruta no es un UUID |
| 404 | `NOT_FOUND` | `id` | La serie no existe, es de otro usuario o ya está cancelada |
