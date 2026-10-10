# Proposal

Origen: FA-108. Salió del mismo pedido hecho desde la raíz de coordinación el 09-10-2026 que dio
FA-105, FA-106 y FA-107:

> […] tambien las tarjetas de credito tener una fecha de corte y el interes y cuando se compra con la
> tarejta de credito colocarle cuotas y se agregan todos, si paga adelantado puede modificarlo
> manualmente sobre los creados, […] una vista de las que siguen activas, si las cuotas de una tarjeta
> ya se pagaron dejaria de aparecer, el modificarlas en grupos, cancelarlas o modificar tener la opcion
> de solo modificar las que todavia no ocurre (mayor a la fecha actual) o modificarlas todas

Decisiones del usuario registradas en la tarea:

- Cada cuota es un movimiento programado. La primera cae en el día de pago del primer corte posterior
  a la compra, y las siguientes mes a mes.
- El interés es de capital fijo: cada cuota es total/N más la tasa mensual de la tarjeta sobre el saldo
  pendiente. Una compra a 1 cuota no lleva interés.
- La compra baja el cupo disponible por el total desde el día de compra, aunque las cuotas todavía no
  cuenten en el saldo.
- Un pago adelantado se registra editando a mano las cuotas creadas.
- Una compra sin cuotas por venir sale de las activas.
- Editar o cancelar en grupo elige alcance, «futuras» o «todas», como en FA-107.

El primer criterio pide decidir el contrato: está en `design.md`.

## Why

Hoy una compra con tarjeta es un gasto único en una cuenta `CREDIT`: no hay manera de diferirla a
cuotas, y `statementDay` y `paymentDueDay` no los usa ninguna regla. FA-105 dejó la tasa mensual en la
tarjeta, FA-106 los movimientos programados que no cuentan hasta su fecha y FA-107 la idea de un grupo
de movimientos con alcance. Con eso alcanza para que una compra cree sus cuotas por adelantado. El front
tiene tres tareas esperando esta.

## What Changes

- Recurso nuevo **compra en cuotas** bajo `/api/installment-purchases`, con el JWT de la API:
  - `POST /preview` calcula las cuotas de una compra sin guardar nada: fecha, capital, interés y total
    de cada una.
  - `POST` registra la compra (`accountId`, `categoryId`, `amount`, `description`, `purchaseDate`,
    `installmentCount`) y crea sus cuotas como gastos confirmados programados, en la misma transacción
    de base. Responde con la compra y el desglose de sus cuotas.
  - `GET` lista las compras con cuotas por venir: cuotas pagadas sobre el total, la próxima y lo que
    falta por pagar.
  - `PATCH /{id}` cambia descripción o categoría con `scope` `FUTURE` o `ALL`.
  - `DELETE /{id}` cancela: borra las cuotas futuras y conserva las pasadas.
- Solo sobre una tarjeta `CREDIT` con `statementDay` y `paymentDueDay`. La primera cuota cae en el día de
  pago del primer corte en o después de la fecha de compra; las siguientes, el mismo día de pago mes a
  mes. Un día que el mes no tiene cae el último día del mes.
- Capital fijo: el total entre N, en pesos enteros y redondeado hacia abajo, con el resto en la última.
  El interés de cada cuota es la tasa mensual de la tarjeta sobre el capital pendiente antes de esa
  cuota. La tasa se copia en la compra al registrarla.
- `availableCredit` descuenta, además del saldo vigente, el capital de las cuotas programadas. Al
  registrar la compra el cupo baja por el total; al llegar cada cuota, su capital deja de estar
  comprometido y la cuota entera entra al saldo.
- Los movimientos ganan `installment` en su respuesta y en el ítem de `reports/transactions`:
  `{purchaseId, number, count}`, o `null` si no son una cuota.
- Una cuota se puede modificar o borrar a mano con `PATCH` o `DELETE /api/transactions/{id}` y sigue
  siendo de su compra.

No es un cambio incompatible: `installment` es un campo nuevo, las rutas son nuevas y `availableCredit`
solo cambia en tarjetas con cuotas, que hoy no existen.

## Capabilities

### New Capabilities

- `cuotas`: simulación, alta, listado de activas, edición en grupo con alcance y cancelación de compras
  en cuotas, la regla de fechas, el cálculo de capital e interés y sus errores.

### Modified Capabilities

- `cuentas`: `availableCredit` descuenta el capital de las cuotas programadas.
- `transacciones`: `installment` en la respuesta de un movimiento.
- `reportes`: `installment` en cada movimiento de `reports/transactions`.

## Fuera de alcance

- Validar que la compra quepa en el cupo disponible. Hoy ningún gasto con tarjeta lo valida.
- Cambiar el monto, el número de cuotas, la tarjeta o la fecha de una compra después del alta. Para
  eso se cancela y se registra de nuevo; un pago adelantado se registra editando las cuotas.
- Recalcular las cuotas si después cambian la tasa, el día de corte o el día de pago de la tarjeta: la
  compra conserva lo que tenía al registrarse.
- Compras con fecha futura: `purchaseDate` es hoy o antes.
- Avances en efectivo, compras en otra moneda y pagos de la tarjeta.
- Qué pasa con una tarjeta que se desactiva o se borra con cuotas por venir. Va al tablero junto a la
  tarea de las series de FA-107.
- El asistente: no registra compras en cuotas.

## Impact

- Esquema: tabla nueva `finance.installment_purchases`; `finance.transactions` gana
  `installment_purchase_id`, `installment_number` e `installment_principal`, con su índice; función
  `finance.committed_credit`. En `schema.sql` y como update en `docs/database/update/`, con su
  reversión. Como modifica una tabla, la base local se recarga después de aplicarlo.
- Datos: `test-data.sql` agrega un usuario con tarjetas para Bruno. `demo-data.sql` agrega una compra en
  cuotas a los usuarios de demo.
- Dominio: el plan de cuotas (fechas, capital e interés) con tests puros. El alcance de FA-107 pasa a
  llamarse `GroupScope` para servir a los dos recursos.
- Aplicación: cinco casos de uso nuevos (simulación, alta, listado, edición, cancelación).
- Persistencia: un adapter nuevo para las compras; `AccountR2dbcAdapter` lee el cupo comprometido;
  `TransactionR2dbcAdapter` y `TransactionReportR2dbcAdapter` leen y escriben la cuota.
- Web: `InstallmentPurchaseController` y sus DTOs; `TransactionResponse` y el ítem del reporte ganan
  `installment`.
- `bruno/`: carpeta nueva `installments/` y el campo nuevo en las listas exactas de claves.
  `bruno-personal/` recibe los requests nuevos.
- `docs/api/contrato-api.md` y `docs/database/modelo-datos.md`.
