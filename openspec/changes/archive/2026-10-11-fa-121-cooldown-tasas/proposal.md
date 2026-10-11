## Why

FA-121, que surgió al implementar FA-120 el 10-10-2026 (decisión 3 de su `design.md`).
`ResolveExchangeRateUseCase` llama a ExchangeRate-API cada vez que a una pata le falta la fila de
hoy. Si el proveedor falla, o no trae una moneda activa del catálogo, cada request siguiente vuelve
a llamar y espera el timeout como tope. Con un solo usuario no se llega al 429 del proveedor. Con
más tráfico, o con una moneda que el proveedor nunca devuelve, se le llamaría muchas veces en un
mismo día, y sus términos piden como mucho una llamada diaria.

## What Changes

- Tras una consulta exitosa al proveedor, no se le vuelve a consultar ese día, según la zona
  `app.timezone`, aunque a alguna pata le siga faltando la fila de hoy. Es el caso de una moneda que
  la respuesta no trae: se resuelve con la tasa anterior guardada, o con 404 si no hay ninguna.
- Tras una consulta fallida (error, respuesta inválida o timeout), no se vuelve a consultar durante
  un intervalo configurable, `tasas.reintento-tras-fallo`, de 1 hora por defecto. Mientras tanto se
  resuelve con las filas guardadas, igual que hoy tras un fallo.
- Las dos reglas valen para los dos caminos que consultan al proveedor: `GET /api/exchange-rates`
  y el alta de movimientos en otra moneda (`refreshToday`).
- El registro del último intento vive en memoria, por instancia. No cambia la base ni el contrato
  del API.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `tasas-de-cambio`: la consulta al proveedor bajo demanda queda limitada a una consulta exitosa por
  día y a un intervalo de espera tras un fallo.

## Impact

- Código: `ResolveExchangeRateUseCase` consulta un registro nuevo de los intentos antes de llamar al
  proveedor y le informa el resultado. El puerto, los adapters y el controlador no cambian.
- Configuración: clave nueva `tasas.reintento-tras-fallo` en `application.yaml`
  (`TASAS_REINTENTO_TRAS_FALLO`, default `1h`) y en el `application.yaml` de test. No hace falta
  tocar el servicio de Cloud Run: el default vale para producción. Se copia también a
  `application-local.yaml` y `application-prod.yaml` solo si se quiere otro valor, porque el base
  ya trae el default.
- Bruno: ningún request cambia. La colección tiene que seguir en verde: el stub responde siempre, y
  la primera consulta del día deja guardadas las tasas que usan los demás requests.

## Fuera de alcance

- Coordinar el límite entre varias instancias. El servicio escala de 0 a 1 (FA-48); si sube el
  máximo, cada instancia llevaría su propio registro, y eso iría a una tarea nueva.
- Evitar que dos requests simultáneos consulten a la vez al proveedor en la primera llamada del
  día. `ON CONFLICT DO NOTHING` ya hace inofensiva la segunda escritura (decisión 1 de FA-120).
- Exponer el estado del registro en `/status` o en un endpoint.
