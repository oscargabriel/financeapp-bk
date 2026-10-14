# Design

## Context

El porqué está en `proposal.md`. Hoy el estado inicial lo decide `TransactionOrigin.estadoInicial()`:
`TELEGRAM` entra `PENDING` y el resto `CONFIRMED`, salvo lo que `CreateTransactionsUseCase` convierte
de otra moneda, que `Borrador.convertido` deja `PENDING` (FA-51). El asistente entra por el alta en
lote del core a través de `PendienteDelAsistenteAdapter` (puente), con un lote de uno. Aprobar es un
`UPDATE` a `CONFIRMED` que el trigger de saldos aplica, y rechazar es un `DELETE`
(`modelo-datos.md`, *Movimientos pendientes*).

FIN-16 ya dejó el esquema listo para pendientes sin cuenta ni categoría de cualquier origen
(`ck_transactions_shape`, `ck_transactions_account`), pensando en este change.

Quien va a usar el grupo:

- El bot (FIN-61) muestra lo que registró el asistente con botones Confirmar y Descartar. El
  `callback_data` de un botón de Telegram admite 64 bytes: cabe un UUID (36), no una lista de ids.
- El correo (FIN-12) y las partes compartidas (FIN-66) van a crear pendientes desde el core, sin
  pasar por el asistente.
- El front lista los pendientes y los aprueba uno a uno (`pendientes-api.ts`).

## Goals / Non-Goals

**Goals:**

- Un solo mecanismo de pendientes para todos los orígenes: quien registra dice si necesita
  confirmación, y todo pendiente se puede confirmar o descartar por su grupo.
- Que confirmar un grupo sea todo o nada, con las mismas validaciones que aprobar uno a uno.

**Non-Goals:**

- Que el grupo tenga atributos propios (quién lo creó, cuándo vence, un texto): no hay quien los
  lea todavía.
- Aprobar eligiendo cuenta o categoría: FIN-66.

## Decisions

### 1. El grupo es una columna de `transactions`

`finance.transactions.group_id UUID NULL`, con un índice `(user_id, group_id) WHERE group_id IS NOT
NULL`, y `ck_transactions_group CHECK (status = 'CONFIRMED' OR group_id IS NOT NULL)`. El id es un
UUID v7 que genera la aplicación al registrar. El grupo se conserva al confirmar: así un grupo ya
confirmado da 409 y no 404, igual que un movimiento.

- **Descartado: confirmar por una lista de ids en el cuerpo.** No cabe en el botón del bot, y el
  front y el correo tendrían que reconstruir qué entró junto.
- **Descartado: tabla `pending_groups`.** No hay nada del grupo que guardar aparte de sus
  movimientos, y una tabla más pediría su propio borrado cuando el último miembro se va.
- **Descartado: grupo solo para lo del asistente.** Ataría otra vez el mecanismo al origen. El
  `CHECK` obliga también a FIN-66 y FIN-12 a crear sus pendientes con grupo.

### 2. Las rutas viven en el core, bajo `/transactions/groups`

```
POST /api/transactions/groups/{groupId}/approve  → 200 con la lista confirmada
POST /api/transactions/groups/{groupId}/reject   → 204
```

Siguen el vocabulario de `/{id}/approve` y `/{id}/reject`. No chocan con ellas: tienen un segmento
más.

- **Descartado: rutas en `/assistant`** que respondieran con un `message` («Se guardaron 2
  movimientos»). El correo y las partes compartidas no son del asistente. El bot (FIN-61) arma su
  texto con la lista confirmada que devuelve el core, como arma el resto de lo que muestra.

### 3. El estado inicial lo pide quien registra

`CreateTransactionsPort.create` recibe el `TransactionStatus` inicial: el controlador web pide
`CONFIRMED` y el puente del asistente `PENDING`. Lo convertido de otra moneda queda `PENDING` igual,
como hoy. `TransactionOrigin.estadoInicial()` se borra: el origen solo dice por dónde entró.

Después de convertir, si el lote tiene pendientes, todos los de ese lote reciben un mismo `groupId`
nuevo. Un lote sin pendientes no genera grupo.

- **Descartado: un método aparte `createPending`.** Duplicaría la lectura de referencias, la
  conversión y la validación de saldo, que son las mismas.

### 4. El orden del grupo es el orden en que entró, por `id`

Los errores de confirmar un grupo llevan el índice del pendiente (`[1].amount`), y el cliente tiene
que poder ubicarlo. El orden del grupo es por `id` ascendente, y el alta asigna ids crecientes en el
orden del lote: genera los N ids de una vez y los ordena antes de repartirlos. Hoy dos ids del mismo
milisegundo salen en orden aleatorio, porque `UuidV7` llena con azar todo lo que sigue al tiempo. Así
el índice de un error coincide con la posición en `group.transactions` de la respuesta del asistente.

- **Descartado: identificar el pendiente por su id en `field`** (`<id>.amount`). Rompe la forma
  `[i].campo` que ya usa el alta en lote, y el handler y los clientes la tratan igual.

