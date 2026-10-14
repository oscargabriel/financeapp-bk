# Design

## Context

El porqué está en `proposal.md`. Lo que ya existe y condiciona el diseño:

- **Esquema (FIN-16).** `expense_shares` guarda cada parte con su estado y una copia de la descripción
  y la fecha del gasto. `transactions.shared_by_user_id` dice quién compartió y `share_id` de qué
  parte sale. Un `PENDING` que no es transferencia puede no tener cuenta ni categoría
  (`ck_transactions_shape`, `ck_transactions_account`). La tabla de FIN-16 (decisión 5) fija qué
  movimiento lleva qué: el gasto de quien debe y el ingreso de quien pagó llevan `share_id`; el
  ingreso de una transferencia compartida, no.
- **Pendientes (FIN-58).** Todo pendiente tiene grupo (`ck_transactions_group`). Aprobar es un
  `UPDATE` condicionado a `PENDING` que el trigger de saldos aplica; rechazar, un `DELETE`. La
  validación de aprobar vive en `AprobacionDePendientes`, compartida por uno a uno y grupo.
- **Afiliaciones (FIN-65).** El API no expone ids de usuario: la otra persona se ve como
  `{email, firstName, lastName}` dentro de una relación con su `id`.
- **Alta en lote.** `CreateTransactionsUseCase` lee una vez las cuentas, las categorías y las monedas
  del usuario, valida el lote entero, convierte, agrupa los pendientes, valida el saldo y escribe con
  `saveAll` en una sola transacción de base.
- Jackson ignora los campos desconocidos: sin declarar `shares` en las series y las compras en
  cuotas, un `shares` ahí se ignoraría en silencio en vez de dar el error que pide el issue.

## Goals / Non-Goals

**Goals:**

- Que compartir, pagar, rechazar y anular una parte sean cada uno todo o nada, también frente a dos
  peticiones simultáneas de las dos personas.
- Que el front tenga en las respuestas que ya usa todo lo que muestran FIN-70 y FIN-71.
- Ningún cambio de esquema.

**Non-Goals:**

- El saldo neto por persona y el detalle de las partes entre dos personas (FIN-67).
- Filtrar pendientes por persona o rechazarlos en lote (FIN-68).
- Cambiar las partes de un gasto ya registrado.

## Decisions

### 1. La persona se nombra por su afiliación

Cada parte trae `affiliationId`: el `id` de la relación `ACCEPTED` que el front ya tiene de
`GET /api/affiliations`. El caso de uso lee una vez por lote las afiliaciones aceptadas del usuario
(`id` → el otro usuario) y resuelve cada parte contra ese mapa, como hace con las cuentas. Una
afiliación inexistente, ajena o no aceptada da el mismo 400: no revela nada que el usuario no vea en
su listado.

- **Descartado: el correo.** El usuario lo puede cambiar, habría que normalizarlo en cada parte, y
  resolverlo es una consulta a `users` que no dice nada sobre la afiliación.
- **Descartado: el id del usuario.** El API no lo expone en ninguna parte (FIN-65), y exponerlo solo
  para esto rompe esa regla.

### 2. Las partes van en el elemento del lote

`shares` es un campo de cada elemento de `POST /api/transactions`. Así el gasto y sus partes entran
juntos o no entra nada, con los errores indexados del alta (`[i].shares[j].amount`).

- **Descartado: una ruta aparte para agregar partes a un gasto ya creado.** Serían dos peticiones, y
  entre una y otra el gasto existiría sin partes; además abre la puerta a cambiar las partes, que
  nadie pidió.

Las reglas que se deciden mirando solo el elemento —monto de cada parte, afiliación repetida, suma
frente al total, partes en un ingreso, forma de la transferencia compartida— van como constraints
en `CreateTransactionRequest` (FA-55). `@ReglasDeTransferencia` cambia: una transferencia con partes
no lleva destino ni monto de destino y sí categoría. Que la afiliación esté aceptada y la categoría
sea de gasto lo decide el caso de uso, porque necesita la base.

### 3. El alta escribe tres cosas en una transacción

Después de validar y convertir, el caso de uso arma:

1. los movimientos del usuario, como hoy;
2. una `ExpenseShare` por parte de cada gasto, con la copia de la descripción y la fecha;
3. el pendiente de cada persona: sin cuenta ni categoría, `shared_by_user_id` quien paga, `share_id`
   su parte y un grupo propio.

El puerto de persistencia los inserta en ese orden en una sola transacción de base, porque cada uno
referencia al anterior (la parte al gasto, el pendiente a la parte). El saldo se valida solo sobre
lo del usuario: lo de los demás entra pendiente.

Cada pendiente ajeno tiene su propio grupo, aunque salgan del mismo gasto: un grupo es de un usuario
(`findByGroup` filtra por usuario) y lo que recibe cada persona no entró junto con nada suyo.

