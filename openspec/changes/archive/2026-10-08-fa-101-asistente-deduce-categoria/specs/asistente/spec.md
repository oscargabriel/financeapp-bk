## ADDED Requirements

### Requirement: Categoría que el usuario no nombra
La instrucción que el asistente envía al modelo SHALL pedirle que, si el usuario no nombra la
categoría de un gasto o un ingreso, la deduzca de lo que describe, solo entre las categorías del
usuario que aplican a ese tipo, y la omita si ninguna encaja con claridad o caben varias. SHALL
pedirle también que no deduzca la cuenta. La categoría que envíe el modelo SHALL resolverse igual
que una nombrada por el usuario.

#### Scenario: La instrucción pide deducir la categoría y no la cuenta
- **WHEN** el asistente consulta al modelo
- **THEN** la instrucción de sistema y la descripción de `categoria` en `crear_movimiento` piden deducir la categoría de lo que describe el usuario cuando no la nombra, y la instrucción pide enviar la cuenta vacía cuando el usuario no la nombra

#### Scenario: Gasto sin categoría
- **WHEN** el modelo llama `crear_movimiento` con `tipo` `EXPENSE` y sin `categoria`
- **THEN** la respuesta es 200 con `"intent": "NEEDS_CLARIFICATION"`, un `message` que nombra las categorías de gasto del usuario, `transaction` en `null`, y `GET /api/transactions/pending` no cambia
