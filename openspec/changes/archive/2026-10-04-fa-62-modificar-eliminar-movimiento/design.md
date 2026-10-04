# Design

## Context

El alta (`POST /api/transactions`) reparte la validación en dos sitios: `CreateTransactionRequest`
valida formato y forma de la transferencia mirando solo el elemento (`@ReglasDeTransferencia`), y
`TransactionBatchValidator` comprueba contra las cuentas y categorías del usuario. Los saldos los
mantiene `trg_transactions_sync_balance`, que en UPDATE revierte la fila vieja y aplica la nueva, y
en DELETE revierte. Por eso este change no toca la base.

Un parche cambia el problema: la forma de la transferencia ya no se decide mirando el cuerpo, sino
el cuerpo más el movimiento guardado.

## Goals / Non-Goals

**Goals:**
- Reutilizar las reglas de cuentas y categorías del alta: un mismo id inválido tiene que dar el
  mismo mensaje en los dos endpoints.
- Que ningún camino de error modifique la fila.

**Non-Goals:**
- Control de concurrencia entre dos parches simultáneos sobre el mismo movimiento.
- Reutilizar el endpoint para modificar en lote.

## Decisions

### `null` significa "no cambia", no "vaciar"

En el parche, un campo ausente y uno en `null` valen lo mismo. Ninguno de los campos modificables
admite vaciarse por sí solo: `categoryId` y `destinationAccountId` quedan vacíos únicamente como
consecuencia de cambiar el tipo, y eso lo resuelve el servidor.

Descartado: JSON Merge Patch (RFC 7396), donde `null` borra. Obligaría a distinguir ausente de
`null` en el record (con `JsonNullable` o un `Map`), y lo único que permitiría borrar ya lo cubre el
cambio de tipo. Si algún día se vuelve modificable `notes`, que sí es opcional, se revisa.

### Validación en dos capas, igual que el alta

- `UpdateTransactionRequest` (en `dto/`): todos los campos opcionales, con las mismas constraints de
  formato del alta (`@ValorDeEnum`, `@Positive`, `@MontoNumeric`, `@Size`, `@FechaConOffset`) y
  `@NotBlank` sustituido por un patrón que rechaza solo el texto en blanco cuando el campo viene.
  `@ReglasDeTransferencia` **no** aplica: sin el movimiento guardado no se sabe el tipo resultante.
- El parche vacío lo comprueba el controlador, no una constraint de clase. Una constraint de clase
  sin campo propio llega al handler como error global y sale con `field: "unknown"`, y nombrar un
  nodo `body` que no existe en el record hace que Spring intente leerlo y falle.
- `UpdateTransactionUseCase`: carga el movimiento por id y usuario, aplica el parche, decide la
  forma resultante y valida las referencias contra las cuentas y categorías del usuario.

Descartado: una sola capa en el caso de uso. Separaría el formato del parche del formato del alta,
y AGENTS.md pide el formato en el record de request.

### Referencias compartidas con el alta

`cuentaPropia` y `categoria` salen de `TransactionBatchValidator` a una clase del mismo paquete
(`ReferenciasDelUsuario`), construida con los mapas de cuentas y categorías del usuario. El alta la
usa con el prefijo de índice (`[3].accountId`) y la modificación sin prefijo (`accountId`). El
alta no cambia de comportamiento, y sus tests lo comprueban.

Descartado: duplicar las comprobaciones en el caso de uso nuevo. Los mensajes que no revelan si
una cuenta es de otro usuario son justamente lo que no puede divergir.

### Qué referencias se vuelven a validar

Solo las que el parche envía, más la categoría guardada cuando el tipo cambia entre `EXPENSE` e
`INCOME` sin enviar categoría nueva: su alcance puede dejar de ser compatible. Una cuenta guardada
que se desactivó después del alta no bloquea corregir la descripción de un movimiento viejo.

Descartado: revalidar todo el movimiento resultante. Haría imposible tocar movimientos de cuentas
desactivadas, que es justo cuando más se corrige el historial.

### 404 igual para inexistente y ajeno

El caso de uso busca con `WHERE id = :id AND user_id = :userId`; el `UPDATE` y el `DELETE` llevan
el mismo filtro. Si no hay fila, `BadRequestException(HttpStatus.NOT_FOUND, ErrorCodes.NOT_FOUND,
"El movimiento no existe", "id")`. `BadRequestException` ya admite cualquier status y el handler lo
respeta, así que no hace falta un `case` nuevo en `WebExceptionHandler`.

Descartado: 403 para el ajeno. Confirmaría que el id existe para otro usuario, lo que el alta evita
deliberadamente con las cuentas.

### Persistencia

`TransactionRepositoryPort` suma `findByIdAndUser`, `update` y `deleteByIdAndUser` (este devuelve si
borró algo). El `UPDATE` reescribe todas las columnas modificables y `amount_base = amount` (solo
COP), dentro de la misma transacción del trigger. El `DELETE` usa `rowsUpdated`: 0 filas es 404, sin
un `SELECT` previo.

## Risks / Trade-offs

- [Dos parches simultáneos sobre el mismo movimiento: gana el último y el primero se pierde en
  silencio] → Aceptado: un solo usuario por cuenta. El trigger mantiene los saldos coherentes
  igualmente, porque revierte la fila que realmente estaba guardada.
- [`findByIdAndUser` y el `UPDATE` son dos sentencias: el movimiento podría borrarse entre las dos]
  → El `UPDATE` también filtra por usuario y, si no afecta filas, responde 404.
- [Los campos que Jackson no conoce, como `notes`, se ignoran sin error] → Se documenta en el
  contrato y en el request de `bruno-personal/`; rechazarlos sería un cambio global del codec.
