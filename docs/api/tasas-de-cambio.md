# Contrato del API — tasas de cambio

Parte del [contrato del API](contrato-api.md). Lo común a todos los endpoints —URL base, formato,
autenticación y errores— está en [generalidades.md](generalidades.md).

### `GET /api/exchange-rates`

La tasa de cambio de un par de monedas en una fecha: cuántas unidades de `to` vale una de `from`.
**Bearer.** Sirve para mostrar conversiones, y es la misma tasa con la que el alta de movimientos
convierte lo que llega en otra moneda (FA-51).

| Parámetro | Obligatorio | Formato |
|---|---|---|
| `from` | sí | Código de una moneda activa (`GET /api/catalogs/currencies`) |
| `to` | sí | Código de una moneda activa |
| `date` | sí | `YYYY-MM-DD` |

**200 OK:**

```json
{ "from": "EUR", "to": "COP", "date": "2026-10-10", "rate": 5125.0000000000, "rateDate": "2026-10-10" }
```

- `rate` va con 10 decimales. El mismo par vale 1.
- Toda tasa se calcula contra el dólar: `from→to = (USD→to) / (USD→from)`. Cada lado sale de la tasa
  guardada más reciente con fecha menor o igual a `date`, por vieja que sea. Si no hay ninguna (una
  fecha anterior a la primera tasa guardada), se usa la más antigua (FA-122).
- `rateDate` es la fecha del dato más viejo que se usó. Si es anterior a `date`, la tasa no está al
  día; si es posterior, `date` es anterior a todas las tasas guardadas. Conviene mostrar los dos casos.
- Para hoy o una fecha futura, si falta la tasa del día, el API la pide a ExchangeRate-API y la
  guarda. Si el proveedor no responde, se usa la anterior.

**Atribución obligatoria.** Las tasas vienen del plan gratuito de ExchangeRate-API, cuyos términos
exigen mostrar `Rates By Exchange Rate API` con un enlace a `https://www.exchangerate-api.com`
donde se muestren las tasas.

| Status | `code` | `field` | Cuándo |
|---|---|---|---|
| 400 | `VALIDATION_ERROR` | `from`, `to` o `date` | Falta el parámetro, la moneda no está activa o la fecha no es un día válido |
| 404 | `NOT_FOUND` | `date` | Una de las monedas no tiene ninguna tasa guardada, ni el proveedor la dio |
