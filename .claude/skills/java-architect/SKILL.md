---
name: java-architect
description: >
  Use when scaffolding a new Java project, designing or reviewing hexagonal architecture
  layers (domain/application/infrastructure/ports/adapters), or applying SOLID/DRY/KISS/YAGNI
  in Java 25 + Spring Boot 4 with WebFlux or virtual threads.
  Skip for non-Java projects or architectures other than hexagonal/ports-and-adapters.
metadata:
  origen: skills-catalogo 2026-10-04
---

# Java Architect

Diseñar, generar y revisar proyectos Java 25 + Spring Boot 4 con arquitectura hexagonal,
principios SOLID/DRY/KISS/YAGNI, estilo funcional y patrones reactivos.

Entregar lo pedido al alcance pedido: no añadir capas, interfaces, adaptadores ni configuración
que no se solicitaron. Si un componente futuro es previsible pero no está pedido, dejar un TODO
comentado y decirlo, en vez de implementarlo.

## Archivos de referencia

Leer según la tarea, no de entrada:

- `references/hexagonal-structure.md` — layout canónico de paquetes, nomenclatura y reglas de
  dependencia. Antes de cualquier scaffolding o revisión de organización de paquetes.
- `references/domain-modeling.md` — entidades, value objects, records anidados de JSON, `@Builder`
  y domain events. Al crear o revisar tipos de `domain/model/` o DTOs.
- `references/springboot4.md` — wiring de Spring Boot 4, virtual threads, WebFlux vs. WebMvc,
  R2DBC, testing slices, Micrometer y GraalVM native. Cuando la tarea toque infraestructura Spring.
- `references/java25-functional.md` — cheatsheet Java 25: records, sealed classes, pattern
  matching, virtual threads, streams, Optional, Project Reactor.
- `references/compact-rules.md` — bloque autocontenido de reglas para inyectar a sub-agentes.

## Paso 1 — Capturar intención

Identificar qué se pide antes de generar código: scaffolding, diseño de dominio, capa de
aplicación, adaptadores, revisión de código, refactor funcional/reactivo o configuración de Spring
Boot 4. Si la intención no es clara, hacer una pregunta concisa antes de continuar.

## Paso 2 — Scaffolding

Layout exacto en `references/hexagonal-structure.md`. Las tres zonas:

```
src/main/java/com/empresa/nombreapp/
├── domain/          ← núcleo puro; sin dependencias de frameworks
│   ├── model/
│   ├── port/
│   │   ├── in/
│   │   └── out/
│   ├── service/
│   └── event/
├── application/     ← orquesta el dominio; depende solo de domain/
│   ├── usecase/
│   └── dto/
└── infrastructure/  ← implementaciones concretas; el único lugar con Spring
    ├── adapter/
    │   ├── in/          ← driving: web, messaging consumers
    │   │   └── web/
    │   │       └── dto/ ← request/response DTOs (no sueltos junto a los controllers)
    │   └── out/         ← driven: persistence, clients, publishers
    │       ├── persistence/
    │       └── messaging/
    └── config/          ← wiring Spring (cablea, no adapta → fuera de adapter/)
```

Los adaptadores se agrupan por dirección: `adapter/in/` cumple los puertos de `domain/port/in`,
`adapter/out/` los de `domain/port/out`. `config/` queda fuera de `adapter/` porque cablea, no
adapta.

Orden de creación:

1. Interfaces de puertos en `domain/port/out/` — son el contrato que la infraestructura cumplirá.
2. Entidades y value objects en `domain/model/` (ver `references/domain-modeling.md`).
3. Casos de uso en `application/usecase/`, con un único método público `ejecutar(Command)` o
   `consultar(Query)`.
4. Adaptadores en `infrastructure/adapter/in|out/`, sin lógica de negocio.
5. Wiring en `infrastructure/config/` con `@Configuration` + `@Bean`.

## Paso 3 — Puertos y adaptadores

```java
public interface PedidoRepository {
    Mono<Void> guardar(Pedido pedido);
    Mono<Pedido> buscarPorId(PedidoId id);             // Mono.empty() si no existe
    /** Never completes empty; emits domain exception if not found. */
    Mono<Pedido> obtenerPorId(PedidoId id);
    Flux<Pedido> buscarPorCliente(ClienteId id);
}
```

Convención `buscar*` / `obtener*`:
- `buscar*` devuelve `Mono.empty()` si no existe; el caller decide.
- `obtener*` nunca completa vacío; el **adapter** lanza la excepción de dominio. El
  `switchIfEmpty` vive en infraestructura, no en el use case — así el use case queda libre de
  plomería y la misma regla no se repite en cada llamador.

```java
// infrastructure/adapter/out/persistence/
@Override
public Mono<Pedido> obtenerPorId(PedidoId id) {
    return repository.findById(id.value())
        .switchIfEmpty(Mono.error(() -> new PedidoNoEncontradoException(id)));
}
```

Ninguna interfaz de `domain/port/` importa `infrastructure/`, `org.springframework`,
`jakarta.persistence` ni otro framework externo.

Los puertos de entrada (`domain/port/in/`) son opcionales: útiles cuando el caso de uso necesita
sustituirse por un test double o cuando hay varios adaptadores de entrada para la misma operación.

Cada adaptador implementa exactamente un puerto, y ninguno expone entidades de dominio hacia
afuera: se mapea a DTOs antes del boundary. Cuando un controller acumula conocimiento de dominio
(un `Map` de formatos, una regla de validación), ese conocimiento va a un enum/VO de dominio, la
traducción del error a HTTP a un mapper de `adapter/in/web/`, y los invariantes del input al
constructor del Command; el controller queda como puro cableado.

