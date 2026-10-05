# Anti-patterns de tests Java reactivos

Referencia para code review de tests. Cada entrada trae el código problemático, el problema
concreto y el fix.

---

## AP-R: Reactivos

### AP-R1: `.block()` en un test reactivo

```java
// INCORRECTO
@Test
void bad_test() {
    Pedido result = useCase.buscar(id).block();
    assertThat(result.getEstado()).isEqualTo(CONFIRMADO);
}

// CORRECTO
@Test
void good_test() {
    StepVerifier.create(useCase.buscar(id))
        .assertNext(p -> assertThat(p.getEstado()).isEqualTo(CONFIRMADO))
        .verifyComplete();
}
```

**Problema:** `.block()` puede causar deadlock en el scheduler de WebFlux y no garantiza un
scheduler no-bloqueante en tests. Oculta problemas de backpressure y de completado del publisher.

---

### AP-R2: `StepVerifier` sin llamada terminal

```java
// INCORRECTO — este test siempre pasa, incluso si el Mono emite error
@Test
void bad_test() {
    StepVerifier.create(useCase.ejecutar(command))
        .expectNext(expectedDto);
    // falta .verifyComplete() — el verifier nunca se suscribe
}

// CORRECTO
@Test
void good_test() {
    StepVerifier.create(useCase.ejecutar(command))
        .expectNext(expectedDto)
        .verifyComplete();
}
```

**Problema:** `StepVerifier.create(...)` construye el verifier pero no lo suscribe. La suscripción
ocurre solo con `.verifyComplete()`, `.verify()` o `.verifyError()`. Sin ellas, el test es un
no-op silencioso.

---

### AP-R3: `Thread.sleep()` para esperar resultados reactivos

```java
// INCORRECTO
@Test
void bad_test() throws InterruptedException {
    service.procesarAsync(command).subscribe();
    Thread.sleep(1000);                     // frágil bajo carga en CI
    verify(repository).guardar(any());
}

// CORRECTO
@Test
void good_test() {
    StepVerifier.create(service.procesarAsync(command))
        .verifyComplete();
    verify(repository).guardar(any());
}

// CORRECTO con tiempo virtual para delays
@Test
void good_test_with_delay() {
    StepVerifier.withVirtualTime(() -> service.procesarConDelay(command))
        .thenAwait(Duration.ofSeconds(5))
        .verifyComplete();
}
```

**Problema:** los tiempos reales hacen los tests flaky bajo carga en CI, y `Thread.sleep()` en
WebFlux puede bloquear el scheduler.

---

## AP-S: Contexto de Spring

### AP-S1: `@SpringBootTest` para unit tests

```java
// INCORRECTO — levanta todo el contexto para testear un caso de uso
@SpringBootTest
class ConfirmarPedidoUseCaseTest {
    @Autowired ConfirmarPedidoUseCase useCase;
}

// CORRECTO — sin Spring para unit tests de application
@ExtendWith(MockitoExtension.class)
class ConfirmarPedidoUseCaseTest {
    @Mock PedidoRepository pedidoRepository;
    @InjectMocks ConfirmarPedidoUseCase useCase;
}
```

**Problema:** `@SpringBootTest` inicializa datasources, beans y configuración. Para un unit test de
caso de uso, el tiempo pasa de ~50 ms a 10-30 s.

---

### AP-S2: `@MockitoBean` fuera de un contexto de Spring

```java
// INCORRECTO — @MockitoBean requiere un ApplicationContext
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @MockitoBean PedidoRepository repository;   // error en runtime
}

// CORRECTO
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository repository;          // @Mock de Mockito, sin Spring
}
```

**Problema:** `@MockitoBean` es una anotación de Spring Test. Fuera de `@WebFluxTest` /
`@SpringBootTest` no hay contexto donde registrar el mock.

Nota de versión: `@MockBean` quedó deprecada en Spring Boot 3.4 y fue removida en Spring Boot 4.
En proyectos con Spring Boot 4, todo `@MockBean` heredado se reemplaza por `@MockitoBean`.

---

### AP-S3: `@WebFluxTest` sin especificar el controller

```java
// INCORRECTO — carga todos los controllers del classpath
@WebFluxTest
class PedidoControllerTest { ... }

// CORRECTO — solo el controller bajo test
@WebFluxTest(PedidoController.class)
class PedidoControllerTest { ... }
```

**Problema:** sin la clase, `@WebFluxTest` intenta cargar todos los `@RestController`, y falla si
falta un `@MockitoBean` para alguna de sus dependencias.

---

### AP-S4: Slice web sin la configuración de seguridad real

```java
// INCORRECTO — el slice deja pasar rutas que en producción están protegidas
@WebFluxTest(PedidoController.class)
class PedidoControllerTest { ... }

// CORRECTO
@WebFluxTest(PedidoController.class)
@Import(SecurityConfig.class)
class PedidoControllerTest { ... }
```

**Problema:** el test da luz verde a un endpoint que en producción responde 401, así que no
detecta rutas mal declaradas en `pathMatchers` (OWASP A01).

---

## AP-M: Mocking

### AP-M1: Mockear la clase bajo test

```java
// INCORRECTO — mockeas lo que quieres testear
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoService service;            // ← esto ES lo que vas a testear
    @InjectMocks PedidoServiceImpl impl;
}

// CORRECTO — mockear solo las dependencias (puertos)
@ExtendWith(MockitoExtension.class)
class PedidoServiceTest {
    @Mock PedidoRepository repository;
    @Mock DomainEventPublisher publisher;
    @InjectMocks PedidoServiceImpl service;
}
```

