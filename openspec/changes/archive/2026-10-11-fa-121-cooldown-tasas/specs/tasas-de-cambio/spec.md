## MODIFIED Requirements

### Requirement: Consulta al proveedor bajo demanda
Si `date` es hoy o posterior, según la zona `app.timezone`, y a alguna pata le falta su fila con
`rate_date` de hoy, el sistema SHALL consultar una sola vez al proveedor de tasas, salvo que lo
impida el requisito *Límite de consultas al proveedor*. Tras la consulta
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

## ADDED Requirements

### Requirement: Límite de consultas al proveedor
Tras una consulta exitosa, el sistema MUST NOT volver a consultar al proveedor ese día, según la
zona `app.timezone`. Tras una consulta fallida, MUST NOT volver a consultarlo hasta que pase el
intervalo `tasas.reintento-tras-fallo`, de 1 hora por defecto. Mientras no consulte, SHALL resolver
con las filas guardadas, con las mismas reglas que tras un fallo del proveedor. El límite SHALL
valer para toda resolución que consulte al proveedor, sea del endpoint o del alta de movimientos.

#### Scenario: Reintento dentro del intervalo
- **WHEN** a las 10:00 el proveedor falló, existe `USD→COP` de ayer en 4050 y no la de hoy, y a las 10:30 se pide `from=USD&to=COP&date=<hoy>` con el intervalo en 1 hora
- **THEN** el proveedor no se consulta, y la respuesta es 200 con `rate` 4050 y `rateDate` de ayer

#### Scenario: Reintento pasado el intervalo
- **WHEN** a las 10:00 el proveedor falló, sigue sin haber `USD→COP` de hoy, y a las 11:00 se pide `from=USD&to=COP&date=<hoy>` con el intervalo en 1 hora
- **THEN** el proveedor se consulta otra vez

#### Scenario: Moneda que el proveedor no trae
- **WHEN** a las 10:00 el proveedor respondió sin `VES`, existe `USD→VES` de ayer en 190 y no la de hoy, y a las 15:00 se pide `from=USD&to=VES&date=<hoy>`
- **THEN** el proveedor no se consulta, y la respuesta es 200 con `rate` 190 y `rateDate` de ayer

#### Scenario: Día siguiente tras una consulta exitosa
- **WHEN** el proveedor respondió ayer, y hoy a una pata le falta la fila de hoy
- **THEN** el proveedor se consulta otra vez

#### Scenario: Alta de movimientos tras un fallo
- **WHEN** el proveedor falló hace 10 minutos con el intervalo en 1 hora, y se crea un movimiento en una moneda sin fila de hoy
- **THEN** el proveedor no se consulta, y el movimiento se convierte con la tasa anterior guardada
