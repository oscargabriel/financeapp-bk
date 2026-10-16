# Design

## Context

Inicio arma hoy su pantalla con tres endpoints y calcula el resto en el front (ver proposal.md). Los
datos ya existen detrás de tres puertos de salida del core: `MonthlySpendingQueryPort` (la vista
`v_monthly_spending`, con el mes en la zona del usuario y los montos en la moneda base),
`BalanceQueryPort` (sumas y `debitBalance` de FIN-104) y `AccountQueryPort` (cuentas activas con
saldo y cupo). Los movimientos salen de `TransactionReportQueryPort`, cuyo adapter ya sabe calcular
`amountBase` con `MontoDeLaPersona` y excluir los pendientes. Toda lectura de movimientos llama
antes a `SeriesAlDia.ponerAlDia`.

## Goals / Non-Goals

**Goals:**
- Un pedido para toda la pantalla, con las reglas de pendientes, programados, transferencias, zona
  horaria y moneda base que ya cumplen los otros reportes, sin copiarlas en SQL nuevo cuando ya hay
  una consulta que las cumple.
- Que la lista de gastos no traiga más filas de las que se muestran.

**Non-Goals:**
- Tocar la vista `v_monthly_spending`, `finance.in_currency` o el esquema.
- Cambiar los endpoints existentes.

## Decisions

### Endpoint nuevo `GET /api/reports/home`, no extender los actuales

Se descartó extender `/monthly-spending` (rellenar huecos y agregar la variación) y
`/reports/balance` (agregar el cupo usado): Inicio seguiría haciendo tres pedidos, y los últimos y
más costosos no encajan en ninguno sin un parámetro que solo usaría esta pantalla. Rellenar los
huecos de `/monthly-spending` además cambiaría su contrato («los meses vacíos no vienen») para otros
consumidores. Un endpoint propio de la
pantalla es un pedido en frío contra Cloud Run y Neon en vez de tres, y deja los generales como
están. Va bajo `/reports` porque es un reporte de solo lectura y lo protege la cadena JWT sin tocar
`SecurityConfig`.

Sin parámetros: la pantalla siempre muestra el mes en curso, 6 meses y listas de 5. Con un
parámetro aparecerían errores 400 y validación que nadie pidió; si otra pantalla lo necesita, se
agrega entonces.

### Un caso de uso que compone puertos de salida, no los casos de uso existentes

`GetHomeSummaryUseCase` implementa `GetHomeSummaryPort` y consume los puertos de salida, no los
puertos de entrada de los otros reportes. Llamar a `GetMonthlySpendingPort`, `GetBalancePort` y
`GetTransactionReportPort` haría correr `ponerAlDia` tres veces y resolvería la moneda y el mes por
separado. Con los puertos de salida, `ponerAlDia` corre una vez y después las consultas van en
paralelo con `Mono.zip`.

El cálculo —rellenar la serie, la variación, `shareOfTop` y `creditUsedPercent`— vive en un record
de dominio `HomeSummary` con su método de fábrica, como `Balance.of` y `TransactionReport.of`: es
regla de negocio, se prueba sin Spring y el caso de uso solo orquesta.

### Dos consultas nuevas con `LIMIT` en `TransactionReportQueryPort`

Para los últimos 5 y los 5 más costosos se descartó reutilizar `findByUser` con un rango: los
últimos no tienen rango natural (el front pedía seis meses enteros para quedarse con cinco), y
ordenar por `amountBase` en Java obliga a traer el mes entero. Las dos consultas nuevas viven en el
mismo adapter y reutilizan su `SELECT`, `MontoDeLaPersona.DE_T` y el mapeo de fila, así que las
reglas de pendientes y de monto en la moneda base no se copian:

- `findLatestExpenses(userId, hasta, limite)`: `EXPENSE` confirmados con `occurred_at <= hasta`,
  `ORDER BY occurred_at DESC, id DESC LIMIT`.
- `findTopExpenses(userId, desde, hastaDia, hasta, limite)`: lo mismo dentro de los días del mes en
  la zona del usuario, `ORDER BY amount_base DESC, occurred_at DESC, id DESC LIMIT`.

`hasta` es el instante actual del `Clock`, pasado por el caso de uso: deja fuera los programados en
SQL en vez de filtrar después, que rompería el `LIMIT`. Es la misma frontera que usa
`ReportedTransaction.scheduledAt`.

### El mes en curso en la zona del usuario

`GET /api/reports/balance` y `/monthly-spending` toman el mes por defecto del `Clock` del servidor.
Aquí se toma de `clock.instant()` en la zona del usuario (`ZonaDelUsuario`), porque el front lo
calculaba así y el criterio pide el mes de cada movimiento según esa zona: con un usuario en otra
zona que la del servidor, «este mes» no puede depender del servidor. Cuesta una lectura del perfil.
El rango de días del mes que se pasa a `findSums` y a `findTopExpenses` sale de ese mismo mes.

### Porcentajes a 2 decimales, mitad hacia arriba

`spentChangePercent`, `shareOfTop` y `creditUsedPercent` salen con escala 2 y `HALF_UP`, igual que
`percentUsed` de `/monthly-spending`. Se descartó redondear a entero como hacía el front: el formato
es del front, y un entero perdería información que el front ya no puede recuperar.

### `debitBalance` y cuentas desde los puertos de la consulta de saldo

Se reutiliza `BalanceQueryPort.findSums` aunque calcula también `period` y `allTime`, que Inicio no
usa: el total débito de FIN-104 es la parte cara y no se duplica. Si esas dos sumas pesan, se separa
`debitBalance` en su propio método después.

## Risks / Trade-offs

- [Las consultas nuevas copian la forma del `SELECT` del reporte] → Viven en el mismo adapter y
  componen las mismas constantes; Bruno compara `amountBase` de un gasto en USD con el de
  `/reports/transactions`.
- [Otro reporte cambia sus reglas y este no] → Inicio usa las mismas vistas y consultas; las únicas
  reglas propias (serie, variación, porcentajes) tienen tests de dominio.
- [Carga de `test-data.sql` justo después de medianoche del día 1] → el gasto de «hace una hora» de
  `saldo@` caería en el mes anterior; es la misma salvedad que ya documenta el script para FIN-104.

## Migration Plan

Sin cambios de esquema ni de datos. El endpoint es aditivo: se despliega con el back y el front lo
adopta en FIN-108. Revertir es revertir el commit.
