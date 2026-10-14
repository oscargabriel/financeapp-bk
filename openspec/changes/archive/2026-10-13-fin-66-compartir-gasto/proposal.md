# Proposal

Issue **FIN-66** del tablero, epic **FIN-9 — Finanzas compartidas**. Depende de FIN-16 (modelo de
datos), FIN-65 (afiliaciones) y FIN-58 (pendiente = sin confirmar), los tres en `Por revisar` y
mergeados en `dev`. Lo consumen FIN-70 y FIN-71 en el front, y FIN-67 y FIN-68 en este repo.

## Why

FIN-16 dejó las tablas de partes compartidas y los pendientes sin cuenta ni categoría, y FIN-65 las
afiliaciones, pero ningún endpoint los usa: hoy no hay forma de registrar un gasto entre varias
personas ni de que la otra persona reciba su parte. Es el núcleo del epic; las pantallas del front y
el saldo entre afiliados esperan este contrato.

## What Changes

Aplica estas decisiones del epic FIN-9, todas del 13-10-2026:

- quien pagó registra el total en su cuenta y cada persona queda debiendo su parte, en montos fijos,
  para cualquier número de personas, y la suma no supera el total;
- cada parte es una fila propia con estado (pendiente, pagada, rechazada, anulada), y la deuda abierta
  es la suma de las pendientes;
- la otra persona ve solo su parte, como un gasto pendiente sin cuenta ni categoría;
- aprobar la parte es pagarla: quien debe elige cuenta y categoría, su gasto se confirma, la parte
  queda pagada y a quien pagó le llega un ingreso pendiente por ese monto;
- rechazar la parte anula la deuda, y el gasto de quien pagó no cambia;
- una transferencia compartida le llega al destinatario como ingreso pendiente y no genera deuda;
- un gasto compartido no se edita en monto ni en partes mientras tenga partes pendientes, y borrarlo
  anula las pendientes y deja las pagadas;
- desvincular no cambia los pendientes ni las partes que ya existían.

Lo decidido con el usuario el 13-10-2026 al proponer, que el issue dejaba al contrato:

- **Transferencia compartida:** se pide como `TRANSFER` sin cuenta destino, con las personas y una
  categoría de gasto, y en los libros de quien la hace queda como un **gasto** de esa categoría.
  Cada persona recibe un ingreso pendiente por su monto, y los montos suman el total.
- **Monedas:** un gasto compartido puede venir en cualquier moneda. Las partes quedan en la moneda
  en que se registró, y cada pendiente se convierte a la moneda de la cuenta que se elige al
  aprobarlo, con la tasa de su fecha, guardando el original.
- **Ver las partes:** el gasto de quien pagó trae sus partes, con persona, monto y estado, en toda
  respuesta de un movimiento y en cada fila de `GET /api/reports/transactions`.

En concreto:

- `POST /api/transactions` acepta `shares` en cada elemento de tipo `EXPENSE` o `TRANSFER`: una
  lista de `{affiliationId, amount}`. Cada persona se identifica por su afiliación `ACCEPTED`. Una
  regla incumplida, o una persona no afiliada o desvinculada, rechaza el lote entero.
- Cada parte crea para su persona un gasto pendiente sin cuenta ni categoría, con su propio grupo.
- `POST /api/transactions/{id}/approve` acepta un cuerpo opcional `{accountId, categoryId}`,
  obligatorio para un pendiente que llegó sin cuenta. Aprobar o rechazar la parte de otra persona
  actualiza la parte y, al pagarla, crea el ingreso pendiente de quien pagó.
- Un pendiente compartido solo se aprueba o se rechaza: `PATCH` y `DELETE` responden 409, y
  confirmar su grupo entero también.
- La respuesta de un movimiento y la fila del reporte de movimientos suman `sharedBy`, `sharedFrom` y
  `shares`, y `accountId` puede ser `null` en un pendiente compartido.
- `POST /api/recurrences` y las dos rutas de `POST /api/installment-purchases` rechazan `shares`.
- `contrato-api.md` y `bruno/` al día. «Crear movimientos pendientes por otra vía que el asistente»
  sale de «Lo que el API todavía no tiene».

## Fuera de alcance

- El saldo neto y el detalle por persona en la pantalla de afiliados: FIN-67.
- Filtrar los pendientes por persona y rechazarlos en lote: FIN-68.
- Compartir junto con «Repetir» o con compra en cuotas: fuera de alcance del epic; aquí solo se
  rechaza.
- Cuenta bancaria compartida y avisos por correo: fuera de alcance del epic.
- Cambiar las partes de un gasto ya registrado: ni el epic ni el issue lo piden. Va a «Lo que el API
  todavía no tiene».
- Retirar una transferencia compartida: borrarla en los libros de quien la hizo no quita el ingreso
  pendiente de quien la recibe, que lo rechaza (ver `design.md`, decisión 4).

## Capabilities

### New Capabilities

- `compartidos`: compartir un gasto o una transferencia con personas afiliadas: las partes en el
  alta, el pendiente de cada persona, su aprobación con cuenta y categoría, su rechazo, la deuda, y
  lo que se puede hacer después con el gasto compartido.

### Modified Capabilities

- `transacciones`: la aprobación acepta cuenta y categoría; un pendiente compartido no se modifica,
  no se borra ni se confirma en grupo; la respuesta de un movimiento suma los campos de compartido.
- `reportes`: cada movimiento del reporte trae los campos de compartido.
- `recurrentes`: el alta de una serie rechaza `shares`.
- `cuotas`: el alta y la simulación de una compra en cuotas rechazan `shares`.

## Impact

- Módulo `core`: alta en lote (DTO, validador, caso de uso y persistencia transaccional de partes y
  pendientes ajenos), aprobación y rechazo uno a uno y en grupo, `PATCH` y `DELETE`, un puerto nuevo
  que describe lo compartido de una lista de movimientos para la web, y los controladores de
  movimientos y del reporte.
- Módulo `puente`: el adaptador del asistente arma el comando sin partes.
- Sin cambios de esquema: usa `expense_shares`, `shared_by_user_id`, `share_id` y la holgura de
  `ck_transactions_shape` y `ck_transactions_account` de FIN-16. Tampoco cambian `test-data.sql` ni
  la demo: `bruno/` registra y afilia sus propios usuarios en cada corrida.
- `docs/api/contrato-api.md`, `docs/database/modelo-datos.md` (la sección de finanzas compartidas
  pasa a citar el contrato), `bruno/` y su réplica en `bruno-personal/`.
