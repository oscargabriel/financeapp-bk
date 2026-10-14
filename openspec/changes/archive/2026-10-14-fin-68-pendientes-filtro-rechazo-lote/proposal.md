# Proposal

Issue **FIN-68** del tablero, epic **FIN-9 — Finanzas compartidas**. Depende de FIN-66 (pendientes
compartidos con `sharedBy`) y de FIN-58 (grupos de pendientes y su descarte), las dos en
`Por revisar`. El front lo consume en FIN-73 («Pendientes: filtrar por persona y rechazar en lote»),
que ya está bloqueado por este issue. Se tomó desde `pendiente`, a pedido del usuario.

## Why

`GET /api/transactions/pending` lista todo junto y los pendientes se rechazan uno a uno, o por grupo
entero (FIN-58). La pantalla de pendientes del front necesita ver solo lo que compartió una persona
—afiliada o ya desvinculada— y rechazar de una vez los que el usuario seleccione, que pueden venir de
grupos distintos.

## What Changes

Aplica estas decisiones del epic FIN-9, del 13-10-2026:

- **los pendientes se filtran por la persona que los compartió y se rechazan en lote**;
- rechazar la parte anula la deuda, y quien pagó la ve rechazada;
- cualquiera de los dos desvincula, y los pendientes que quedan se aprueban o se rechazan igual.

Contrato decidido con el usuario en la conversación del 13-10-2026:

- **Filtro:** `GET /api/transactions/pending?sharedBy=<correo>`. El correo es el de `sharedBy` en la
  respuesta (el actual de la persona), y se normaliza como en el registro: sin espacios alrededor y
  en minúsculas. Devuelve solo los pendientes que esa persona compartió con el usuario, en el mismo
  orden. Un correo sin pendientes da `[]`: no revela si tiene cuenta. Sin el parámetro, la lista es
  la de hoy. Un correo vacío, de más de 255 caracteres o sin formato de correo es 400
  `VALIDATION_ERROR` en `sharedBy`. Se descartó filtrar por `affiliationId`: una persona desvinculada
  que solo deja ingresos pendientes no sale en `/affiliations` ni en `/affiliations/balances`, así
  que el front no tendría con qué id filtrarla; las personas, en cambio, salen de la propia lista.
- **Rechazo en lote:** `POST /api/transactions/pending/reject` con `{"ids": [...]}`, **204**. Cada
  rechazo tiene el efecto del individual: el pendiente se borra, la parte compartida de quien debe
  queda `REJECTED`, y un gasto pendiente de quien pagó anula sus partes pendientes.
- **Todo o nada:** si algún id no es un pendiente del usuario, no se rechaza ninguno. Un id que no
  existe, ya se rechazó o es de otro usuario es 404 `NOT_FOUND` en `ids[i]` —la misma respuesta para
  los tres—; uno propio ya confirmado es 409 `INVALID_STATE` en `ids[i]`. Si hay de los dos, sale el
  404 con los no encontrados.
- **Tope de 100 ids.** Sin `ids`, vacío o con más de 100 es 400 `VALIDATION_ERROR` en `ids`; un id
  repetido, también en `ids`; un elemento `null` o que no es UUID, en `ids[i]`.
- `contrato-api.md` documenta el filtro y la ruta nueva, y quita de «Lo que el API todavía no tiene»
  la línea de rechazar pendientes de grupos distintos; queda solo **aprobar** una selección.

Se reutiliza el descarte de grupo de FIN-58 a nivel de persistencia: el mismo borrado condicionado de
pendientes, que arrastra las partes compartidas en la misma transacción. La ruta de grupo en sí no
sirve, porque la selección cruza grupos.

## Fuera de alcance

- **Aprobar** una selección de pendientes de grupos distintos: el epic solo pide rechazar en lote.
- Filtrar por varias personas a la vez, o pedir solo los pendientes que no son compartidos.
- Filtrar por persona en `GET /api/reports/transactions` u otras listas.
- Lo que el front hace con el filtro y la selección: FIN-73.

## Capabilities

### New Capabilities

Ninguna.

### Modified Capabilities

- `transacciones`: la lista de pendientes acepta el filtro `sharedBy`; se agregan el rechazo en lote,
  sus errores y su autenticación.
- `compartidos`: rechazar en lote el pendiente de una parte la deja `REJECTED`, como el rechazo
  individual.

## Impact

- Módulo `core`: `ListPendingTransactionsPort` y su caso de uso reciben el filtro; un puerto de
  entrada y un caso de uso nuevos para el lote; `TransactionRepositoryPort` suma la lectura de varios
  ids, el filtro por persona y el borrado todo o nada, que reutiliza el SQL y el arrastre de partes de
  `deletePendingIn`. `TransactionController` suma la ruta y el parámetro, con un DTO de request.
  `ErrorCodes` no cambia.
- Sin cambios de esquema: `transactions.shared_by_user_id` y `users.email` ya existen.
- `docs/api/contrato-api.md`, una carpeta nueva en `bruno/` y su réplica en `bruno-personal/`. El
  comentario de `docs/database/test-data.sql` que lista los prefijos de `@bruno.local` suma el de la
  carpeta nueva.
- El front: FIN-73 ya está bloqueado por este issue y cubre la parte del contrato que cambia.
