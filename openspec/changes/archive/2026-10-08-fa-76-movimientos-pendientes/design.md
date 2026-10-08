# Design

## Context

`transactions` no tiene estado: toda fila mueve saldo desde que se inserta, por
`trg_transactions_sync_balance`, y cuenta en todas las lecturas. Esas lecturas son el reporte de
movimientos, la consulta de saldo de FA-75 y las dos vistas de gasto mensual. FA-77 va a crear
filas a partir de lo que interprete un modelo, y esas filas no pueden tener efecto hasta que el
usuario las revise.

## Decisiones

### 1. Dos estados, y rechazar es borrar

`status VARCHAR(10) NOT NULL DEFAULT 'CONFIRMED'`, con `CHECK (status IN ('PENDING', 'CONFIRMED'))`.
Rechazar un pendiente es un `DELETE`.

- **Descartado: un tercer estado `REJECTED`.** Obligaría a filtrarlo en todas las lecturas, igual
  que el pendiente, solo para guardar algo que nadie lee. Además los movimientos se borran
  físicamente por decisión del modelo (comentario de la tabla en `schema.sql`), y un rechazado
  sería el primer borrado lógico. Si FA-77 necesita aprender de los rechazos, el estado se agrega
  entonces.
- **Descartado: un booleano `is_pending`.** Funciona para dos valores, pero si mañana hay un
  tercero, la columna tiene que cambiar de tipo. Con el texto y un CHECK, basta con cambiar el CHECK.

### 2. El trigger ignora los pendientes

`sync_account_balances` revierte la fila vieja solo si `OLD.status = 'CONFIRMED'`, y aplica la nueva
solo si `NEW.status = 'CONFIRMED'`. Con eso cada operación queda bien sin código propio:

- aprobar es un `UPDATE` de `PENDING` a `CONFIRMED`: no hay nada que revertir y se aplica la fila;
- rechazar es un `DELETE` de un pendiente: no hay nada que revertir;
- un `PATCH` sobre un pendiente no toca saldos;
- un `PATCH` sobre un confirmado funciona como hoy.

- **Descartado: una tabla `pending_transactions` aparte.** Duplicaría las columnas y los CHECK de
  forma (`ck_transactions_shape`, el monto destino), y aprobar sería mover una fila entre tablas.
  Además FA-77 quiere consultar los pendientes con los mismos filtros que los movimientos.
- **Descartado: que la aplicación ajuste el saldo al aprobar.** `current_balance` lo escriben
  solo los triggers (comentario de la columna en `schema.sql`); aprobar no es motivo para romper
  esa regla.

### 3. Las lecturas filtran `status = 'CONFIRMED'`

Llevan el filtro el reporte de movimientos, la consulta de saldo y las dos vistas de gasto mensual.
En la consulta de saldo el filtro va en el `ON` del `LEFT JOIN`, no en el `WHERE`: un usuario que
solo tiene pendientes tiene que seguir saliendo, con ceros.

Las comprobaciones de «la cuenta o la categoría tiene movimientos» (`AccountR2dbcAdapter` y
`CategoryR2dbcAdapter`) **siguen contando los pendientes**. Su motivo es la referencia, no el
saldo: la FK diferida impide borrar la fila referenciada igual, y un pendiente necesita su cuenta y
su categoría para poder aprobarse.

`ix_transactions_expense_report` no cambia. El filtro de estado se evalúa sobre las filas que ya
devuelve el índice, y los pendientes van a ser pocos.

### 4. Acciones con su propia ruta

Las rutas son `GET /api/transactions/pending`, `POST /api/transactions/{id}/approve` (200 con el
movimiento) y `POST /api/transactions/{id}/reject` (204).

- **Descartado: `PATCH /api/transactions/{id}` con `{"status": "CONFIRMED"}`.** El `PATCH` tiene sus
  reglas: un campo ausente se conserva, y las reglas se evalúan sobre el movimiento resultante. Una
  transición de estado mezclada con un parche obliga a decidir qué pasa con
  `{"status": "CONFIRMED", "amount": 0}`. Las acciones con su propia ruta no tienen esa ambigüedad.
- **Descartado: rechazar con `DELETE /api/transactions/{id}`.** Ese endpoint ya borra cualquier
  movimiento, y lo sigue haciendo. El de rechazo existe para que un cliente no borre por error un
  movimiento confirmado cuando creía rechazar un pendiente: sobre un confirmado responde 409.

`GET /transactions/pending` no choca con `/{id}`, porque no hay `GET` sobre un id.

### 5. Errores

| Caso | Respuesta |
|---|---|
| Id que no es UUID | 400 `VALIDATION_ERROR` en `id` |
| Inexistente o de otro usuario | 404 `NOT_FOUND` en `id`, sin distinguirlos, igual que `PATCH` y `DELETE` |
| Ya confirmado | 409 `INVALID_STATE` en `status` |

`INVALID_STATE` es un código nuevo de `ErrorCodes`, porque ninguno de los que hay describe la
categoría.

- **Descartado: `RESOURCE_IN_USE`.** Significa que otra cosa depende del recurso, como una cuenta
  con movimientos. Aquí no hay ninguna dependencia: lo que falla es el estado.
- **Descartado: 404 para un confirmado** («no hay ningún pendiente con ese id»). Esconde un error
  real del cliente, que sí sabe que el movimiento existe, y la tarea pide un error propio para ese
  caso.

### 6. Una lectura y una escritura condicionada

El caso de uso lee el movimiento con `findByIdAndUser` para responder 404 o 409. Después escribe con
la condición repetida en el `WHERE`: `UPDATE ... SET status = 'CONFIRMED' WHERE id AND user_id AND
status = 'PENDING'` al aprobar, y `DELETE ... WHERE ... AND status = 'PENDING'` al rechazar. Si
entre la lectura y la escritura otro request lo aprobó o lo borró, la escritura afecta cero filas y
responde 404, igual que `PATCH` cuando la fila desaparece entre leer y escribir. Así nunca se aplica
dos veces el saldo.

### 7. Los datos de Bruno van en un usuario propio

Se siembra `pendientes@financeapp.local` en `test-data.sql`, con una cuenta, sus categorías y dos
pendientes: un gasto y una transferencia. También lleva un movimiento confirmado, para el caso
«aprobar un confirmado». La carpeta `bruno/pending/` entra con ese usuario.

- **Descartado: sembrarlos en `prueba@`.** Aprobar cambia saldos y totales que verifican
  `accounts/`, `monthly-spending/` y `reports/`. Con un usuario aparte, el orden de las carpetas no
  importa.
- **Precio aceptado:** la carpeta escribe sobre filas sembradas, así que la segunda corrida sin
  recargar falla: el pendiente ya está aprobado. Es la primera carpeta con esa condición. La
  verificación oficial ya recarga (`verificar-bruno.ps1 -RecargarDatos`), y el `docs` de la carpeta
  lo dice. No hay otra forma mientras ningún endpoint cree pendientes. Cuando FA-77 los cree, la
  carpeta puede sembrarlos ella misma y dejar de depender de la recarga.

## Riesgos

- **El update cambia una función que corre en cada movimiento.** Si la app nueva se despliega
  antes del update, el `INSERT` con `status` falla y nada se registra. El encabezado del update dice
  que va **antes**. Al revés no hay riesgo: con el update y la app vieja, todo entra `CONFIRMED` por
  el default, y la función nueva da el mismo resultado que la vieja.
- **Neon no tiene pendientes.** Las filas existentes entran `CONFIRMED` por el default del
  `ADD COLUMN`, que no dispara triggers: ningún saldo cambia al aplicar el update.