**Problema:** el test nunca ejecuta el código real.

---

### AP-M2: Mockear la implementación concreta en lugar del puerto

```java
// INCORRECTO
@Mock R2dbcPedidoRepository r2dbcRepository;
@InjectMocks ConfirmarPedidoUseCase useCase;

// CORRECTO — el puerto (interfaz en domain/port/out/)
@Mock PedidoRepository pedidoRepository;
@InjectMocks ConfirmarPedidoUseCase useCase;
```

**Problema:** el caso de uso depende del puerto, no de la implementación. Mockear la concreta ata
el test a infraestructura y rompe la regla de dependencia hexagonal.

---

### AP-M3: `verify()` como única assertion

```java
// INCORRECTO — verifica que se llamó el método, no el resultado
@Test
void bad_test() {
    useCase.ejecutar(command).subscribe();
    verify(repository).guardar(any());
}

// CORRECTO — resultado y side effect
@Test
void good_test() {
    StepVerifier.create(useCase.ejecutar(command))
        .assertNext(dto -> assertThat(dto.estado()).isEqualTo(CONFIRMADO))
        .verifyComplete();

    verify(repository).guardar(argThat(p -> p.getEstado() == CONFIRMADO));
}
```

---

## AP-T: Estructura

### AP-T1: Estado mutable compartido entre tests

```java
// INCORRECTO — campo mutable sin reset
class PedidoServiceTest {
    private Pedido pedido = Pedido.crear(lineas);

    @Test void test1() { pedido = pedido.confirmar(); }
    @Test void test2() { /* pedido ya está confirmado si test1 corrió primero */ }
}

// CORRECTO — recrear en @BeforeEach
class PedidoServiceTest {
    private Pedido pedido;

    @BeforeEach
    void setUp() {
        pedido = Pedido.crear(lineas);
    }
}
```

Un test que falla al cambiar el orden de ejecución señala estado compartido — es un bug de diseño
del test, no del runner.

---

### AP-T2: Test sin assertions

```java
// INCORRECTO — no prueba nada, siempre pasa
@Test
void bad_test() {
    useCase.ejecutar(command);
}

// CORRECTO
@Test
void good_test() {
    StepVerifier.create(useCase.ejecutar(command))
        .assertNext(dto -> assertThat(dto).isNotNull())
        .verifyComplete();
}
```

---

### AP-T3: Tests que dependen del orden

```java
// INCORRECTO — test2 depende del estado que dejó test1
static int counter = 0;

@Test @Order(1) void test1() { counter++; }
@Test @Order(2) void test2() { assertThat(counter).isEqualTo(1); }

// CORRECTO — cada test es independiente
@Test
void test_increment() {
    int counter = 0;
    counter++;
    assertThat(counter).isEqualTo(1);
}
```

---

## AP-H: Arquitectura hexagonal

### AP-H1: Spring en tests de dominio

```java
// INCORRECTO
@SpringBootTest
class PedidoTest { ... }

// CORRECTO — POJO puro
class PedidoTest { ... }
```

### AP-H2: Test de caso de uso que instancia el adaptador real

```java
// INCORRECTO
class ConfirmarPedidoUseCaseTest {
    R2dbcPedidoRepository realRepo = new R2dbcPedidoRepository(realDataSource);
    ConfirmarPedidoUseCase useCase = new ConfirmarPedidoUseCase(realRepo);
}

// CORRECTO — el puerto mockeado
@ExtendWith(MockitoExtension.class)
class ConfirmarPedidoUseCaseTest {
    @Mock PedidoRepository repository;
    @InjectMocks ConfirmarPedidoUseCase useCase;
}
```

---

## AP-P: Rendimiento

| Anti-pattern | Impacto | Fix |
|---|---|---|
| `@SpringBootTest` para unit tests | +10-30 s por clase | `@ExtendWith(MockitoExtension.class)` |
| `Thread.sleep(1000)` por test | +1 s acumulado, y flaky | `StepVerifier.withVirtualTime()` |
| `@Testcontainers` sin `static` | Contenedor nuevo por test (30-60 s c/u) | `@Container static` |
| Varias clases `@SpringBootTest` con configuración distinta | Recarga de contexto por clase | Clase base abstracta compartida |
| `@WebFluxTest` sin clase | Carga todos los controllers | `@WebFluxTest(XController.class)` |

---

## Racionalizaciones frecuentes

| Excusa | Realidad |
|---|---|
| "Es un test simple, no necesita StepVerifier" | Si retorna `Mono`/`Flux`, la aserción va por `StepVerifier`: es lo único que verifica la señal de completado. |
| "Con `.block()` es más legible" | Puede hacer deadlock en WebFlux y no prueba el completado ni el orden de señales. |
| "Uso `@SpringBootTest` para tener todo el contexto" | `@WebFluxTest` trae lo necesario para la capa web y corre ~10x más rápido. |
| "Mockeo la clase bajo test, igual pruebo el comportamiento" | Se prueba el mock. Mockea sus dependencias, no ella. |
| "Primero implemento, luego los tests" | Eso es tests-after: el test escrito después tiende a confirmar lo que el código ya hace, incluido el bug. |
| "El test pasa, no necesito `.verifyComplete()`" | Pasa porque no ejecuta nada. |