### 5. Confirmar actúa sobre lo que queda pendiente del grupo

El grupo se lee entero (todos sus movimientos del usuario, por `id`). Sin filas: 404 `NOT_FOUND` en
`groupId`, que cubre el inexistente, el ajeno y el ya descartado. Con filas pero ninguna pendiente:
409 `INVALID_STATE` en `status`. Con pendientes, se validan y confirman solo esos. Lo aprobado uno a
uno no se toca, y lo rechazado uno a uno ya no está.

La escritura es un `UPDATE ... WHERE id = ANY(:ids) AND status = 'PENDING'` dentro de una
transacción de base: si actualiza menos filas que las validadas (otro request aprobó o borró uno en
medio), hace rollback y responde 404, como `PendienteDelUsuario.exigirEscritura`. Descartar es un
`DELETE` con la misma condición; si no borra nada, 404.

### 6. Una sola validación de aprobación, uno a uno y en grupo

La validación de `ApprovePendingTransactionUseCase` pasa a una clase del paquete `usecase` que
reciben los dos casos de uso. Lee las cuentas del usuario (activas o no, con su saldo) y sus
categorías vivas, y aplica en este orden:

1. `INVALID_STATE` por cada cuenta desactivada (`accountId`, `destinationAccountId`) y por cada
   categoría que no esté viva o no aplique al tipo (`categoryId`). Si hay alguno, 409 con todos.
2. Saldo no negativo con `SaldoNoNegativo`: `exigir` sobre el efecto del pendiente suelto, en
   `amount`; `exigirLote` sobre el grupo, que ya produce `[i].amount` y suma el efecto conjunto.

Uno a uno, el campo va sin prefijo; en grupo, con `[i].`. La categoría es nueva también en la
aprobación uno a uno: hoy aprobar un gasto de una categoría borrada lo confirma con ella. Con la regla
de `categorias` que impide cambiar el `appliesTo` de una categoría con movimientos, el único caso que
se da es la categoría borrada.

- **Descartado: validar la categoría solo en el grupo.** Dos reglas para aprobar lo mismo, según el
  botón.

### 7. Varios movimientos: una llamada a `crear_movimiento` por movimiento

Gemini devuelve varias partes `functionCall` en una misma respuesta. El adapter las junta todas y
`FuncionesDelAsistente` decide: si alguna es `crear_movimiento`, la decisión es `CrearMovimientos`
con todas esas, en orden; si no, la primera llamada, como hoy. La regla 2 de `instrucciones.txt` pasa
a pedir una llamada por movimiento, y una sola para consultar. El stub entiende una llamada por línea
(`<funcion> <json>`), así que los requests de una línea de `bruno/` siguen valiendo.

- **Descartado: un argumento `movimientos` con un arreglo.** Cambia el esquema de la función y todos
  los requests del stub, y los objetos anidados en una declaración de función son donde más se
  equivoca el modelo.

### 8. Todo o nada al registrar desde el asistente

Los movimientos se resuelven todos antes de registrar nada. El primero que falle produce
`NEEDS_CLARIFICATION` con su mensaje; con más de uno, prefijado con «Movimiento N:». Lo que el alta
rechace (por ejemplo, la categoría que no aplica) ya llega como un 400 con índices; el asistente lo
sigue traduciendo a `NEEDS_CLARIFICATION`, como hoy.

`PendingMovementPort.register` recibe la lista y devuelve el `groupId` con los movimientos
registrados. La respuesta reemplaza `transaction` por `group: {id, transactions}`.

### 9. El esquema en dos updates

Ninguno de los dos órdenes de un solo script sirve:

- el `CHECK` antes del despliegue rompe el asistente de la versión vieja, que crea pendientes sin
  grupo;
- la columna después del despliegue rompe la versión nueva, que escribe `group_id`.

`20261013_02_grupo_de_pendientes.sql` va **antes** del despliegue y agrega la columna y el índice; la
versión vieja no los nombra. `20261013_03_grupo_de_pendientes_check.sql` va **después**: le da a cada
pendiente sin grupo uno propio (`gen_random_uuid()`, cada fila sola) y crea el `CHECK`. Entre los
dos, los pendientes viejos salen con `groupId` `null` y solo se aprueban uno a uno. `schema.sql` lleva
el estado final.

## Risks / Trade-offs

- **Cambio de forma en la respuesta del asistente** → no hay cliente fuera de `bruno/`: el front no
  la usa y FIN-61 todavía no existe.
- **El modelo puede partir mal un mensaje** (dos movimientos donde había uno) → nada se guarda sin
  confirmar, y el usuario descarta el grupo.
- **Un pendiente del grupo que no se puede aprobar bloquea a los demás** → es lo pedido (todo o
  nada). Se corrige ese con `PATCH`, se rechaza uno a uno, o se aprueban los otros uno a uno.
- **Un pendiente sin cuenta (FIN-66) en un grupo** → hoy no se crea ninguno. Confirmarlo sin
  cuenta violaría `ck_transactions_account`; cómo se aprueba lo decide FIN-66.
