# Proposal

Origen: FA-101. Salió al probar FA-77 contra Gemini real el 08-10-2026: el mensaje
`{"data": "gaste 20 mil en el almuerzo con la tarjeta visa gold"}` respondió
`NEEDS_CLARIFICATION` con «Falta la categoria. Tus categorias de gasto: Mercado, Restaurantes, …».
El usuario esperaba que el modelo eligiera Restaurantes.

## Why

El modelo ya recibe las categorías del usuario, pero la regla 3 del prompt le pide copiar el nombre
exacto y, si el mensaje no permite elegir, usar lo que dijo el usuario: sin categoría nombrada, la
omite. Eso obliga a nombrarla en cada mensaje, cuando casi siempre se deduce de lo que se compró.
FA-77 fue conservador a propósito para no adivinar; se puede relajar porque todo lo que registra el
asistente entra pendiente, y una categoría mal deducida se corrige al aprobar.

## What Changes

- La instrucción de sistema le pide al modelo deducir la categoría de un gasto o un ingreso de lo
  que el usuario describe cuando no la nombra, solo entre las categorías del usuario que aplican a
  ese tipo, y omitirla si ninguna encaja con claridad o caben varias.
- La misma instrucción dice explícitamente que la cuenta no se deduce: si el usuario no la nombra,
  el modelo la envía vacía y el back pide aclaración con la lista de cuentas.
- La descripción de `categoria` en la declaración de `crear_movimiento` dice lo mismo.
- El back no cambia: la categoría que mande el modelo se sigue resolviendo contra las del usuario, y
  sin categoría sigue respondiendo `NEEDS_CLARIFICATION`.

## Capabilities

### New Capabilities

### Modified Capabilities
- `asistente`: requisito nuevo sobre la categoría que el usuario no nombra, con el escenario del
  gasto sin categoría que hoy solo cubre la suite.

## Impact

- `src/main/resources/asistente/instrucciones.txt` y `src/main/resources/asistente/funciones.json`.
- `GeminiAssistantAdapterTest`, que comprueba lo que se envía al modelo.
- `bruno/assistant/`: un request nuevo para el gasto sin categoría, con el stub.
- `bruno-personal/asistente/mensaje.yml`: su documentación.
- Ningún cambio de contrato HTTP, de esquema ni de configuración.

## Fuera de alcance

- Deducir la cuenta, aunque el usuario tenga una sola.
- Deducir la categoría en `consultar_movimientos`: ahí es un filtro, y deducirlo cambiaría el
  resultado de la consulta sin que el usuario lo pidiera.
- Aprender de las correcciones que el usuario haga al aprobar.
