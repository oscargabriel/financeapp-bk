# Java 25 — Idioms Funcionales y Reactivos

## Tabla de contenidos
1. [Records](#records)
2. [Sealed classes e interfaces](#sealed)
3. [Pattern matching](#pattern-matching)
4. [Streams y colecciones](#streams)
5. [Optional](#optional)
6. [Interfaces funcionales y composición](#functional-interfaces)
7. [Virtual Threads y concurrencia estructurada](#virtual-threads)
8. [Project Reactor — Mono y Flux](#reactor)
9. [String Templates](#string-templates)
10. [Decisión: virtual threads vs. reactive](#decision)

---

## 1. Records {#records}

Los `record` son la forma canónica de value objects inmutables. Generan automáticamente
constructor, accessors, `equals`, `hashCode` y `toString`.

```java
// Value object básico con validación
public record Dinero(BigDecimal cantidad, Moneda moneda) {
    public Dinero {
        Objects.requireNonNull(cantidad, "cantidad requerida");
        Objects.requireNonNull(moneda, "moneda requerida");
        if (cantidad.compareTo(BigDecimal.ZERO) < 0)
            throw new IllegalArgumentException("cantidad no puede ser negativa");
    }

    public Dinero sumar(Dinero otro) {
        if (!moneda.equals(otro.moneda))
            throw new MonedaIncompatibleException(moneda, otro.moneda);
        return new Dinero(cantidad.add(otro.cantidad), moneda);
    }
}

// Command / Query como record
public record ConfirmarPedidoCommand(PedidoId pedidoId, UsuarioId confirmadoPor) {}

// Record con factory method para validación compleja
public record RangoFechas(LocalDate inicio, LocalDate fin) {
    public static RangoFechas de(LocalDate inicio, LocalDate fin) {
        if (fin.isBefore(inicio)) throw new IllegalArgumentException("fin antes de inicio");
        return new RangoFechas(inicio, fin);
    }
}
```

**Cuándo NO usar `record`**: cuando la entidad tiene identidad de ciclo de vida larga
(un `Pedido` que cambia de estado). En ese caso usar clase `final` con constructor privado.

---

## 2. Sealed classes e interfaces {#sealed}

Usar `sealed` para modelar tipos cerrados donde el conjunto de subtipos es conocido.
Ideal para domain events, resultados tipados, estados de máquina.

```java
// Domain events — sealed interface con record implementations
public sealed interface EventoPago
    permits PagoRecibido, PagoRechazado, PagoReembolsado {}

public record PagoRecibido(PagoId id, Dinero monto, Instant occurredOn)
    implements EventoPago {}
public record PagoRechazado(PagoId id, String motivo, Instant occurredOn)
    implements EventoPago {}
public record PagoReembolsado(PagoId id, Dinero monto, Instant occurredOn)
    implements EventoPago {}

// Resultado tipado — alternativa a excepciones para flujos esperados
public sealed interface Resultado<T> permits Resultado.Ok, Resultado.Error {
    record Ok<T>(T valor) implements Resultado<T> {}
    record Error<T>(String mensaje, Throwable causa) implements Resultado<T> {}
}
```

El compilador verifica exhaustividad en `switch`. Si se añade un nuevo `permits`,
todos los `switch` sin `default` fallan en compilación — ventaja deliberada para
detectar casos no manejados en tiempo de compilación, no en producción.

---

## 3. Pattern Matching {#pattern-matching}

### Switch expressions con patterns

```java
// Exhaustivo gracias a sealed — sin default necesario
String describir(EventoPago evento) {
    return switch (evento) {
        case PagoRecibido r  -> STR."Pago de \{r.monto()} recibido";
        case PagoRechazado r -> STR."Pago rechazado: \{r.motivo()}";
        case PagoReembolsado r -> STR."Reembolso de \{r.monto()}";
    };
}

// Guarded patterns
String clasificarMonto(Dinero dinero) {
    return switch (dinero.cantidad()) {
        case BigDecimal d when d.compareTo(BigDecimal.ZERO) == 0 -> "sin cargo";
        case BigDecimal d when d.compareTo(new BigDecimal("100")) < 0 -> "micro";
        case BigDecimal d when d.compareTo(new BigDecimal("10000")) < 0 -> "normal";
        default -> "alto valor";
    };
}

// Deconstruction patterns con records
void procesarEvento(EventoPago evento) {
    if (evento instanceof PagoRecibido(var id, var monto, var ts)) {
        registrar(id, monto);  // variables extraídas directamente del record
    }
}
```

### instanceof con binding variable

```java
// Antes (Java pre-16)
if (obj instanceof String) { String s = (String) obj; usar(s); }

// Ahora
if (obj instanceof String s && !s.isBlank()) {
    usar(s);
}
```

---

## 4. Streams y colecciones {#streams}

### Pipelines declarativas

```java
// Agrupación con downstream collector
Map<EstadoPedido, Long> conteosPorEstado = pedidos.stream()
    .collect(Collectors.groupingBy(Pedido::estado, Collectors.counting()));

// Collector teeing — dos reducciones en un solo paso
record Particion<T>(List<T> cumple, List<T> noCumple) {}

Particion<Pedido> particion = pedidos.stream()
    .collect(Collectors.teeing(
        Collectors.filtering(Pedido::estaConfirmado, Collectors.toList()),
        Collectors.filtering(p -> !p.estaConfirmado(), Collectors.toList()),
        Particion::new
    ));

// flatMap para aplanar colecciones anidadas
List<LineaPedido> todasLasLineas = pedidos.stream()
    .flatMap(p -> p.lineas().stream())
    .toList();  // toList() inmutable desde Java 16

// Gatherers (Java 22+ preview, estable en 25) — operaciones con estado
List<Pedido> ventanaDecinco = pedidos.stream()
    .gather(Gatherers.windowFixed(5))
    .map(ventana -> ventana.get(0))  // primero de cada ventana
    .toList();
```

### Evitar antipatrones

```java
// MAL: mutar estado externo desde stream
List<String> resultado = new ArrayList<>();
pedidos.stream().forEach(p -> resultado.add(p.id().toString()));

// BIEN: collect
List<String> resultado = pedidos.stream()
    .map(p -> p.id().toString())
    .toList();

// MAL: findFirst().orElse(null)
Pedido p = pedidos.stream().filter(...).findFirst().orElse(null);

// BIEN: Optional toda la cadena
Optional<Pedido> pedido = pedidos.stream().filter(...).findFirst();
```

---

## 5. Optional {#optional}

```java
// Encadenar sin isPresent()
String nombreCliente = clienteRepository.buscarPorId(id)
    .map(Cliente::nombre)
    .map(Nombre::completo)
    .orElse("Desconocido");

// orElseThrow con excepción de dominio
Cliente cliente = clienteRepository.buscarPorId(id)
    .orElseThrow(() -> new ClienteNoEncontradoException(id));

// flatMap para Optional anidados
Optional<Direccion> direccion = clienteRepository.buscarPorId(id)
    .flatMap(Cliente::direccionPrincipal);

// ifPresentOrElse para side-effects controlados
pedidoRepository.buscarPorId(id)
    .ifPresentOrElse(
        this::procesarPedido,
        () -> log.warn(STR."Pedido \{id} no encontrado")
    );
```

**Reglas de uso:**
- Nunca usar `Optional` como tipo de parámetro de método.
- Nunca serializar `Optional` directamente — mapear antes.
- No usar en campos de clase.
- No usar `Optional.get()` sin `isPresent()` previo — usar `orElseThrow()` siempre.

---

## 6. Interfaces funcionales y composición {#functional-interfaces}

```java
// Predicados reutilizables como constantes estáticas
public final class PedidoPredicates {
    public static final Predicate<Pedido> CONFIRMADO =
        p -> p.estado() == EstadoPedido.CONFIRMADO;
    public static final Predicate<Pedido> TIENE_LINEAS =
        p -> !p.lineas().isEmpty();
    public static final Predicate<Pedido> LISTO_PARA_ENVIO =
        CONFIRMADO.and(TIENE_LINEAS);
    private PedidoPredicates() {}
}

// Composición de funciones con andThen
Function<String, String> normalizar =
    ((Function<String, String>) String::toLowerCase)
    .andThen(String::strip)
    .andThen(s -> s.replaceAll("\\s+", " "));

// UnaryOperator para transformaciones del mismo tipo
UnaryOperator<Pedido> aplicarDescuento =
    pedido -> pedido.withTotal(pedido.total().aplicarDescuento(0.10));

// Uso en pipelines
List<Pedido> pedidosListos = pedidos.stream()
    .filter(PedidoPredicates.LISTO_PARA_ENVIO)
    .map(aplicarDescuento)
    .toList();
```

---

## 7. Virtual Threads y concurrencia estructurada {#virtual-threads}

### Virtual threads (Project Loom — estable desde Java 21)

```java
// Executor de virtual threads — código bloqueante escala sin cambios
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    List<Future<ResultadoDto>> futures = ids.stream()
        .map(id -> executor.submit(() -> procesarId(id)))
        .toList();

    for (var future : futures) {
        procesar(future.get());
    }
}

// Thread por tarea con nombre
Thread.ofVirtual()
    .name("procesar-pedido-", 0)
    .start(() -> procesarPedido(pedido));
```

### Structured Concurrency (Java 25 — JEP 505)

```java
// ShutdownOnFailure — cancela todo si uno falla
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var inventario = scope.fork(() -> inventarioService.verificar(sku));
    var precio     = scope.fork(() -> precioService.calcular(sku));
    scope.join().throwIfFailed();  // propaga la primera excepción
    return new DisponibilidadDto(inventario.get(), precio.get());
}

// ShutdownOnSuccess — retorna el primero que termina bien
try (var scope = new StructuredTaskScope.ShutdownOnSuccess<String>()) {
    scope.fork(() -> cacheService.buscar(key));
    scope.fork(() -> dbService.buscar(key));
    scope.join();
    return scope.result();
}
```

Toda la concurrencia paralela está acotada al scope. Si una tarea falla, el scope
cancela las demás automáticamente. El código es imperativo y el stack trace es claro.

---

## 8. Project Reactor — Mono y Flux {#reactor}

### Patrones de uso en arquitectura hexagonal

```java
// Puerto de salida reactivo (en domain/port/out/)
public interface PedidoRepository {
    Mono<Void> guardar(Pedido pedido);
    Mono<Pedido> buscarPorId(PedidoId id);
    Flux<Pedido> buscarPorCliente(ClienteId clienteId);
}

// Caso de uso reactivo
public Mono<PedidoConfirmadoDto> ejecutar(ConfirmarPedidoCommand cmd) {
    return pedidoRepository.buscarPorId(cmd.pedidoId())
        .switchIfEmpty(Mono.error(new PedidoNoEncontradoException(cmd.pedidoId())))
        .map(Pedido::confirmar)
        .flatMap(pedidoRepository::guardar)
        .doOnNext(p -> eventPublisher.publicar(
            new PedidoConfirmado(p.id(), Instant.now())))
        .map(PedidoConfirmadoDto::desde);
}

// Flux con backpressure y manejo de errores por elemento
Flux<ResultadoDto> procesarLote(List<ItemDto> items) {
    return Flux.fromIterable(items)
        .flatMap(
            item -> procesarItem(item)
                .onErrorResume(e -> Mono.just(ResultadoDto.error(item.id(), e.getMessage()))),
            8  // concurrencia máxima
        )
        .timeout(Duration.ofSeconds(30));
}
```

### Antipatrones Reactor

```java
// MAL: bloquear dentro de un pipeline reactivo — deadlock potencial
Mono<Dto> mal = pedidoMono.map(p -> {
    return otroRepository.findById(p.id()).block();  // NUNCA
});

// BIEN: flatMap con Mono
Mono<Dto> bien = pedidoMono.flatMap(p ->
    otroRepository.findById(p.id()).map(o -> combinar(p, o))
);

// MAL: subscribe() dentro de un método que retorna Mono/Flux
void malMetodo(Mono<X> m) {
    m.subscribe(this::procesar);  // pierde el control del ciclo de vida
}

// BIEN: retornar el Mono sin suscribirse (el caller controla la ejecución)
Mono<Void> bienMetodo(Mono<X> m) {
    return m.flatMap(this::procesar);
}

// MAL: crear Mono con new Thread() o CompletableFuture sin scheduler
Mono<X> mal2 = Mono.create(sink -> new Thread(() -> sink.success(computar())).start());

// BIEN: usar Mono.fromCallable con scheduler apropiado
Mono<X> bien2 = Mono.fromCallable(this::computar)
    .subscribeOn(Schedulers.boundedElastic());
```

---

## 9. String Templates {#string-templates}

Estables en Java 25 (JEP 430 / 459):

```java
// STR — interpolación simple
String mensaje = STR."Pedido \{pedidoId} confirmado para cliente \{cliente.nombre()}";

// FMT — con formato de printf
String precio = FMT."Total: %.2f\{monto.cantidad()} \{monto.moneda()}";

// En logging — más legible que String.format()
log.info(STR."Procesando \{items.size()} items del lote \{loteId}");

// Multilínea
String sql = STR."""
    SELECT * FROM pedidos
    WHERE cliente_id = \{clienteId.valor()}
    AND estado = '\{estado.name()}'
    """;
```

Usar string templates en lugar de `String.format()` o concatenación con `+` en código
nuevo. Los errores de tipos se detectan en tiempo de compilación.

---

## 10. Decisión: virtual threads vs. reactive {#decision}

```
¿El stack ya usa WebFlux o R2DBC?
    SÍ → continuar con Reactor (Mono/Flux)
         Mezclar bloqueante en WebFlux causa deadlocks y pérdida de backpressure
    NO → ¿Servicio I/O-bound con >100 threads concurrentes esperados?
            SÍ → virtual threads + WebMvc + código bloqueante convencional
                 Habilitar: spring.threads.virtual.enabled=true
            NO → threads de plataforma convencionales son suficientes

¿Necesito backpressure explícita o streaming de eventos (SSE, WebSocket)?
    SÍ → Reactor/Flux independientemente del resto
    NO → virtual threads son más simples, depurables y legibles

¿El equipo es nuevo en programación reactiva?
    SÍ → virtual threads — misma escala, curva de aprendizaje mínima
    NO → evaluar según los criterios anteriores
```

En proyectos nuevos sin restricción de stack, preferir virtual threads por
legibilidad y facilidad de debugging. Reservar Reactor para pipelines donde la
composición de operadores y el backpressure aporten valor real.
