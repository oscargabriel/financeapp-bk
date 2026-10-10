# Proposal

Origen: FA-107. Salió del mismo pedido hecho desde la raíz de coordinación el 09-10-2026 que dio
FA-105, FA-106 y FA-108:

> quiero agregar […] gastos recurrentes como suscripciones de netflix o afiliacion de un grupo o algo
> que se cargue cada x tiempo, seria desde semanal hasta anual, el periodo dinamico puede ser por dia
> del mes (1,2,3,4...) dia de la semana (lunes, martes, miercoles...) […] tener una vista que pueda ver
> […] las que siguen activas, […] el modificarlas en grupos, cancelarlas o modificar tener la opcion de
> solo modificar las que todavia no ocurre (mayor a la fecha actual) o modificarlas todas

Decisiones del usuario registradas en la tarea: periodicidad semanal (día de la semana, cada X
semanas) o mensual (día del mes, cada X meses; X = 12 es anual); una serie con fin crea todas sus
ocurrencias al darse de alta, como programadas; una sin fin tiene creada solo la próxima, y al llegar su
fecha aparece la siguiente; editar o cancelar en grupo elige alcance, «futuras» o «todas». El primer
criterio pide decidir el contrato: está en `design.md`.

## Why

Hoy no hay forma de registrar un cargo que se repite: el usuario tiene que crear a mano cada cobro de
Netflix o de la cuota del gimnasio, y no hay vínculo entre ellos para cambiarlos o cancelarlos juntos.
FA-106 ya dejó resuelto que un movimiento con fecha futura no cuenta hasta su fecha, que es lo que
hace falta para crear las ocurrencias por adelantado. El front tiene tres tareas esperando esta.

## What Changes

- Recurso nuevo **serie** bajo `/api/recurrences`, con el JWT de la API:
  - `POST` da de alta una serie con los datos de un gasto o ingreso (`type`, `accountId`,
    `categoryId`, `amount`, `description`), su periodicidad (`frequency`, `interval`, `dayOfWeek` o
    `dayOfMonth`), `startDate` y, opcionalmente, `endDate` u `occurrences`. Crea sus ocurrencias
    como movimientos confirmados, en la misma transacción de base.
  - `GET` lista las series activas, cada una con su `nextOccurrenceAt`.
  - `PATCH /{id}` edita monto, descripción, categoría, cuenta o periodicidad con `scope` `FUTURE` o
    `ALL` en el cuerpo.
  - `DELETE /{id}` cancela: borra las ocurrencias futuras, conserva las pasadas y saca la serie del
    listado.
- Cada ocurrencia cae a las 00:00 del día que le toca, en la zona del usuario. Un día 31 en un mes
  más corto cae el último día del mes.
- Una serie sin fin tiene creadas todas sus ocurrencias hasta hoy más la siguiente. Al leer saldos,
  reportes o el listado de series, el API crea las que falten. No hay ningún proceso programado: el
  servicio puede haber estado apagado (ver `design.md`).
- Los movimientos ganan `recurrenceId` en su respuesta, en el ítem de `reports/transactions` y en
  todo lo que devuelve un movimiento. Es `null` si no son de una serie.
- Una ocurrencia se puede modificar o borrar a mano con `PATCH` o `DELETE /api/transactions/{id}`,
  y sigue siendo de su serie. La serie no vuelve a crear una ocurrencia que se borró a mano.

No es un cambio incompatible: `recurrenceId` es un campo nuevo y las rutas son nuevas.

## Capabilities

### New Capabilities

- `recurrentes`: alta, listado de activas, edición en grupo con alcance y cancelación de series, la
  regla de fechas, la puesta al día de las series sin fin y sus errores.

### Modified Capabilities

- `transacciones`: `recurrenceId` en la respuesta de un movimiento.
- `reportes`: `recurrenceId` en cada movimiento de `reports/transactions`.

## Fuera de alcance

- Las compras en cuotas (FA-108). Esa tarea reutiliza la idea de grupo y de `scope` decidida aquí.
- Cambiar el fin de una serie (`endDate` u `occurrences`) o su tipo después del alta.
- Transferencias recurrentes: una serie es de gasto o de ingreso.
- Un endpoint para listar las ocurrencias de una serie. El front puede filtrar por `recurrenceId`
  lo que ya le da `reports/transactions`.
- Qué pasa con una serie cuando se desactiva o se borra su cuenta o su categoría: hoy la puesta al día
  sigue generando sobre ellas. Va como tarea nueva del tablero, junto a la de borrar una cuenta con
  programados.
- Notas (`notes`) en la plantilla de la serie.
- El asistente: no crea ni edita series.

## Impact

- Esquema: tabla nueva `finance.recurrences`; `finance.transactions` gana `recurrence_id`, con su
  índice. En `schema.sql` y como update en `docs/database/update/`, con su reversión. Como modifica
  una tabla, la base local se recarga después de aplicarlo.
- Datos: `test-data.sql` agrega un usuario con una serie sin fin atrasada, para probar la puesta al
  día desde Bruno. `demo-data.sql` agrega una suscripción mensual a los usuarios de demo.
- Dominio: la serie, su regla de fechas y el alcance, con tests puros.
- Aplicación: cuatro casos de uso nuevos (alta, listado, edición, cancelación) y la puesta al día, que
  llaman los casos de uso de cuentas, saldo, gasto mensual y reportes.
- Persistencia: un adapter nuevo para las series; `TransactionR2dbcAdapter` y
  `TransactionReportR2dbcAdapter` leen `recurrence_id`.
- Web: `RecurrenceController` y sus DTOs; `TransactionResponse` y el ítem del reporte ganan
  `recurrenceId`.
- `bruno/`: carpeta nueva `recurrences/` y el campo nuevo en las listas exactas de claves.
  `bruno-personal/` recibe los requests nuevos.
- `docs/api/contrato-api.md` y `docs/database/modelo-datos.md`.