## Paso 4 — Casos de uso

```java
@ApplicationService
@AllArgsConstructor
public class ConfirmarPedidoUseCase implements ConfirmarPedidoPort {

    private final PedidoRepository pedidoRepository;
    private final DomainEventPublisher eventPublisher;

    @Override
    public Mono<PedidoConfirmadoDto> ejecutar(ConfirmarPedidoCommand command) {
        return pedidoRepository.obtenerPorId(command.pedidoId())
            .map(Pedido::confirmar)
            .flatMap(pedidoRepository::guardar)
            .doOnNext(p -> eventPublisher.publicar(
                new PedidoConfirmado(p.id(), Instant.now())))
            .map(PedidoConfirmadoDto::desde);
    }
}
```

- Inyección por constructor con interfaces; Lombok `@AllArgsConstructor` sobre campos `final` en
  vez del constructor manual. Constructor explícito solo si lleva `@Value` o desambigua con
  `@Qualifier`.
- `Command` y `Query` son `record` inmutables.
- Cero lógica de negocio: solo orquestación. Sin imports de `infrastructure/`.

## Paso 5 — SOLID, funcional y reactivo

**SOLID en Java 25:** una razón de cambio por clase; `sealed interface` + `switch` exhaustivo en
lugar de cadenas `instanceof` (al añadir un caso, el compilador obliga a actualizar los `switch`
sin `default`); respetar el contrato completo al implementar un puerto; dividir puertos grandes
(un repositorio de 12 métodos suele ser command + query); y el dominio nunca depende de
infraestructura.

**Estilo funcional:** inmutabilidad por defecto (`record`, `List.of()`, campos `final`, métodos
que devuelven nueva instancia); pipelines de `Stream` en vez de bucles, con `toList()` sobre
`Collectors.toList()`; `Optional` encadenado y `orElseThrow()` en vez de `.get()`; lambdas
reutilizables extraídas a `static final Predicate<T>`/`Function<T,R>`. Idioms concretos en
`references/java25-functional.md`.

**Reactivo vs. virtual threads** (configuración en `references/springboot4.md`):

| Caso | Recomendación |
|---|---|
| Servicio nuevo, stack sin WebFlux | Virtual threads + WebMvc + código bloqueante |
| Stack ya usa WebFlux o R2DBC | Reactor nativo; no mezclar bloqueante |
| Pipeline de transformación asíncrona | `Flux<T>` con operadores declarativos |
| Backpressure explícita / streaming | Reactive Streams / Reactor |
| Concurrencia paralela estructurada | `StructuredTaskScope` (JEP 505) |

Con `spring.threads.virtual.enabled=true`, el código bloqueante convencional es suficiente: no
envolverlo en `Mono.fromCallable()` ni usar `.block()` dentro de pipelines reactivos.

## Paso 6 — Revisión de código

**DRY** sobre bloques repetidos de 3+ líneas, pero no cuando las similitudes son superficiales y
los conceptos distintos. **KISS**: cuestionar interfaces con una sola implementación y sin plan
concreto de una segunda. **YAGNI**: borrar parámetros no usados, configuración sin consumidor y
generics excesivamente amplios; el control de versiones los devuelve si el caso aparece.

Estructurar el feedback en tres niveles: **bloqueante** (viola arquitectura o introduce un bug),
**mejora** (code smell que reduce mantenibilidad), **sugerencia** (estilo o idiom preferible).

Violaciones de arquitectura a detectar:
- Use case con `switchIfEmpty(Mono.error(...))` repetido para validar existencia → mover al adapter.
- `default` method con lógica de negocio en un port interface → pertenece a la implementación.
- Dominio que importa `org.springframework` o `jakarta.*`.
- Controller que accede al repositorio sin pasar por el use case.
- Entidad R2DBC y entidad de dominio en la misma clase.
- `record` de sub-estructura JSON en archivo suelto que solo usa un contenedor → anidarlo.
- Record/clase de datos construido con constructor posicional en vez de builder (incluidos tests).
- Controller con `Map`/constante de reglas de negocio o validación de formato.

Violaciones de seguridad en la misma revisión (OWASP):
- SQL construido por concatenación de input en un adapter R2DBC → `.bind()`/`Criteria`.
- Request DTO mapeado directo a entidad de dominio/persistencia → mapper explícito con solo los
  campos controlables por el cliente.
- `WebClient` cuya URI completa viene de input o de datos externos sin allowlist.
- VO/record de entrada sin validación en el constructor compacto.
- Chequeos de autorización con `if` dentro de controllers → `SecurityWebFilterChain` (skill
  `java-security`).

## Reglas de dependencia

```
infrastructure/  →  application/  →  domain/
                                   ↑
                            (nunca al revés)
```

Congelarlas con ArchUnit en la suite (plantilla en la skill `java-testing`,
`references/layers.md`):

```java
ArchRule reglaHexagonal = layeredArchitecture()
    .consideringAllDependencies()
    .layer("Domain").definedBy("..domain..")
    .layer("Application").definedBy("..application..")
    .layer("Infrastructure").definedBy("..infrastructure..")
    .whereLayer("Domain").mayNotAccessLayersExcept()
    .whereLayer("Application").mayOnlyAccessLayers("Domain")
    .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain");
```

Tras un scaffolding o refactor que modifique código, correr la suite con el wrapper del proyecto
y reportar el conteo (skill `verificar`).