### 4. La transferencia compartida es un gasto en los libros de quien la hace

Decidido con el usuario al proponer. Hoy una `TRANSFER` exige cuenta destino propia, y el esquema de
FIN-16 no admite una sin destino. Quien la hace la pide como `TRANSFER` sin destino, con sus personas
y una categoría de gasto, y se guarda como `EXPENSE` de esa categoría: baja su saldo y cuenta en el
gasto mensual. Cada persona recibe un ingreso pendiente con `shared_by_user_id` y sin `share_id`,
como dice la tabla de FIN-16. Las partes suman el total: lo que sale de la cuenta es lo que llega a
las personas.

- **Descartado: `TRANSFER` sin cuenta destino.** Pide cambiar `ck_transactions_shape` y el trigger de
  saldos, contra el objetivo de FIN-16, y el dinero saldría de los saldos sin aparecer como gasto ni
  como transferencia entre cuentas propias.
- **Descartado: guardar partes `PAID` para la transferencia.** Ataría el ingreso al gasto, pero
  FIN-67 contaría como pagado algo que nunca fue deuda.

**Precio:** sin `share_id`, nada une el gasto de quien transfirió con el ingreso de quien recibe.
Borrar o editar el gasto no retira ni cambia ese ingreso: el dinero ya salió, y quien lo recibe lo
rechaza si no le llegó. En la respuesta, el gasto de quien transfirió sale con `shares` en `null`: a
quién se transfirió queda en la descripción. Las dos cosas van a `contrato-api.md`.

### 5. Aprobar con cuenta y categoría en la misma ruta

`POST /api/transactions/{id}/approve` acepta un cuerpo opcional `{accountId, categoryId}`. Es
obligatorio si el pendiente no tiene cuenta, y prohibido si ya la tiene (400 en el campo que sobre):
cambiar la cuenta de un pendiente que ya la tiene sigue siendo un `PATCH`.

Las referencias del cuerpo se validan como en el alta, con `ReferenciasDelUsuario`: inexistente,
ajena, desactivada o de otro tipo es 400 `VALIDATION_ERROR` en su campo. Después, el saldo como en
toda aprobación (409 `INSUFFICIENT_FUNDS` en `amount`), con el monto ya convertido.

- **Descartado: `PATCH` para poner la cuenta y luego `approve`.** Dos peticiones para un solo gesto
  del usuario, y el `PATCH` permitiría cambiar el monto de la parte (ver decisión 7).
- **Descartado: una ruta nueva para pagar la parte.** El ingreso de una transferencia compartida y el
  de una parte pagada también llegan sin cuenta, y no son partes que pagar: la ruta de aprobar ya es
  el gesto común.

El 409 de grupo para un pendiente sin cuenta (`[i].accountId`) sale de `AprobacionDePendientes`, antes
de mirar saldos: hoy `SaldoNoNegativo` supone que todo movimiento tiene cuenta.

### 6. La conversión al aprobar usa la tasa de la fecha del pendiente

Si la cuenta elegida está en otra moneda, el monto se convierte con la tasa de la fecha del
pendiente en la zona de la app, redondeado a los decimales de la moneda de la cuenta, como en FA-51,
y el movimiento guarda `original_amount` y `original_currency_code`. A diferencia del alta, no queda
pendiente: el usuario lo está confirmando al elegir la cuenta, y si el banco cobró otra cosa lo
corrige con `PATCH`, que sobre un confirmado sí está permitido. La parte queda pagada por su monto, en
su moneda: la deuda era esa, no su conversión.

- **Descartado: convertir con la tasa del día de la aprobación.** El gasto ocurrió en su fecha, y
  todo lo demás del API convierte con la tasa de la fecha del movimiento.

### 7. Un pendiente compartido solo se aprueba o se rechaza

`PATCH` y `DELETE` sobre un pendiente con `shared_by_user_id` responden 409 `INVALID_STATE` en
`status`. Si quien debe pudiera bajar el monto de su parte y aprobarla, la parte quedaría pagada por
un monto que nadie pagó; y un `DELETE` tendría que hacer lo mismo que rechazar. La regla cubre
también los ingresos (de una parte pagada o de una transferencia) para no tener dos reglas: los dos
se aprueban o se rechazan, y confirmados ya se modifican como cualquier movimiento.

- **Descartado: permitir `PATCH` de la descripción.** No hay quien lo pida, y dejaría una excepción
  dentro de la excepción.

### 8. Pagar, rechazar y anular son una transacción de base cada uno

