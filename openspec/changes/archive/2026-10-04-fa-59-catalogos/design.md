# Design

## Context

Los catálogos mezclan dos clases de dato: enums del dominio (`AccountType`, `TransactionType`), que no
pasan por la base, y filas de `finance.currencies` y `finance.categories`, que sí. Las categorías ya
tienen caso de uso (`ListCategoriesPort`, FA-18), y las monedas solo tienen `CurrencyQueryPort.exists`.

## Decisions

### Las etiquetas en español viven en el adaptador web

Cada `description` sale de un `switch` exhaustivo sin `default` en el DTO de respuesta del catálogo:
al agregar un valor a `AccountType` o `TransactionType`, el compilador obliga a darle etiqueta.

Descartado: un campo `descripcion` en los enums del dominio. La etiqueta es de presentación para un
cliente en español, y el dominio la cargaría para un único consumidor. Si mañana otro adaptador
(el bot de Telegram) necesita la misma etiqueta, se mueve entonces.

### Los catálogos de enums no tienen caso de uso

`CatalogController` lee `AccountType.values()` y `TransactionType.values()` directamente. Las
monedas y las categorías sí pasan por puerto de entrada.

**Contradice `AGENTS.md`**, que dice que un endpoint nuevo toca puerto de entrada, caso de uso,
puerto de salida y adapter. Esa regla existe para que el controlador no hable con la persistencia ni
cargue reglas; aquí no hay ninguna de las dos, y un puerto que solo devuelve `values()` sería una
capa sin contenido. El change agrega a `AGENTS.md` la excepción: un endpoint que solo expone los
valores de un enum del dominio no lleva puerto.

Descartado: `ListAccountTypesPort` y `ListTransactionTypesPort` con sus casos de uso.

### Monedas: puerto de entrada nuevo y método nuevo en el puerto de salida existente

`ListCurrenciesPort` → `ListCurrenciesUseCase` → `CurrencyQueryPort.findActive()`, implementado en
`CurrencyR2dbcAdapter` con `ORDER BY code`. El método va en el puerto existente porque es la misma
tabla y el mismo adapter; un `Currency(code, name, symbol)` nuevo en `domain/model`.

### Categorías: se reutiliza `ListCategoriesPort` y se duplica el parseo de `appliesTo`

El controlador del catálogo llama al mismo caso de uso que `CategoryController`, así que el filtro y
el orden son los mismos por construcción. El parseo de `appliesTo` (exacto, en mayúsculas, 400 en
`appliesTo`) es un método privado de `CategoryController`; el catálogo tiene su propia copia.

Descartado: extraer el parseo a una clase compartida. Habría que tocar `CategoryController`, y el
criterio de la tarea dice que no cambia. Las dos copias quedan fijadas por los tests de cada
controlador y por `bruno/` con el mismo texto de error; si cambian los alcances, el compilador no
avisa en ninguna de las dos, así que la deuda es real pero acotada a ocho líneas.

### Una moneda inactiva de prueba en `test-data.sql`

La semilla solo trae monedas activas, así que sin un dato nuevo nada en Bruno probaría el filtro de
`is_active`. Se usa `XTS`, el código que ISO 4217 reserva para pruebas, con un `INSERT ... ON
CONFLICT (code) DO UPDATE SET is_active = FALSE` para que recargar el escenario lo deje igual.
Va en `test-data.sql` y no en `seed.sql`: en Neon no debe existir.

### La réplica en `bruno-personal/` como regla de `tasks`

Se agrega una regla en `openspec/config.yaml` (`rules.tasks`) y un punto en el paso 8 de
`tareas-notion`. Descartado: un script que compare `bruno/` con `bruno-personal/`. Las dos
colecciones no tienen la misma forma (nombres de carpeta en español, sin requests de error ni de
escenario), así que la comparación sería frágil y avisaría de diferencias deliberadas.

## Risks / Trade-offs

- La duplicación del parseo de `appliesTo`: ver arriba.
- Las etiquetas en español son una propuesta; cambiarlas no rompe a nadie más que al cliente que las
  muestre.
