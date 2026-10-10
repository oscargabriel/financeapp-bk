# Proposal

Origen: FA-120, del tablero (Feature, etapa 9). Surgió al refinar la etapa 9 en FA-30 el
10-10-2026, donde se decidieron el proveedor, la regla de la tasa anterior y la consulta bajo
demanda.

## Why

La app solo admite movimientos en COP porque no tiene de dónde sacar una tasa de cambio. FA-51 va a
convertir montos de otras monedas y necesita una pieza que, dado un par de monedas y una fecha,
devuelva la tasa. Esa pieza no existe: `finance.exchange_rates` está en el esquema, pero nada la lee
ni la llena.

## What Changes

- Un caso de uso resuelve la tasa de un par (`from`, `to`) en una fecha:
  - Las dos patas se buscan contra USD: `from→to = (USD→to) / (USD→from)`.
  - Cada pata es la fila `USD→X` de `exchange_rates` con la `rate_date` más reciente menor o igual a
    la fecha, sin límite de antigüedad.
  - El mismo par vale 1, y la pata de USD también.
- Si la fecha es hoy o posterior y a alguna pata le falta la fila de hoy, el caso de uso consulta una
  vez al proveedor. Las tasas `USD→X` de todas las monedas activas del catálogo se guardan con
  `source = 'API'` y `rate_date` de hoy, y después se resuelve.
- Un fallo o timeout del proveedor no corta la resolución. El adapter lo deja en el log (status o
  tipo de error, nunca el cuerpo) y se usa la fila anterior. Si no hay ninguna, el error es 404
  `NOT_FOUND` en el campo `date`.
- El proveedor real es ExchangeRate-API (`https://open.er-api.com/v6/latest/USD`). Su URL y su
  timeout salen de configuración (`tasas.*`), nunca del input.
- Hay un proveedor `stub` con tasas fijas. `verificar-bruno.ps1` lo activa con
  `TASAS_PROVEEDOR=stub` y no existe con el perfil `prod`, igual que el del asistente (FA-77).
- **Nuevo endpoint `GET /api/exchange-rates?from=&to=&date=`**, con JWT. Responde
  `{from, to, date, rate, rateDate}`. No lo pedía la tarea. Se decidió el 10-10-2026, al proponer:
  sin un endpoint, el SQL de la consulta y del guardado no lo ejercitaría nada hasta FA-51, porque la
  suite no tiene base. Además le sirve al front para mostrar conversiones.

FA-120 nombra un «puerto de salida». Aquí la resolución es un **caso de uso** detrás de un puerto de
entrada (`ResolveExchangeRatePort`), apoyado en dos puertos de salida: el repositorio de tasas y el
proveedor. La regla de la tasa anterior y el cuándo consultar son lógica de negocio, y en un adapter
quedarían fuera del alcance de los tests de la suite. FA-51 consume el mismo puerto de entrada.

## Fuera de alcance

- Convertir montos o aceptar movimientos en otra moneda: es FA-51. `MONEDA_UNICA` sigue en COP.
- La atribución visible que exige ExchangeRate-API («Rates By Exchange Rate API»): la muestra el
  front. Su tarea se crea al cerrar FA-51, según está anotado ahí.
- Un movimiento con fecha anterior a la primera tasa guardada: la pregunta quedó abierta en FA-51.
  Aquí responde 404.
- Tasas directas entre dos monedas que no son USD (una fila `EUR→COP`): se ignoran (ver
  `design.md`).
- Cargar o editar tasas a mano por el API.
- Ningún cambio de esquema: `exchange_rates` ya tiene todo lo que hace falta.

## Capabilities

### New Capabilities

- `tasas-de-cambio`: la resolución de la tasa de un par en una fecha y el endpoint que la expone.

### Modified Capabilities

Ninguna.

## Impact

- **API:** un endpoint nuevo de lectura, `GET /api/exchange-rates`. Se documenta en
  `docs/api/contrato-api.md`.
- **Código:**
  - el dominio: `ExchangeRate`, `UsdRate`, `ResolveExchangeRatePort` y dos puertos de salida;
  - `ResolveExchangeRateUseCase`;
  - `ExchangeRateR2dbcAdapter`;
  - `ExchangeRateApiAdapter` y `StubExchangeRateAdapter`;
  - `ExchangeRateController` y su DTO.
- **Configuración:**
  - `tasas.proveedor`, `tasas.exchangerate-api.url` y `tasas.exchangerate-api.timeout` en
    `application.yaml`, todas con default;
  - las mismas en el yaml de test;
  - `TASAS_PROVEEDOR=stub` en `verificar-bruno.ps1`, y el párrafo del stub en `AGENTS.md`.
- **Datos:** `test-data.sql` borra todas las tasas locales y siembra las suyas, para que los
  escenarios de Bruno no dependan de lo que haya guardado el proveedor real en la base local. En
  Neon no se carga.
- **Bruno:** carpeta nueva `bruno/exchange-rates/`, replicada en `bruno-personal/`.
- **Cloud Run:** sin variables nuevas obligatorias. El servicio sale a internet hacia
  `open.er-api.com`.