| Acción | Escrituras, en una transacción |
|---|---|
| Pagar la parte (aprobar el gasto de quien debe) | `UPDATE` del pendiente a `CONFIRMED` con cuenta, categoría, monto y moneda, condicionado a `PENDING`; `UPDATE` de la parte a `PAID`, condicionado a `PENDING`; `INSERT` del ingreso pendiente de quien pagó |
| Rechazar la parte (uno a uno o su grupo) | `UPDATE` de la parte a `REJECTED`, condicionado a `PENDING`; `DELETE` del pendiente |
| Borrar el gasto de quien pagó (`DELETE`, o rechazar o descartar si quedó pendiente) | `UPDATE` de sus partes `PENDING` a `VOIDED`; `DELETE` de los pendientes de esas partes; `DELETE` del gasto |

Las escrituras de rechazar y anular viven en las tres escrituras de borrado del puerto de movimientos
(`deleteByIdAndUser`, `deletePending`, `deletePendingIn`), no en los casos de uso. Borrar un
movimiento arrastra siempre sus partes, igual que la FK arrastra `source_transaction_id`. Así los tres
caminos que borran (el `DELETE`, el rechazo uno a uno y el descarte de grupo) no pueden separarse.

Si una escritura condicionada no afecta su fila, la transacción se deshace y la respuesta es 404, como
`PendienteDelUsuario.exigirEscritura`. Con dos peticiones a la vez, el `UPDATE` de la parte bloquea la
fila: si gana el borrado, el pago no encuentra la parte `PENDING` y responde 404; si gana el pago, el
borrado ya no la ve pendiente y deja el pago intacto.

El ingreso de quien pagó tiene la fecha de la aprobación, no la del gasto: es cuando le pagan, y con
la fecha del gasto caería en un mes que quizá ya cerró. Lleva la descripción de la parte y un grupo
propio.

### 9. Lo que no cambia en un gasto con partes pendientes es el monto y el tipo

El epic bloquea «monto y partes». Las partes no tienen ruta para cambiarlas; el monto y el tipo dan
409 `INVALID_STATE` en su campo. Cambiar el tipo se bloquea porque un gasto que pasa a ingreso o a
transferencia dejaría a las partes sin gasto del que salir. La descripción, la fecha, la cuenta y la
categoría sí cambian, y la copia en la parte no las sigue: la parte registra lo que se compartió.
FIN-16 suponía que el gasto no se editaba en nada; el epic solo pide el monto.

Un gasto compartido convertido de otra moneda tampoco cambia de monto mientras tenga partes
pendientes, aunque FA-51 espere ajustarlo al cargo real: se aprueba como está y se ajusta cuando las
partes se resuelvan.

### 10. Lo compartido de la respuesta lo arma un puerto aparte

`sharedBy`, `sharedFrom` y `shares` salen de un puerto de entrada nuevo que, dada una lista de
movimientos del usuario, devuelve lo compartido de cada uno con una consulta: la persona que compartió
(`users`), el gasto del que sale la parte (`expense_shares` → `transactions`) y las partes del gasto
de quien pagó, con su persona. `TransactionController` y `TransactionReportController` lo consultan
después del puerto principal y arman la respuesta.

- **Descartado: llevarlo en `Transaction` y en todas las consultas del adapter.** Cada lectura de
  movimientos, incluidas las que solo validan, cargaría tres joins más, y el modelo de dominio
  cargaría datos de presentación de otras personas.

`sharedFrom.amount` y `sharedFrom.currencyCode` van en la moneda de la parte: el `original_amount` del
gasto si se convirtió, y si no su `amount`.

### 11. Series y compras en cuotas declaran `shares` para rechazarlo

Los requests de `POST /api/recurrences` y de las dos rutas de compras en cuotas declaran `shares` con
una constraint que solo acepta ausente, `null` o vacío. Es lo mínimo para que el error exista (ver
*Context*).

## Risks / Trade-offs

- **Código que supone que todo movimiento tiene cuenta.** `TransactionResponse.from`,
  `SaldoNoNegativo.efecto` y `AprobacionDePendientes` hacen `accountId()` sin mirar. → Las tareas
  cubren la lista de pendientes, la aprobación uno a uno y en grupo, y el rechazo con un pendiente sin
  cuenta; los reportes, el asistente y `check-saldos.sql` filtran `CONFIRMED` y no lo ven.
- **Sin tope de personas por gasto.** → Lo acotan las afiliaciones aceptadas del usuario y el MB del
  codec. Si hiciera falta, una constraint de tamaño en `shares`.
- **Transferencia compartida sin vínculo.** → Ver decisión 4: queda escrito en el contrato.
- **Un convertido compartido no se ajusta al cargo real mientras haya partes pendientes.** → Ver
  decisión 9.

## Migration Plan

Sin cambios de esquema ni de datos: todo lo que se escribe cabe en FIN-16 y FIN-58. La app se
despliega sola, en cualquier momento. `test-data.sql` no cambia: `bruno/` registra y afilia en cada
corrida a sus usuarios `@bruno.local`, que el script borra en cascada al recargar.
