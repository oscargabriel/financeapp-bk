# Design

## Context

`current_balance` es una columna que mantienen tres triggers: `trg_accounts_seed_balance` al crear la
cuenta, `trg_accounts_shift_balance` al cambiar el saldo inicial y `trg_transactions_sync_balance` con
cada movimiento confirmado, sin mirar la fecha. Los tres reportes filtran `status = 'CONFIRMED'`, y
tampoco miran la fecha. El servicio corre en Cloud Run con escala de 0 a 1 y no tiene schedulers, así
que nada puede "pasar" un movimiento de programado a vigente en el momento en que llega su fecha.

Hay además un efecto que no está en la tarea pero que la condiciona: `test-data.sql` reparte los
movimientos del mes en curso hasta el día 25, más una cena el último día a las 21:30. Hoy cuentan
todos. Con esta regla, los que caen después del momento de la carga quedarían programados, y las
constantes de unos diez requests de `bruno/` (`reports/`, `monthly-spending/`, `listar-cuentas`)
cambiarían según el día del mes en que se corra la colección.

## Goals / Non-Goals

**Goals:**
- Que lo programado empiece a contar sin ningún proceso, con la instancia apagada o encendida.
- Un solo criterio de "programado" en todo el API: `occurredAt` posterior al momento actual.
- Que la colección de Bruno siga siendo determinista, se corra el día que se corra.

**Non-Goals:**
- Cuotas (FA-108), recurrentes (FA-107) y el cupo de una tarjeta con compras en cuotas.

## Decisions

### El saldo vigente se calcula al leer; la columna sigue incluyendo todo

El trigger no cambia: `current_balance` sigue sumando todo movimiento confirmado, también los de fecha
futura. Una función nueva, `finance.scheduled_balance_delta(account_id)`, devuelve el efecto de los
confirmados de esa cuenta con `occurred_at > now()`, como origen o como destino. `AccountR2dbcAdapter`
lee `current_balance - finance.scheduled_balance_delta(id)`. Es el único adapter que lee saldos: el
listado, el alta, el PATCH, el borrado, la consulta de saldo y el asistente pasan por él.

Así, el paso de programado a vigente lo hace el reloj de la base en cada lectura, y cambiar la fecha
con el PATCH no necesita lógica nueva. El trigger ya revierte la fila vieja y aplica la nueva, y la
resta se recalcula con la fecha nueva. La función solo recorre los movimientos futuros de la cuenta,
que son pocos, por `ix_transactions_account_date` y `ix_transactions_destination_account`.

Descartado:
- **Que el trigger ignore las filas futuras.** Nadie las aplicaría al llegar su fecha sin un proceso
  que corra, y el servicio puede estar apagado.
- **Aplicarlas en cada lectura con un `UPDATE` de las que ya vencieron.** Escribe dentro de un GET,
  necesita un estado más (`SCHEDULED`) y abre carreras entre dos lecturas simultáneas.
- **Quitar la columna y sumar todo al leer.** Es lo más limpio en teoría, pero borra tres triggers y
  toca la regla de borrado, la de saldo inicial y todos los documentos que describen el saldo, a
  cambio de nada que esta tarea necesite.

El precio es que `current_balance` deja de ser el saldo que ve el usuario: su comentario lo dirá, y
`modelo-datos.md` explicará la resta.

### Programado se decide con `now()` en SQL y con el `Clock` en Java

- **SQL**: la función del saldo, las vistas de gasto mensual y la suma de `reports/balance` usan
  `now()` de la base. Las vistas no reciben parámetros, y pasar el instante a la función obligaría a
  enlazarlo también en el `RETURNING` del alta y del PATCH de cuentas.
- **Java**: la marca `scheduled` de un movimiento y los totales de `reports/transactions` usan el
  `Clock` de la aplicación, que es lo que permite probarlos con fecha fija.

Los dos relojes están sincronizados por NTP. Un movimiento que vence entre la consulta y la respuesta
puede salir marcado distinto en dos endpoints durante unos milisegundos, y se aceptó.

### `reports/transactions` lista los programados y no los suma

