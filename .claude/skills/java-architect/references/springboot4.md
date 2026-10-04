# Spring Boot 4 — Arquitectura Hexagonal

Spring Boot 4 se basa en Spring Framework 7 y Jakarta EE 11. Esta referencia cubre
cómo encajar Spring Boot 4 en la capa de infraestructura sin contaminar el dominio.

## Tabla de contenidos
1. [Wiring de puertos con @Configuration](#wiring)
2. [Virtual Threads](#virtual-threads)
3. [WebFlux vs. WebMvc](#webflux-vs-webmvc)
4. [R2DBC como adaptador de persistencia](#r2dbc)
5. [Testing slices](#testing)
6. [Observability — Micrometer + Actuator](#observability)
7. [GraalVM Native Image](#native)

---

## 1. Wiring de puertos con @Configuration {#wiring}

El dominio y la capa de aplicación no conocen Spring. El cableado ocurre exclusivamente
en `infrastructure/config/`:

```java
@Configuration
public class ApplicationConfig {

    // Cablear caso de uso con sus puertos de salida
    @Bean
    public ConfirmarPedidoUseCase confirmarPedidoUseCase(
            PedidoRepository pedidoRepository,
            DomainEventPublisher eventPublisher) {
        return new ConfirmarPedidoUseCase(pedidoRepository, eventPublisher);
    }

    @Bean
    public CrearPedidoUseCase crearPedidoUseCase(PedidoRepository pedidoRepository) {
        return new CrearPedidoUseCase(pedidoRepository);
    }
}
```

Los adaptadores de infraestructura sí pueden usar `@Component` / `@Repository` /
`@Service` para que Spring los detecte:

```java
@Repository
public class R2dbcPedidoRepository implements PedidoRepository {
    private final PedidoR2dbcEntityRepository springRepo;
    private final PedidoEntityMapper mapper;

    // inyección por constructor
    public R2dbcPedidoRepository(PedidoR2dbcEntityRepository springRepo,
                                  PedidoEntityMapper mapper) {
        this.springRepo = springRepo;
        this.mapper = mapper;
    }

    @Override
    public Mono<Void> guardar(Pedido pedido) {
        return springRepo.save(mapper.toEntity(pedido)).then();
    }

    @Override
    public Mono<Pedido> buscarPorId(PedidoId id) {
        return springRepo.findById(id.valor()).map(mapper::toDomain);
    }
}
```

Nunca anotar las clases de `domain/` o `application/` con `@Component`, `@Service`,
`@Autowired` u otras anotaciones de Spring.

---

## 2. Virtual Threads {#virtual-threads}

### Habilitar en Spring Boot 4

```properties
# application.properties
spring.threads.virtual.enabled=true
```

Con esta propiedad, Spring Boot 4 configura automáticamente:
- El servidor embebido (Tomcat/Jetty) para usar virtual threads por petición
- El executor de `@Async` con virtual threads
- El scheduler de `@Scheduled`

### Cuándo usar virtual threads

Con virtual threads activados, el código bloqueante convencional escala sin cambiar nada:

```java
// Esto funciona perfectamente con virtual threads — no hay que cambiar nada
@Override
public Mono<Void> guardar(Pedido pedido) {
    // Si usas JDBC bloqueante con virtual threads:
    jdbcTemplate.update("INSERT INTO pedido ...", pedido.id().valor());
    return Mono.empty();
}
```

Usar virtual threads + WebMvc (bloqueante) cuando:
- El proyecto es nuevo y no hay dependencia de WebFlux/R2DBC
- El equipo prefiere código imperativo y legible
- No se necesita backpressure explícita

### Structured Concurrency con virtual threads

```java
// Java 25 — JEP 505
try (var scope = new StructuredTaskScope.ShutdownOnFailure()) {
    var pedidoTask  = scope.fork(() -> pedidoRepository.buscarPorId(id));
    var clienteTask = scope.fork(() -> clienteRepository.buscarPorId(clienteId));
    scope.join().throwIfFailed();
    return new ResumenDto(pedidoTask.get(), clienteTask.get());
}
```

Toda la concurrencia paralela está dentro del scope; si una tarea falla, las demás
se cancelan automáticamente. Código imperativo, sin callbacks.

---

## 3. WebFlux vs. WebMvc {#webflux-vs-webmvc}

### WebFlux (reactivo)

Usar cuando el stack ya usa R2DBC, el servicio es un API Gateway, o se necesita
streaming de eventos SSE/WebSocket:

```java
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final ConfirmarPedidoPort confirmarPedido;

    public PedidoController(ConfirmarPedidoPort confirmarPedido) {
        this.confirmarPedido = confirmarPedido;
    }

    @PostMapping("/{id}/confirmar")
    public Mono<PedidoResponse> confirmar(@PathVariable UUID id) {
        return confirmarPedido.ejecutar(new ConfirmarPedidoCommand(new PedidoId(id)))
            .map(PedidoResponse::desde);
    }

    @GetMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<PedidoResponse> stream() {
        return pedidoQueryPort.obtenerTodos().map(PedidoResponse::desde);
    }
}
```

### WebMvc con Virtual Threads

Usar cuando el equipo prefiere código bloqueante y virtual threads proporcionan el
paralelismo necesario:

```java
@RestController
@RequestMapping("/pedidos")
public class PedidoController {

    private final ConfirmarPedidoPort confirmarPedido;

    @PostMapping("/{id}/confirmar")
    public PedidoResponse confirmar(@PathVariable UUID id) {
        // Bloqueante — OK con virtual threads
        return PedidoResponse.desde(
            confirmarPedido.ejecutarSincrono(new ConfirmarPedidoCommand(new PedidoId(id)))
        );
    }
}
```

**Regla**: no mezclar WebMvc bloqueante con R2DBC reactivo ni WebFlux con JDBC bloqueante.
Elegir un modelo y ser consistente en toda la capa de infraestructura.

---

## 4. R2DBC como adaptador de persistencia {#r2dbc}

R2DBC es el adaptador reactivo de persistencia. La entidad R2DBC vive en
`infrastructure/persistence/` — nunca en `domain/`:

```java
// infrastructure/persistence/PedidoR2dbcEntity.java
@Table("pedidos")
public record PedidoR2dbcEntity(
    @Id UUID id,
    String estado,
    Instant creadoEn
) {}

// Repositorio Spring Data R2DBC (interfaz de Spring, no del dominio)
public interface PedidoR2dbcEntityRepository
    extends ReactiveCrudRepository<PedidoR2dbcEntity, UUID> {

    Flux<PedidoR2dbcEntity> findByClienteId(UUID clienteId);
}
```

El mapper convierte entre la entidad R2DBC y el modelo de dominio:

```java
@Component
public class PedidoEntityMapper {

    public PedidoR2dbcEntity toEntity(Pedido pedido) {
        return new PedidoR2dbcEntity(
            pedido.id().valor(),
            pedido.estado().name(),
            Instant.now()
        );
    }

    public Pedido toDomain(PedidoR2dbcEntity entity) {
        return Pedido.reconstituir(
            new PedidoId(entity.id()),
            EstadoPedido.valueOf(entity.estado())
        );
    }
}
```

### Transacciones reactivas

```java
@Transactional  // Spring maneja la transacción reactiva con R2DBC
public Mono<Void> guardar(Pedido pedido) {
    return entityRepository.save(mapper.toEntity(pedido)).then();
}
```

---

## 5. Testing slices {#testing}

### Test de dominio (sin Spring)

```java
class PedidoTest {
    @Test
    void confirmar_cambia_estado_a_confirmado() {
        var pedido = Pedido.crear(List.of(lineaDePrueba()));
        var confirmado = pedido.confirmar();
        assertThat(confirmado.estado()).isEqualTo(EstadoPedido.CONFIRMADO);
    }
}
```

### Test de controlador — @WebFluxTest

```java
@WebFluxTest(PedidoController.class)
class PedidoControllerTest {

    @MockBean
    private ConfirmarPedidoPort confirmarPedido;

    @Autowired
    private WebTestClient webTestClient;

    @Test
    void confirmar_pedido_retorna_200() {
        when(confirmarPedido.ejecutar(any()))
            .thenReturn(Mono.just(new PedidoConfirmadoDto(/* ... */)));

        webTestClient.post().uri("/pedidos/{id}/confirmar", UUID.randomUUID())
            .exchange()
            .expectStatus().isOk();
    }
}
```

### Test de repositorio — @DataR2dbcTest

```java
@DataR2dbcTest
class R2dbcPedidoRepositoryTest {

    @Autowired
    private PedidoR2dbcEntityRepository springRepo;

    @Test
    void guardar_y_buscar_pedido() {
        var entity = new PedidoR2dbcEntity(UUID.randomUUID(), "PENDIENTE", Instant.now());
        springRepo.save(entity)
            .then(springRepo.findById(entity.id()))
            .as(StepVerifier::create)
            .assertNext(e -> assertThat(e.estado()).isEqualTo("PENDIENTE"))
            .verifyComplete();
    }
}
```

### Test de integración completo

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PedidoIntegrationTest {
    // Usar Testcontainers para la base de datos real
}
```

---

## 6. Observability — Micrometer + Actuator {#observability}

Spring Boot 4 incluye Micrometer con trazas distribuidas (OTLP) de forma nativa:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-tracing-bridge-otel</artifactId>
</dependency>
```

```properties
management.tracing.sampling.probability=1.0
management.endpoints.web.exposure.include=health,info,metrics,prometheus
```

Instrumentar casos de uso con `@Observed` (no contamina el dominio si se aplica solo
en infraestructura o en la clase de use case como anotación no invasiva):

```java
@Observed(name = "pedido.confirmar")
public Mono<PedidoConfirmadoDto> ejecutar(ConfirmarPedidoCommand command) {
    // ...
}
```

---

## 7. GraalVM Native Image {#native}

Spring Boot 4 mejora el soporte nativo. Consideraciones para arquitectura hexagonal:

**Problema**: Spring usa proxies dinámicos para `@Configuration` y AOP. Con native image,
los proxies deben declararse en tiempo de compilación.

**Solución**: preferir `@Configuration(proxyBeanMethods = false)` cuando los `@Bean`
no se llaman entre sí:

```java
@Configuration(proxyBeanMethods = false)  // sin proxy CGLIB — más eficiente en native
public class ApplicationConfig {
    @Bean
    public ConfirmarPedidoUseCase confirmarPedidoUseCase(
            PedidoRepository repo, DomainEventPublisher pub) {
        return new ConfirmarPedidoUseCase(repo, pub);
    }
}
```

Las clases de dominio (POJOs, records) no necesitan configuración especial para native
ya que no usan reflection de Spring. Los adaptadores con `@RestController` y `@Repository`
son procesados automáticamente por el AOT de Spring Boot 4.
