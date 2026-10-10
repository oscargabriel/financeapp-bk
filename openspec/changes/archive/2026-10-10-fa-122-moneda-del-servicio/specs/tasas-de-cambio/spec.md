## MODIFIED Requirements

### Requirement: Tasa de un par en una fecha
`GET /api/exchange-rates?from=&to=&date=` SHALL devolver 200 con `{from, to, date, rate, rateDate}`.
`rate` SHALL ser `(USD→to) / (USD→from)` redondeado a 10 decimales. Cada pata SHALL ser la fila
`USD→X` de `exchange_rates` con la `rate_date` más reciente menor o igual a `date`, sin límite de
antigüedad. Si no hay ninguna, SHALL ser la fila `USD→X` más antigua guardada. `USD→USD` vale 1.
`rateDate` SHALL ser la `rate_date` más vieja de las patas usadas. El mismo par SHALL dar `rate` 1 y
`rateDate` igual a `date`. Las filas directas entre dos monedas distintas de USD MUST NOT usarse.

#### Scenario: Tasa del día guardada
- **WHEN** existe `USD→COP` de hoy en 4100 y un usuario autenticado pide `GET /api/exchange-rates?from=USD&to=COP&date=<hoy>`
- **THEN** la respuesta es 200 con `rate` 4100, `rateDate` de hoy, `from` `USD`, `to` `COP` y `date` de hoy

#### Scenario: Tasa cruzada
- **WHEN** existen `USD→COP` 4100 y `USD→EUR` 0.8, las dos de hoy, y se pide `from=EUR&to=COP&date=<hoy>`
- **THEN** la respuesta es 200 con `rate` 5125

#### Scenario: Par inverso
- **WHEN** existe `USD→COP` de hoy en 4000 y se pide `from=COP&to=USD&date=<hoy>`
- **THEN** `rate` es 0.0002500000

#### Scenario: Tasa anterior sin límite de antigüedad
- **WHEN** la única fila `USD→COP` con `rate_date` ≤ hace 40 días es la de hace 45 días, en 3900, y se pide `from=USD&to=COP&date=<hace 40 días>`
- **THEN** la respuesta es 200 con `rate` 3900 y `rateDate` de hace 45 días

#### Scenario: Mismo par
- **WHEN** se pide `from=COP&to=COP&date=2026-01-15`
- **THEN** la respuesta es 200 con `rate` 1 y `rateDate` `2026-01-15`, sin buscar tasas ni consultar al proveedor

#### Scenario: Sin tasa para la fecha
- **WHEN** la fila `USD→COP` más antigua es la de hace 45 días, en 3900, y se pide `from=USD&to=COP&date=2000-01-01`
- **THEN** la respuesta es 200 con `rate` 3900 y `rateDate` de hace 45 días, sin consultar al proveedor

### Requirement: Fallo del proveedor
Un error, una respuesta no exitosa o un timeout del proveedor MUST NOT cortar la resolución. El
fallo SHALL quedar en el log con el status HTTP o el tipo de error, sin el cuerpo de la respuesta, y
la resolución SHALL seguir con las filas guardadas. Si alguna pata no tiene ninguna fila guardada,
la respuesta SHALL ser 404 `NOT_FOUND` en el campo `date`, sin detalle del proveedor.

#### Scenario: Proveedor caído con tasa anterior
- **WHEN** no hay `USD→COP` de hoy, existe la de ayer en 4050, el proveedor responde 500, y se pide `from=USD&to=COP&date=<hoy>`
- **THEN** la respuesta es 200 con `rate` 4050 y `rateDate` de ayer, y el log registra el status 500

#### Scenario: Proveedor caído sin tasa anterior
- **WHEN** no hay ninguna fila `USD→COP` y el proveedor no responde dentro del timeout
- **THEN** la respuesta es 404 `NOT_FOUND` en el campo `date`, y la descripción no menciona al proveedor

#### Scenario: Respuesta del proveedor inválida
- **WHEN** el proveedor responde 200 con `result` distinto de `success`, o con `base_code` distinto de `USD`
- **THEN** se trata como un fallo del proveedor y no se guarda ninguna fila
