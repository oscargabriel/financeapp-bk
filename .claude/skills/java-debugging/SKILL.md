---
name: java-debugging
description: >
  Use when encountering any bug, failing test, build failure or unexpected behavior in
  financeapp-bk, before proposing a fix — and especially for Spring WebFlux reactive code:
  Mono/Flux pipeline issues, backpressure, race conditions, context propagation failures, or
  reactive stack traces. Combines a root-cause-first debugging method with WebFlux domain knowledge.
license: El método y references/root-cause-tracing.md vienen de superpowers (MIT, ver LICENSE-superpowers); el resto, del catálogo propio.
metadata:
  origen: skills-catalogo 2026-10-04
  incorpora: superpowers 6.4.2, skills/systematic-debugging (SKILL.md y root-cause-tracing.md)
---

# Depuración — causa raíz primero, con conocimiento de WebFlux

Dos partes: el **método** (de dónde se parte y en qué orden), adaptado de
`systematic-debugging` de superpowers, y el **conocimiento de WebFlux** (cómo se clasifican y se
leen los errores reactivos). Al actualizarse superpowers, comparar la primera parte con
`skills/systematic-debugging/SKILL.md` de la versión nueva; la procedencia está al final.

## La ley

```
NINGÚN ARREGLO SIN INVESTIGAR LA CAUSA RAÍZ PRIMERO
```

Arreglar el síntoma es fallar. Si la fase 1 no está completa, no se propone un arreglo. Vale
también, y sobre todo, cuando el problema parece simple, hay prisa o "un arreglo rápido" parece
obvio: lo sistemático es más rápido que adivinar.

## El método: cuatro fases, en orden

### Fase 1 — Investigar la causa raíz

1. **Leer el error completo.** El mensaje, toda la traza, las líneas y los códigos: muchas veces ya
   dicen la solución. En una traza reactiva, ver *Leer una traza reactiva* más abajo.
2. **Reproducirlo de forma consistente.** Pasos exactos. Si no se reproduce siempre, juntar más
   datos; no adivinar.
3. **Revisar qué cambió.** `git diff`, commits recientes, dependencias, configuración, diferencias
   de entorno (perfil activo, `application-local.yaml`, proceso viejo sirviendo código anterior).
4. **En un sistema de varias capas, instrumentar los bordes antes de arreglar.** Aquí las capas son
   controlador → caso de uso → adapter → R2DBC → PostgreSQL, más los filtros de seguridad antes del
   controlador. Registrar qué entra y qué sale de cada una, correr una vez, y con esa evidencia
   ubicar dónde se rompe. Recién ahí investigar esa capa.
5. **Rastrear el dato hacia atrás.** ¿Dónde nace el valor malo? ¿Quién llamó con él? Subir hasta el
   origen y arreglar ahí, no donde explota. Técnica completa en
   `references/root-cause-tracing.md`.

### Fase 2 — Buscar el patrón

1. Encontrar código parecido que **sí** funciona en el mismo repo.
2. Si se sigue un patrón o una referencia, leerla completa, no por encima.
3. Listar cada diferencia entre lo que funciona y lo que no, por chica que sea.
4. Entender de qué depende: beans, propiedades, perfil, orden de filtros.

### Fase 3 — Hipótesis y prueba

1. **Una sola hipótesis, escrita:** "creo que X es la causa porque Y". Antes de elegirla, enumerar
   al menos tres candidatas ordenadas por probabilidad, incluidas las de fuera del código.
2. **Probarla con el cambio más chico posible**, una variable a la vez.
3. ¿Se confirmó? Fase 4. ¿No? Hipótesis nueva; **no** apilar arreglos encima.
4. Si no se entiende algo, decirlo ("no entiendo X") en vez de fingir.

### Fase 4 — Arreglar

1. **Primero un test que falle** reproduciendo el bug, con la capa que corresponda según
   `java-testing`. Sin ese test no hay arreglo.
2. **Un solo arreglo**, a la causa raíz. Sin "ya que estoy" ni refactors de paso.
3. **Verificar** con `verificar`: el test nuevo pasa, ninguno se rompe, y `bru run` si el bug
   tocaba el API o la base.
4. **Si el arreglo no funciona:** parar y contar los intentos. Con menos de tres, volver a la fase 1
   con lo aprendido. **Con tres o más, parar y cuestionar la arquitectura con el usuario** antes de
   un cuarto: cuando cada arreglo destapa un problema nuevo en otro lugar, el problema es el
   diseño, no la hipótesis.

### Cuando la investigación dice "no hay causa raíz"

Si de verdad es ambiental, de tiempos o externo: documentar lo investigado, poner el manejo que
corresponda (reintento, timeout, mensaje de error) y dejar logs para la próxima. Pero casi siempre
"no hay causa raíz" es una investigación incompleta.

## Señales de alarma: volver a la fase 1

- "Arreglo rápido ahora, investigo después."
- "Pruebo cambiar X a ver si funciona."
- "Cambio varias cosas y corro los tests."
- "Me salto el test, lo verifico a mano."
- "Seguro es X, lo arreglo."
- Proponer soluciones antes de rastrear el flujo del dato.
- "Un intento más" cuando ya van dos o más.

Y del lado del usuario: "¿eso no está pasando?" (se asumió sin verificar), "deja de adivinar",
"¿estamos trabados?". Cualquiera de esas es señal de volver a la fase 1.

---

## Conocimiento de WebFlux

### Cuándo pesa esta parte

