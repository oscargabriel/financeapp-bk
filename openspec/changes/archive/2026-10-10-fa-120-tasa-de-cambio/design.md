# Design

Las decisiones de fondo vienen de FA-30 (10-10-2026, cuerpo de la etapa 9): proveedor
ExchangeRate-API, tasa anterior sin límite de antigüedad, sin job. Aquí van las que quedaban para el
change.

## 1. Se guardan las tasas USD→X de todas las monedas activas

Una consulta a `latest/USD` trae todas las monedas. Cuando hace falta llamar al proveedor, se guarda
una fila `USD→X` por cada moneda activa del catálogo distinta de USD que venga en la respuesta, con
`INSERT ... ON CONFLICT DO NOTHING`:

- **Una llamada al día basta para cualquier par.** El proveedor responde 429 si se le consulta de
  más, y sus términos piden una llamada diaria como máximo.
- **El histórico se arma para todas las monedas**, no solo para las que alguien pidió ese día. Eso
  le sirve a la regla de la tasa anterior el día que el proveedor falle.
- **`ON CONFLICT DO NOTHING` resuelve dos cosas.** Dos requests simultáneos que llamen al proveedor
  no fallan por la clave única. Y una fila `MANUAL` de hoy no se pisa con la de la API.

Las monedas inactivas y las que no están en el catálogo no se guardan, porque la FK lo impediría.

**Descartado:** guardar solo el par pedido. Haría falta una llamada por par, y el histórico de las
demás monedas quedaría vacío.

## 2. Toda tasa se calcula contra USD

`from→to = (USD→to) / (USD→from)`, donde `USD→USD = 1`. Cada pata es la fila `USD→X` más reciente
con `rate_date` ≤ la fecha. El cociente se redondea a escala 10, `HALF_EVEN`, que es la escala de
`exchange_rate` en `transactions`, la columna donde FA-51 lo va a guardar. `rateDate` en la
respuesta es la más vieja de las dos patas: dice qué tan antiguo es el dato que se usó.

**Descartado:** preferir una fila directa `from→to` (por ejemplo `EUR→COP MANUAL`) cuando exista.
Mezclaría patas de fechas y fuentes distintas, sin una regla clara de cuál gana. Hoy nadie escribe
filas directas fuera de USD. Si mañana se cargan tasas manuales, se cargan como `USD→X`.

## 3. Cuándo se llama al proveedor

Solo si la fecha pedida es hoy o posterior (hoy según el `Clock` de la app, `app.timezone`) y a
alguna pata le falta su fila con `rate_date` de hoy. Se llama una vez por resolución.

- **Una fecha pasada nunca llama al proveedor.** Su endpoint gratuito no tiene histórico.
- **Una fecha futura usa la tasa de hoy**, que es la más reciente que puede haber.

Una moneda que el proveedor no trae deja su pata sin fila de hoy, así que el siguiente request
vuelve a llamar. No hay enfriamiento tras un fallo: cada request con la pata faltante reintenta, con
el timeout configurado como tope. Con un solo usuario por instancia el volumen no llega al límite
del proveedor. Si llegara, la salida es recordar en memoria el último intento del día; no se
implementa ahora.

## 4. El fallo del proveedor se absorbe en el caso de uso

El adapter traduce cualquier fallo de la llamada a un error, y lo deja en el log con el status o el
tipo de excepción: ni la URL completa ni el cuerpo. El caso de uso lo absorbe con `onErrorResume` y
sigue con lo que haya en la base.

Si no hay tasa, sea porque la fecha es anterior a todas o porque el proveedor falló y no había nada
antes, el error es el mismo: 404 `NOT_FOUND`, campo `date`, sin detalle del proveedor.

**Descartado:** un 502 distinto cuando el proveedor falló. Al cliente no le cambia lo que puede
hacer, y el log ya distingue el caso.

## 5. La respuesta del proveedor se valida en el borde

`ExchangeRateApiAdapter` exige `result = "success"` y `base_code = "USD"`. De `rates` solo toma las
tasas mayores que 0 y menores que 10^10, porque `NUMERIC(20,10)` no admite más de diez dígitos
enteros. Una respuesta que no cumple cuenta como fallo del proveedor (decisión 4).

## 6. El stub

`StubExchangeRateAdapter` devuelve un mapa fijo:

| Moneda | Tasa USD→X |
|---|---|
| COP | 4000 |
| EUR | 0.8 |
| MXN | 18 |
| VES | 200 |
| ARS | 1000 |
| CLP | 900 |
| PEN | 3.5 |
| BRL | 5 |

Lleva `@Profile("!prod")` y se activa con `tasas.proveedor=stub`, como `StubAssistantAdapter`. El
real se activa con `tasas.proveedor=exchangerate-api`, que es el default. Con `prod` y `stub` a la
vez no hay adapter y el contexto no arranca, igual que en el asistente.

## 7. Bruno no depende del proveedor real

`test-data.sql` borra toda `exchange_rates` y siembra sus tasas:

- `USD→COP` de hoy: 4100, `MANUAL`. Ya existía.
- `USD→COP` de hace 30 días: 3900, `MANUAL`. Es nueva.

La tabla no es por usuario, y en la base local la llenan tanto el stub como el proveedor real
cuando el desarrollador levanta la app a mano. Sin el borrado, una fila real de hace 15 días
cambiaría el resultado del escenario de la tasa anterior. Lo que se pierde es el histórico local,
que se rearma solo. En Neon el script no se carga.

Con eso:

| Escenario | Resultado |
|---|---|
| `USD→COP` hoy | 4100, sin proveedor |
| `EUR→COP` hoy | Hay que llamar al stub por la pata de EUR. El stub no pisa la fila `MANUAL` de COP: 4100 / 0.8 = 5125 |
| `USD→COP` hace 10 días | 3900, la tasa anterior sin límite |
| `USD→COP` en 2000-01-01 | 404 |
