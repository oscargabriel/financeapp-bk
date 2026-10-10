# tasas-de-cambio Specification

## Purpose
La tasa de cambio de un par de monedas en una fecha, calculada contra USD con las tasas guardadas en
`exchange_rates` y completada bajo demanda desde ExchangeRate-API. La expone
`GET /api/exchange-rates` y la usa la conversión de movimientos en otra moneda (FA-51).

## Requirements

### Requirement: Tasa de un par en una fecha
`GET /api/exchange-rates?from=&to=&date=` SHALL devolver 200 con `{from, to, date, rate, rateDate}`.
`rate` SHALL ser `(USD→to) / (USD→from)` redondeado a 10 decimales, donde cada pata es la fila
`USD→X` de `exchange_rates` con la `rate_date` más reciente menor o igual a `date`, sin límite de
antigüedad, y `USD→USD` vale 1. `rateDate` SHALL ser la `rate_date` más vieja de las patas usadas.
El mismo par SHALL dar `rate` 1 y `rateDate` igual a `date`. Las filas directas entre dos monedas
distintas de USD MUST NOT usarse.

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
- **WHEN** la única fila `USD→COP` con `rate_date` ≤ hace 10 días es la de hace 30 días, en 3900, y se pide `from=USD&to=COP&date=<hace 10 días>`
- **THEN** la respuesta es 200 con `rate` 3900 y `rateDate` de hace 30 días

#### Scenario: Mismo par
- **WHEN** se pide `from=COP&to=COP&date=2026-01-15`
- **THEN** la respuesta es 200 con `rate` 1 y `rateDate` `2026-01-15`, sin buscar tasas ni consultar al proveedor

#### Scenario: Sin tasa para la fecha
- **WHEN** no hay ninguna fila `USD→COP` con `rate_date` ≤ `2000-01-01` y se pide `from=USD&to=COP&date=2000-01-01`
- **THEN** la respuesta es 404 con un error `NOT_FOUND` en el campo `date`, sin consultar al proveedor

### Requirement: Consulta al proveedor bajo demanda
Si `date` es hoy o posterior, según la zona `app.timezone`, y a alguna pata le falta su fila con
`rate_date` de hoy, el sistema SHALL consultar una sola vez al proveedor de tasas. Tras la consulta
SHALL guardar una fila `USD→X` con `source = 'API'` y `rate_date` de hoy por cada moneda activa del
catálogo, distinta de USD, que traiga la respuesta, y después resolver la tasa. Una fila que ya
exista para ese par y esa fecha MUST NOT sobrescribirse. Una fecha anterior a hoy MUST NOT consultar
al proveedor.

#### Scenario: Falta la pata de hoy
- **WHEN** existe `USD→COP` de hoy pero no `USD→EUR` de hoy, el proveedor responde EUR 0.8, y se pide `from=EUR&to=COP&date=<hoy>`
- **THEN** el proveedor se consulta una vez, queda guardada `USD→EUR` de hoy en 0.8 con `source` `API`, y `rate` es `USD→COP` / 0.8

#### Scenario: Fila manual de hoy
- **WHEN** existe `USD→COP` de hoy en 4100 con `source` `MANUAL`, el proveedor responde COP 4000, y se le consulta por otra moneda
- **THEN** `USD→COP` de hoy sigue en 4100 con `source` `MANUAL`

#### Scenario: Todas las patas de hoy presentes
- **WHEN** existen `USD→COP` y `USD→EUR` de hoy y se pide `from=EUR&to=COP&date=<hoy>`
- **THEN** el proveedor no se consulta

#### Scenario: Fecha futura
- **WHEN** no hay `USD→COP` de hoy y se pide `from=USD&to=COP&date=<mañana>`
- **THEN** se consulta al proveedor, se guarda la fila de hoy y `rateDate` es hoy

#### Scenario: Solo monedas activas del catálogo
- **WHEN** el proveedor responde tasas de monedas que no están activas en el catálogo
- **THEN** solo se guardan las de las monedas activas

### Requirement: Fallo del proveedor
Un error, una respuesta no exitosa o un timeout del proveedor MUST NOT cortar la resolución: el
fallo SHALL quedar en el log con el status HTTP o el tipo de error, sin el cuerpo de la respuesta, y
la resolución SHALL seguir con las filas guardadas. Si no queda ninguna tasa ≤ la fecha, la
respuesta SHALL ser 404 `NOT_FOUND` en el campo `date`, sin detalle del proveedor.

#### Scenario: Proveedor caído con tasa anterior
- **WHEN** no hay `USD→COP` de hoy, existe la de ayer en 4050, el proveedor responde 500, y se pide `from=USD&to=COP&date=<hoy>`
- **THEN** la respuesta es 200 con `rate` 4050 y `rateDate` de ayer, y el log registra el status 500

#### Scenario: Proveedor caído sin tasa anterior
- **WHEN** no hay ninguna fila `USD→COP` y el proveedor no responde dentro del timeout
- **THEN** la respuesta es 404 `NOT_FOUND` en el campo `date`, y la descripción no menciona al proveedor

#### Scenario: Respuesta del proveedor inválida
- **WHEN** el proveedor responde 200 con `result` distinto de `success`, o con `base_code` distinto de `USD`
- **THEN** se trata como un fallo del proveedor y no se guarda ninguna fila

### Requirement: Proveedor configurable
La URL y el timeout del proveedor real SHALL salir de configuración (`tasas.exchangerate-api.url`,
`tasas.exchangerate-api.timeout`), nunca de la petición. `tasas.proveedor` SHALL elegir entre
`exchangerate-api`, el default, y `stub`, que responde tasas fijas. El `stub` MUST NOT existir con
el perfil `prod`.

#### Scenario: Stub fuera de prod
- **WHEN** la app arranca con `tasas.proveedor=stub` y un perfil distinto de `prod`
- **THEN** las consultas al proveedor responden las tasas fijas del stub sin salir a la red

### Requirement: Validación de la consulta
`from`, `to` y `date` SHALL ser obligatorios. `from` y `to` SHALL ser códigos de monedas activas del
catálogo, y `date` una fecha `AAAA-MM-DD`. Cada error SHALL salir como 400 `VALIDATION_ERROR` en el
campo del parámetro.

#### Scenario: Falta un parámetro
- **WHEN** se pide `GET /api/exchange-rates?to=COP&date=2026-10-10`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `from`

#### Scenario: Moneda inexistente o inactiva
- **WHEN** se pide `from=USD&to=XTS&date=2026-10-10` con `XTS` inactiva
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `to`

#### Scenario: Fecha mal formada
- **WHEN** se pide `from=USD&to=COP&date=10-10-2026`
- **THEN** la respuesta es 400 con un error `VALIDATION_ERROR` en el campo `date`

### Requirement: Tasas autenticadas con JWT
`GET /api/exchange-rates` SHALL exigir el JWT de la cadena de la API.

#### Scenario: Sin credenciales
- **WHEN** se pide `GET /api/exchange-rates?from=USD&to=COP&date=2026-10-10` sin `Authorization`
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED`

#### Scenario: Con la credencial Basic
- **WHEN** se pide con la credencial Basic compartida
- **THEN** la respuesta es 401 con un error `UNAUTHENTICATED`
