# Proposal

Issue **FIN-16** del tablero (migrada de Notion, FA-33), epic **FIN-9 — Finanzas compartidas**. Es la
primera tarea del flujo del epic: el modelo de datos sobre el que construyen FIN-65 (afiliaciones),
FIN-66 (compartir con partes), FIN-67 (saldo entre afiliados) y FIN-68 (filtro y rechazo en lote).

## Why

Hoy la base no tiene con qué representar a otra persona: no hay relación entre usuarios, y un
movimiento pendiente exige cuenta y, si no es transferencia, categoría. Las cuatro tareas de backend
del epic necesitan el mismo modelo, y conviene fijarlo una vez, antes de que la primera construya
encima de `transactions`.

## What Changes

Aplica estas decisiones del epic FIN-9, todas del 13-10-2026:

- un solo modelo: personas afiliadas y gasto repartido con deuda;
- la otra persona se identifica por su correo, y la afiliación pasa por solicitada, aceptada,
  rechazada, cancelada o desvinculada;
- una solicitud a un correo no registrado responde igual que a uno registrado;
- tras desvincular, los pendientes y las partes abiertas siguen;
- cada parte es una fila propia (persona, monto y estado: pendiente, pagada, rechazada, anulada), y la
  deuda abierta es la suma de las partes pendientes;
- la otra persona ve su parte como un gasto pendiente sin cuenta ni categoría;
- aprobar la parte le crea a quien pagó un ingreso pendiente;
- una transferencia compartida le llega al destinatario como ingreso pendiente y no genera deuda;
- borrar un gasto compartido anula las partes pendientes y deja las pagadas;
- los pendientes se filtran por la persona que los compartió.

En concreto:

- **Tabla nueva `finance.affiliations`.** Cada fila es una solicitud, con su estado. Guarda el correo
  escrito y, solo si ese correo tenía cuenta al enviarla, el usuario destinatario. No puede haber dos
  relaciones abiertas (solicitada o aceptada) entre las mismas dos personas, en ningún sentido.
- **Tabla nueva `finance.expense_shares`, las partes.** Una fila por persona que debe una parte de un
  gasto: quién pagó, quién debe, monto, moneda y estado. Guarda copia de la descripción y la fecha
  del gasto, porque la parte pagada sobrevive al borrado del gasto.
- **`finance.transactions` admite pendientes sin cuenta ni categoría.** `account_id` deja de ser
  `NOT NULL`. Un `PENDING` que no es transferencia puede no llevar cuenta ni categoría; un
  `CONFIRMED` sigue exigiendo las dos (o la cuenta destino, si es transferencia). Esto vale para
  cualquier pendiente, sea cual sea su origen, en línea con FIN-58.
- **Dos columnas nuevas en `transactions`:**
  - `shared_by_user_id`: quién compartió el movimiento con el usuario. Sirve para mostrarlo y para
    el filtro de FIN-68.
  - `share_id`: la parte a la que corresponde, si la hay. La llevan el gasto pendiente de quien debe
    y el ingreso pendiente de quien pagó.
- DDL en `schema.sql` y el update `docs/database/update/20261013_01_finanzas_compartidas.sql`, con
  su reversión.
- `docs/database/modelo-datos.md`: diagrama, una sección *Finanzas compartidas* y la sección de
  pendientes al día. «Personas afiliadas y finanzas compartidas entre usuarios» sale de lo pendiente.

Ningún endpoint cambia. La app actual no nombra las tablas ni las columnas nuevas, y ninguna fila
existente queda sin cuenta.

El criterio que pide dejar el modelo «justificado en la memoria del proyecto» se cumple con el
`design.md` de este change y `modelo-datos.md`. En este repo, el porqué de una decisión vive en el
change que la tomó, y la memoria de Claude queda solo para cómo trabajar con el usuario
(`.claude/rules/claude-code.md`, *Reparto con la memoria persistente*). Así se resolvió también en
FIN-47.

## Fuera de alcance

- Endpoints, contrato y colección de Bruno: las reglas que dependen de otras filas son de FIN-65 y
  FIN-66. Ejemplos: que la suma de las partes no supere el total, que solo se comparta con un
  afiliado vigente, que un gasto con partes pendientes no se edite y las transiciones de estado.
- Cómo queda en los libros de quien hace una transferencia compartida. Hoy una `TRANSFER` exige una
  cuenta destino propia, y FIN-66 lo deja a su contrato. Este modelo cubre el lado del destinatario
  (ingreso pendiente con `shared_by_user_id`). Si FIN-66 necesita otra forma, la cambia allí.
- El mecanismo para confirmar o descartar en grupo de FIN-58.
- Cuenta bancaria compartida, compartir con series o cuotas, y avisos por correo (fuera de alcance
  del epic).
- `drop.sql` no lista las tablas ni las funciones agregadas desde septiembre, y su `DROP SCHEMA` sin
  `CASCADE` fallaría. Es anterior a este change y va como tarea propia del tablero: FIN-78.

## Capabilities

### New Capabilities

Ninguna: el change no altera el comportamiento del API (`skip_specs: true`). Las specs las escriben
FIN-65 a FIN-68, cuando exista comportamiento que describir.

### Modified Capabilities

Ninguna.

## Impact

- `docs/database/schema.sql`, `docs/database/update/20261013_01_finanzas_compartidas.sql` y
  `docs/database/modelo-datos.md`.
- **La base local se reinicia**: el update modifica `transactions`, así que tras aplicarlo se corre
  `cargar-datos-local.sql`. El PR lo dice, para que el front sepa que sus datos se recargaron.
- **Neon**: el update se puede aplicar antes o después de cualquier versión de la app. Se aplica al
  promover a `main` y se anota en el registro de `docs/despliegue.md`.
- Las tareas que lean pendientes desde este change tienen que tolerar `account_id` y `category_id`
  nulos. Hoy no existe ninguna fila así; la primera la crea FIN-66.
