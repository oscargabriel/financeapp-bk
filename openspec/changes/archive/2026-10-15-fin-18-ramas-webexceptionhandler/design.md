# Design

## Context

`WebExceptionHandler` extiende `AbstractErrorWebExceptionHandler` de Boot, cuyo `handle(ServerWebExchange,
Throwable)` es público: guarda el error en el exchange, enruta con `getRoutingFunction` y escribe la
`ServerResponse`. Fuera de Spring basta con darle los message readers y llamar antes a
`afterPropertiesSet()`, que es lo que hace el contenedor. Hoy solo lo ejercita `WebExceptionHandlerIT` (`@SpringBootTest`, servidor real), con
cuatro casos: ruta inexistente, que no filtre internals, 413 del codec y 401. El resto de las ramas
cubiertas viene de los slices de cada controlador. Ver `proposal.md` para los números.

## Goals / Non-Goals

**Goals:**

- Cada rama del handler la alcanza un test que nombra el tipo de excepción y comprueba el JSON que sale.
- El umbral de rama queda en el build con margen suficiente para no romper por una rama suelta.

**Non-Goals:**

- Reordenar o reescribir el switch. Si un test muestra una respuesta distinta de la que el código
  declara, eso es un bug y se para.
- Probar qué excepción lanza cada controlador: eso ya lo hacen sus slices.

## Decisions

### Test unitario del handler, no un IT con un controlador de prueba

`WebExceptionHandlerTest` construye el handler con `DefaultErrorAttributes`,
`new WebProperties.Resources()`, un `StaticApplicationContext`, `ServerCodecConfigurer.create()` y un
`JsonMapper`; le pone los readers, llama a `afterPropertiesSet()` y después a
`handle(MockServerWebExchange, excepción)`. Lee el status y el cuerpo de la `MockServerHttpResponse`.

Descartado: un `@RestController` de prueba en `src/test` que lance cada excepción, ejercitado con
`@SpringBootTest`. El component scan parte del paquete del `main`, así que ese controlador entraría en
el contexto de **todos** los `*IT`; además exigiría un token por request y no alcanzaría el respaldo
de serialización, que solo se dispara con un `ObjectMapper` que falle. Con el unitario, ese caso es un
`ObjectMapper` mockeado que lanza en `writeValueAsString`.

Descartado también: pedir el bean a un contexto de Spring y llamar a `handle` sobre él. Ganaría
fidelidad en el cableado, que ya cubre `WebExceptionHandlerIT`, a costa de levantar un contexto completo.

Las excepciones de validación de método se arman a mano: `HandlerMethodValidationException` sobre un
`MethodValidationResult` con `ParameterValidationResult`/`ParameterErrors` construidos contra los
`MethodParameter` de un método dummy del propio test (uno `@RequestBody` y uno `@RequestParam`). Es el
único modo de alcanzar las ramas de `detallesDe` sin depender de que algún endpoint real tenga cada
forma.

### El dominio de los `case` lo comprueba el compilador

El criterio pide comprobar que ningún `case` esté dominado por su supertipo. En un `switch` de patrones
eso no puede pasar en silencio: JLS 14.11.1 lo hace error de compilación («this case label is dominated
by a preceding case label»). Lo que sí puede pasar es que un `case` quede inalcanzable por otro motivo,
o que responda con el `code` equivocado; eso lo cubre un test por `case`, y los dos pares sensibles
(`NumberFormatException` antes de `IllegalArgumentException`, `HandlerMethodValidationException` y
`ServerWebInputException` antes de `ResponseStatusException`) llevan cada uno un test que comprueba el
`code` del subtipo, no el del supertipo.

Descartado: un test por reflexión que lea el orden de los `case`. Duplicaría al compilador.

### Umbral de rama del 90 %

La rama global queda en ~93,6 % (1346 + 9 ramas del handler + 1 de `BadRequestException` sobre 1449).
Un 90 % deja ~3,6 puntos de margen, unas 50 ramas: un change normal no lo rompe por un `if` sin test,
pero uno que agregue una clase grande sin probar sí. El de línea tiene más margen (85 % frente a
98,55 %) porque se puso cuando la cobertura era mucho menor.

Descartados: 85 % igual al de línea (casi 9 puntos de margen, decorativo con los números de hoy) y
93 % (a medio punto del real, rompería con el primer change que deje dos o tres ramas sin cubrir).

## Risks / Trade-offs

- [El test unitario no pasa por el filtro de seguridad ni por el dispatcher real] → Lo cubre
  `WebExceptionHandlerIT`, que se queda como está.
- [Armar `HandlerMethodValidationException` a mano acopla el test a la API de validación de Spring] →
  Si Spring la cambia, falla el test y no el contrato; el cambio se ve en compilación.
- [El umbral global no protege al handler en particular: puede volver a perder ramas mientras el resto
  compense] → Aceptado; el test nuevo es lo que lo protege, y un umbral por clase es fuera de alcance.