- Un error con frames de Reactor en la traza.
- Un bug intermitente o que depende de tiempos en un pipeline `Mono`/`Flux`.
- Un pipeline que no se comporta como se espera: backpressure, publisher frío, contexto perdido.
- Algo que "debería funcionar" y no hace nada, ni falla.

### Árbol de clasificación

```
¿Qué muestra la traza o el síntoma?
│
├── "block()/blockFirst()/blockLast() are blocking"
│     └── REACTIVO — llamada bloqueante en un hilo reactivo
│
├── "Assembly trace" / "checkpoint(...)" en la traza
│     └── REACTIVO — problema al ensamblar la cadena de operadores
│
├── Intermitente o dependiente de tiempos
│     └── REACTIVO — backpressure, condición de carrera, publisher caliente/frío
│
├── El Mono/Flux completa pero no pasa nada (ni error ni salida)
│     └── REACTIVO — publisher frío sin suscriptor
│
├── Solo en ciertas rutas o requests
│     └── INTEGRACIÓN — mapeo del controlador, filtro, base-path /api, cadena de seguridad
│
├── Al arrancar / "No qualifying bean" / falla de inyección
│     └── CONFIGURACIÓN — contexto de Spring, bean faltante, perfil equivocado
│
├── Consistente y sin frames de Reactor
│     └── LÓGICA DE NEGOCIO — dominio o caso de uso (Java puro)
│
└── Solo al hablar con la persistencia
      └── PERSISTENCIA — driver reactivo, consulta o límite de transacción
```

### Trampas reactivas

| Síntoma | Causa | Cómo detectarlo |
|---|---|---|
| `IllegalStateException: block()/subscribe() called from within a reactive thread` | Llamada bloqueante dentro de un Mono/Flux | La traza muestra `reactor.core.publisher` antes del `block()` |
| `NullPointerException` dentro de `.map()` | Arriba se emitió `null`, o se usó `empty()` donde iba `error()` | `.log()` antes del `.map()` |
| Faltan campos del MDC o del contexto de seguridad en los logs | El contexto no cruzó los límites reactivos | Comprobar que `ReactorMdcHook` esté registrado al arrancar |
| `ConnectionTimeoutException` bajo carga | Pool de conexiones reactivo agotado | Comparar el tamaño del pool con la concurrencia esperada |
| Un error se traga en silencio | `.onErrorResume()` u `.onErrorReturn()` demasiado amplios | Buscar esos operadores en el camino del pipeline |
| El Flux/Mono nunca corre, sin error | Publisher frío sin suscriptor | Seguir desde la fuente hasta donde debería estar el `subscribe()` |
| Comportamiento distinto por request bajo carga | Estado mutable compartido en un bean u operador | Buscar campos no `final` que se modifican dentro del pipeline |

### Leer una traza reactiva

Las trazas de Reactor están invertidas respecto al Java imperativo:

```
1. ARRIBA          → la excepción real (qué falló)
2. Tu primer frame → dónde aparece en el código del proyecto
                     (saltar los frames reactor.*, spring.*, netty.*)
3. HACIA ABAJO     → la cadena de operadores que llevó al origen
```

Sin trazas de ensamblado la cadena cuesta seguirla. Para activarlas (solo en desarrollo, tienen
costo):

```java
ReactorDebugAgent.init();
```

O acotar con un checkpoint en el operador sospechoso; la etiqueta aparece en la traza:

```java
.checkpoint("cuentas:crear")
```

### Herramientas mínimas de investigación

```java
// 1. Registrar el pipeline en un punto (quitar al terminar)
.log("debug:etiqueta", Level.FINE)

// 2. Acotar dónde ocurre el error
.checkpoint("transacciones:validar-lote")

// 3. Ver qué pasa por el pipeline
.doOnNext(v -> log.debug("valor: {}", v))
.doOnError(e -> log.error("error en este punto", e))
.doOnComplete(() -> log.debug("completó"))

// 4. Confirmar que el contexto se propaga
.contextWrite(ctx -> {
    log.debug("contexto: {}", ctx);
    return ctx;
})
```

Trazas de ensamblado globales (flag de la JVM, solo desarrollo): `-Dreactor.tools.agent=true`.

### Dónde mirar primero según la capa

| Capa | Paquete | Bugs típicos |
|---|---|---|
| Dominio | `domain/` | NPE en el modelo, lógica pura |
| Casos de uso | `application/usecase/` | Encadenado de Mono equivocado, `flatMap` que falta, error mal propagado |
| Adapters de salida | `infrastructure/adapter/out/` | Errores de consulta, mapeo de filas, transacciones |
| Configuración | `infrastructure/config/` | Cableado de beans, propiedades faltantes, pool |
| Entrada web | `infrastructure/adapter/in/web/` | Mapeo de rutas, orden de filtros, parseo del request, `WebExceptionHandler` |

---

## Procedencia

La primera parte (la ley, las cuatro fases, la regla de los tres intentos, las señales de alarma y
`references/root-cause-tracing.md`) es una adaptación al español y a este proyecto de
`skills/systematic-debugging` de [superpowers](https://github.com/obra/superpowers) **6.4.2**, de
Jesse Vincent, bajo licencia MIT: el aviso completo está en `LICENSE-superpowers`. Se dejaron fuera
`condition-based-waiting.md` (aquí los tiempos se prueban con `StepVerifier` y tiempo virtual, ver
`java-testing`), `defense-in-depth.md` (la validación en el borde ya está en `AGENTS.md`) y los
archivos de pruebas de presión de la skill.
