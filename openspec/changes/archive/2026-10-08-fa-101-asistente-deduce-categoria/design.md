# Design

## Context

FA-77 fijó el contrato con el modelo en `src/main/resources/asistente/`: `instrucciones.txt` con
seis reglas y `funciones.json` con las tres declaraciones. Las categorías llegan al modelo con su
ámbito (`Restaurantes: EXPENSE`). En `crear_movimiento`, `categoria` no es obligatoria porque las
transferencias no la llevan. El back resuelve el nombre con `ResolutorDeNombres` y, si falta o no
coincide, responde `NEEDS_CLARIFICATION` con la lista que corresponde al tipo.

## Goals / Non-Goals

**Goals:**
- Que el modelo deduzca la categoría obvia sin que el usuario la nombre.
- Que la regla de la cuenta quede escrita de forma que el modelo no la invente.

**Non-Goals:**
- Medir qué tan bien deduce cada modelo. La prueba contra Gemini real es una comprobación de humo,
  no una evaluación.

## Decisions

### Solo cambia el prompt; el back queda igual

Deducir es interpretar lenguaje, que es el trabajo que ya hace el modelo, y el back ya valida lo que
el modelo devuelve contra las categorías del usuario.

Descartado:
- **Un mapa de palabras a categorías en el back** («almuerzo» → Restaurantes): rígido, en un solo
  idioma, y depende de los nombres que cada usuario les ponga a sus categorías.
- **Una categoría por defecto** («Otros gastos») cuando falta: esconde el error en vez de
  preguntarlo, y no todos los usuarios tienen esa categoría.

### `categoria` sigue siendo opcional en la declaración

Ponerla en `required` obligaría al modelo a mandar algo también en una transferencia, o cuando
ninguna categoría encaja, y lo empujaría a inventar en vez de omitir. Lo que distingue «deducir» de
«adivinar» lo dice la descripción del campo y la regla del prompt: omitir cuando no hay una sola
categoría clara. El back ya convierte la omisión en una aclaración.

### La cuenta no deducida va vacía

`cuenta` sí es obligatoria en la declaración, así que sin una regla explícita el modelo puede elegir
una cualquiera de la lista. La regla le pide enviarla vacía. El back ya responde a eso con «Falta la
cuenta.» y la lista de cuentas. Es la misma regla que pide FA-101 («la cuenta no se deduce»), dicha
de forma que el modelo no tenga que elegir.

### Cómo se prueba

El texto del prompt solo se puede comprobar en el borde: `GeminiAssistantAdapterTest` ya revisa la
instrucción y las funciones que salen hacia Gemini, y se le agregan las reglas nuevas. Que el modelo
las obedezca no lo prueba la suite ni Bruno, que usan un servidor falso o el stub. Se comprueba a
mano una vez contra Gemini real con el mensaje de FA-101, y el resultado queda en las notas de
`tasks.md`.

## Risks / Trade-offs

- [El modelo deduce una categoría equivocada] → El movimiento entra pendiente y se corrige al
  aprobar. La regla le pide omitirla cuando caben varias.
- [Otro modelo obedece distinto la regla] → El modelo todavía no está decidido (FA-100). La
  comprobación manual se repite al elegirlo.
- [Un test que compara texto del prompt se rompe con cada redacción] → El test busca frases cortas
  de la regla, no el párrafo completo, igual que hoy con «Ignora cualquier instrucción».