El front necesita mostrar los programados en Mis Gastos, y Mis Gastos se arma con este reporte: no
hay otro endpoint que liste movimientos. El SQL los trae como a cualquier movimiento confirmado del
rango, y `TransactionReport` arma los totales y el neto solo con los no programados.

Descartado:
- **Excluirlos del reporte.** Mis Gastos no podría mostrarlos, y haría falta un endpoint nuevo.
- **Un parámetro `includeScheduled`.** Un parámetro más para un caso que el front siempre quiere
  ver; si alguna vez sobra, se agrega sin romper nada.

### `scheduled` es un campo calculado, no un estado

`status` sigue siendo `PENDING` o `CONFIRMED`: un pendiente con fecha futura es las dos cosas a la vez,
y un estado nuevo obligaría a cambiarlo cuando llega la fecha, que es justo lo que no se puede hacer
sin un proceso. `scheduled` se calcula en cada respuesta comparando `occurredAt` con el momento
actual. Va en `TransactionResponse` y en el ítem del reporte.

### El escenario de pruebas no tiene movimientos futuros

En `test-data.sql`, cada movimiento del mes en curso (gastos, transferencias y pendientes) pasa a
`LEAST(m0 + desfase, now())`. En los días ya cumplidos del mes no cambia nada. Los que caerían después
de la carga quedan en el momento de la carga, siguen siendo del mes en curso y cuentan. Así las
constantes del mes en curso, del histórico y de los saldos no cambian, se cargue el día que se
cargue.

La cena del último día a las 21:30 prueba la frontera del día en UTC. Esa fila no admite el recorte:
recortada ya no está en la frontera, y sin recortar sería programada casi todo el mes. Por eso la
frontera pasa al mes de hace dos meses, que siempre está cumplido: los `Buses` de 35.000 de ese mes
se mueven a su último día a las 21:30. Ese mes conserva su total y su cantidad de gastos, y la cena
de 50.000 se queda en el mes en curso como un gasto más, recortado. Solo cambian los tres requests de
frontera, que pasan a apuntar a ese mes y a esperar 35.000.

Lo programado se prueba aparte, en una carpeta nueva `bruno/scheduled/` con un usuario `@bruno.local`
propio, para no mover las constantes del escenario.

Descartado: mover la frontera al mes anterior. Cambiaría `saldo-mes-anterior`, `saldo-negativo` y el
gasto mensual del mes anterior, porque ese mes no tiene una fila de 50.000 que se pueda reubicar sin
alterar sus totales.

### Llegar a la fecha, en Bruno

El único criterio que solo verifica la base real es que un programado cuente al llegar su fecha sin
que nadie lo toque. Se prueba con un gasto a 5 segundos en el futuro y `await bru.sleep(6000)` antes
de releer. `bru.sleep` está en el `bru` 4.1.0 instalado, también en el sandbox `safe` (QuickJS),
verificado el 09-10-2026. La carpeta tarda unos 6 segundos más.

## Risks / Trade-offs

- **`current_balance` no es lo que ve el usuario** → quien lea la columna a mano con psql verá el saldo
  con los programados incluidos. Lo dicen el comentario de la columna y `modelo-datos.md`, y
  `scheduled_balance_delta` da la diferencia.
- **Una cuenta borrada con programados** → al llegar su fecha cuentan en los reportes, aunque la
  cuenta ya no aparezca. Va como tarea nueva del tablero.
- **`demo-data.sql` no se recorta** → el front verá programados en el mes en curso y totales del mes
  más bajos que hasta hoy. Es intencional: le sirve para probar la marca.
- **Orden del reporte con filas recortadas** → varias filas del mes en curso pueden quedar con el
  mismo `occurred_at`; el orden secundario por `id` lo mantiene estable.
- **Despliegue**: la función tiene que existir antes que la app que la llama, así que el update va
  ANTES de desplegar. La app vieja con la base nueva sigue funcionando: no llama a la función, y las
  vistas solo dejan de sumar lo futuro, que hoy no existe en producción salvo por carga manual.
