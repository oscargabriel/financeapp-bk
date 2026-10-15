# Design

## Context

`docs/api/contrato-api.md` tiene hoy: `## Índice` (la tabla de endpoints), `## Generalidades`,
`## Autenticación`, `## Errores` (con `### Saldo no negativo`), `## Endpoints` con 37 secciones
`###` en orden de recurso, `## CORS` y `## Lo que el API todavía no tiene`. Tiene 73 enlaces
internos `](#ancla)`, apuntando a 31 anclas distintas; muchos cruzan de recurso (las transacciones
remiten a `#saldo-no-negativo`, `#series-recurrentes`, `#compras-en-cuotas`,
`#movimientos-compartidos`; el registro remite al login, etc.). Ningún archivo versionado fuera de
los skills y de los changes archivados enlaza una sección del contrato.

## Goals / Non-Goals

**Goals:**
- Que leer el contrato de un recurso cueste lo de ese recurso.
- Que el texto llegue a los archivos nuevos sin una sola edición fuera de los enlaces, y que eso se
  pueda comprobar.

**Non-Goals:**
- Una verificación permanente de los enlaces del contrato en la suite (ver la decisión 5).

## Decisions

### 1. Los archivos se llaman como la capacidad de `openspec/specs/`

`docs/api/cuentas.md` es el contrato de lo que describe `openspec/specs/cuentas/`. Quien trabaja un
change ya sabe qué capacidad toca, así que sabe qué archivo abrir sin consultar el índice.

Descartado: nombres por ruta del API (`accounts.md`, `installment-purchases.md`). Siguen al cliente,
pero obligan a una segunda tabla mental entre la spec en español y el archivo en inglés, y los
recursos sin ruta propia (compartidos) no tendrían nombre natural.

Mapa de secciones a archivos:

| Archivo | Secciones del contrato de hoy |
|---|---|
| `generalidades.md` | `## Generalidades`, `## Autenticación`, `## Errores`, `## CORS`, `## Lo que el API todavía no tiene` |
| `auth.md` | `register`, `login`, Google Identity Services, `google/register`, `google/login`, `refresh`, `logout`, `GET /api/status` |
| `usuarios.md` | Los cuatro de `/api/users/me` |
| `cuentas.md` | Los cuatro de `/api/accounts` |
| `categorias.md` | Los cuatro de `/api/categories` |
| `catalogos.md` | Catálogos |
| `tasas-de-cambio.md` | `GET /api/exchange-rates` |
| `transacciones.md` | `POST`, `PATCH` y `DELETE` de `/api/transactions`, Movimientos pendientes, Movimientos programados |
| `compartidos.md` | Movimientos compartidos |
| `recurrentes.md` | Series recurrentes |
| `cuotas.md` | Compras en cuotas |
| `reportes.md` | `GET /api/monthly-spending`, `GET /api/reports/transactions`, `GET /api/reports/balance` |
| `asistente.md` | `POST /api/assistant/messages` |
| `afiliaciones.md` | Afiliaciones |

Dos excepciones al nombre por capacidad, a propósito: `GET /api/status` va en `auth.md` y no en un
`arranque.md` de 600 caracteres, porque comparte con `/auth/*` la cadena Basic y así lo agrupa el
issue; y `monthly-spending` va en `reportes.md`, que es la spec que lo describe.

### 2. Las transacciones se parten en cuatro archivos

El issue agrupa en «transactions» los pendientes, compartidos, programados, series y cuotas. En un
solo archivo serían unos 45k caracteres, un tercio del contrato de hoy, y el recurso más tocado
seguiría siendo el más caro de leer. Compartidos, series y cuotas tienen spec propia y, las dos
últimas, ruta propia (`/api/recurrences`, `/api/installment-purchases`): van a su archivo.
Pendientes y programados se quedan en `transacciones.md`, porque sus rutas son
`/api/transactions/...` y los describe la spec `transacciones`.

### 3. Lo común va a `generalidades.md`, no al índice

El criterio admite las dos. Con lo común dentro, `contrato-api.md` mediría unos 16k y cualquier
sesión que solo busque qué archivo abrir pagaría también autenticación y errores. Separado, el
índice queda en unos 6k y `generalidades.md` se abre solo cuando hace falta.

Se conservan los encabezados de hoy, incluidos los `##`: `generalidades.md` tiene sus cinco
secciones `##` con sus `###` debajo. En los archivos de recurso, las secciones conservan su
`###` bajo un `# Contrato del API — <recurso>` y una línea que remite al índice y a
`generalidades.md`; se pierde el `## Endpoints` intermedio, que solo agrupaba.

### 4. El corte se hace con un script, no a mano

Un script del scratchpad (fuera del repo) corta el archivo por rangos de líneas según el mapa de la
decisión 1, calcula de qué archivo es cada ancla con la regla de GitHub para los slugs, y reescribe
`](#ancla)` como `](archivo.md#ancla)` solo cuando el ancla quedó en otro archivo. Copiar a mano
2.600 líneas es la forma más probable de perder una.

La comprobación la hace otro script, también fuera del repo:

- cada encabezado `###` del original aparece en exactamente un archivo nuevo, con el mismo texto;
- cada enlace `](archivo.md#ancla)` y `](#ancla)` de `docs/api/` resuelve a un encabezado existente;
- quitando los encabezados y la línea añadidos y deshaciendo la reescritura de enlaces, la unión de
  los archivos reproduce el original línea por línea, salvo `## Endpoints` y el índice.

### 5. La verificación no se queda en la suite

Un test que valide los enlaces del contrato (como `CloudRunConfigTest` lee `application.yaml` del
disco) avisaría cuando un change futuro rompa un enlace entre archivos. Se descarta por ahora: la
suite es de Java y hasta hoy no verifica documentación, y meterla es una decisión del tema, no de
esta tarea. Si al partir aparecen muchos enlaces cruzados frágiles, se propone como tarea nueva.

### 6. El índice conserva la tabla de endpoints, con el archivo de cada uno

La tabla de hoy (método, ruta, autenticación, para qué) gana una columna con el enlace al archivo.
Es lo que permite buscar una ruta (`/api/recurrences`) y saber qué archivo abrir. Debajo, la lista
de archivos con una línea de qué cubre cada uno. El párrafo de cabecera cambia «se actualiza aquí»
por «se actualiza el archivo de su recurso».

## Risks / Trade-offs

- [Un enlace de fuera apunta a `contrato-api.md#seccion` y deja de resolver] → En el repo solo lo
  hacen los changes archivados, que son historia. Los skills los ajusta la raíz.
- [El front lee su copia sin partir hasta que cierre FIN-94] → FIN-94 está bloqueada por esta
  tarea; su copia sigue funcionando entera mientras tanto.
- [Un change futuro edita el índice en vez del archivo del recurso] → El párrafo de cabecera del
  índice dice dónde va cada cambio, y el índice ya no tiene secciones que editar.
