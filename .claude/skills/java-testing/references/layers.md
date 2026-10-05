# Plantillas por capa

Plantillas concretas para cada capa hexagonal. Adapta paquetes, tipos y dominio al proyecto real
— los ejemplos usan dominios neutros (usuario, cuenta, pedido). Lee la sección de la capa que
estás probando.

## Tabla de contenido
- [Dominio — unitario puro](#dominio--unitario-puro)
- [Aplicación — caso de uso](#aplicacion--caso-de-uso)
- [Adaptador de entrada web — slice](#adaptador-de-entrada-web--slice)
- [Adaptador de salida persistencia — integración](#adaptador-de-salida-persistencia--integracion)
- [Adaptador de salida HTTP externo — integración](#adaptador-de-salida-http-externo--integracion)
- [E2E / flujo crítico](#e2e--flujo-critico)
- [Test de arquitectura (ArchUnit)](#test-de-arquitectura-archunit)

---

## Dominio — unitario puro

Sin Spring, sin mocks, sin reactividad (el dominio idealmente no expone tipos reactivos). Es el
test más rápido y el que más debes tener. Prueba invariantes, validaciones y reglas puras.

```java
class CuentaTest {

    @Test
    void noPermiteRetirarMasDelSaldoDisponible() {
        // given
        var cuenta = unaCuenta().conSaldo(Money.of(100)).build();

        // when / then
        assertThatThrownBy(() -> cuenta.retirar(Money.of(150)))
            .isInstanceOf(SaldoInsuficienteException.class);
    }

    @Test
    void retirarDescuentaElSaldo() {
        var cuenta = unaCuenta().conSaldo(Money.of(100)).build();

        cuenta.retirar(Money.of(30));

        assertThat(cuenta.saldo()).isEqualTo(Money.of(70));
    }
}
```

Para value objects, prueba igualdad por valor, normalización y rechazo de valores inválidos en el
constructor/factory. Todo VO que valide entrada externa lleva al menos un test de rechazo con
input malicioso (formato roto, longitud excesiva, caracteres de inyección).

Usa `assertThatThrownBy` de AssertJ para excepciones.

---

## Aplicación — caso de uso

JUnit 5 + Mockito sobre los **puertos de salida**, `StepVerifier` para el resultado. Sin contexto
de Spring. Prueba la **orquestación**: que el caso de uso llama a los puertos correctos, en el
orden correcto, y compone bien el flujo reactivo — no la lógica de dominio (esa ya la cubriste).

```java
@ExtendWith(MockitoExtension.class)
class RetirarFondosTest {

    @Mock CuentaRepository cuentas;          // puerto de salida
    @Mock EventPublisher eventos;            // puerto de salida
    @InjectMocks RetirarFondos retirar;      // caso de uso bajo prueba

    @Test
    void retiraYPublicaEventoCuandoHaySaldo() {
        // given
        var cuenta = unaCuenta().conSaldo(Money.of(100)).build();
        when(cuentas.buscarPorId(cuenta.id())).thenReturn(Mono.just(cuenta));
        when(cuentas.guardar(any())).thenAnswer(inv -> Mono.just(inv.getArgument(0)));
        when(eventos.publicar(any())).thenReturn(Mono.empty());

        // when
        var resultado = retirar.ejecutar(cuenta.id(), Money.of(40));

        // then
        StepVerifier.create(resultado)
            .assertNext(c -> assertThat(c.saldo()).isEqualTo(Money.of(60)))
            .verifyComplete();
        verify(eventos).publicar(any(FondosRetirados.class));
    }

    @Test
    void propagaErrorYNoPublicaCuandoLaCuentaNoExiste() {
        when(cuentas.buscarPorId(any())).thenReturn(Mono.empty());

        var resultado = retirar.ejecutar(UUID.randomUUID(), Money.of(40));

        StepVerifier.create(resultado)
            .expectError(CuentaNoEncontradaException.class)
            .verify();
        verify(eventos, never()).publicar(any());
    }
}
```

Se mockean los puertos (interfaces de `domain/port/`), nunca las implementaciones concretas ni la
clase bajo test. Para verificar que un puerto **no** fue suscrito (no solo no invocado), usa
`PublisherProbe` — ver `reactor-patterns.md`.

---

## Adaptador de entrada web — slice

`@WebFluxTest(XController.class)` carga solo la capa web del controller indicado. Mockea el caso
de uso con `@MockitoBean` (`@MockBean` fue removido en Spring Boot 4). Prueba routing,
(de)serialización, validación de entrada y status codes; no lógica de negocio.

```java
@WebFluxTest(CuentaController.class)
class CuentaControllerTest {

    @Autowired WebTestClient client;
    @MockitoBean RetirarFondos retirar;      // el caso de uso, mockeado

    @Test
    void retornar200ConElSaldoActualizado() {
        var cuenta = unaCuenta().conSaldo(Money.of(60)).build();
        when(retirar.ejecutar(any(), any())).thenReturn(Mono.just(cuenta));

        client.post().uri("/cuentas/{id}/retiros", cuenta.id())
            .bodyValue(new RetiroRequest(40))
            .exchange()
            .expectStatus().isOk()
            .expectBody()
            .jsonPath("$.saldo").isEqualTo(60);
    }

    @Test
    void retornar400CuandoElMontoEsInvalido() {
        client.post().uri("/cuentas/{id}/retiros", UUID.randomUUID())
            .bodyValue(new RetiroRequest(-5))
            .exchange()
            .expectStatus().isBadRequest();
    }

    @Test
    void retornar404CuandoElCasoDeUsoNoEncuentraLaCuenta() {
        when(retirar.ejecutar(any(), any()))
            .thenReturn(Mono.error(new CuentaNoEncontradaException()));

        client.post().uri("/cuentas/{id}/retiros", UUID.randomUUID())
            .bodyValue(new RetiroRequest(40))
            .exchange()
            .expectStatus().isNotFound();
    }
}
```

Con functional routing (`RouterFunction`) en vez de `@RestController`, importa la configuración
del router con `@Import` y prueba igual con `WebTestClient`.

**Seguridad (OWASP A01):** un slice sin la configuración de seguridad real prueba rutas que en
producción están protegidas de otra forma. Importa la config (`@Import(SecurityConfig.class)`) y
cubre 401 sin credenciales, y 403 con credenciales válidas pero rol insuficiente si hay roles.

---

## Adaptador de salida persistencia — integración

Base real del mismo motor que producción vía Testcontainers. Aquí no se mockea la base: el valor
del test es probar el SQL/mapeo de verdad. Un contenedor `static` por clase — uno por test cuesta
30-60 s cada vez.

**R2DBC (`@DataR2dbcTest`):**

```java
@DataR2dbcTest
@Testcontainers
class CuentaR2dbcAdapterIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) {
        registry.add("spring.r2dbc.url", () ->
            "r2dbc:postgresql://%s:%d/%s".formatted(
                postgres.getHost(), postgres.getFirstMappedPort(), postgres.getDatabaseName()));
        registry.add("spring.r2dbc.username", postgres::getUsername);
        registry.add("spring.r2dbc.password", postgres::getPassword);
    }

    @Autowired CuentaR2dbcAdapter adapter;

    @Test
    void guardaYRecuperaUnaCuentaPreservandoElSaldo() {
        var cuenta = unaCuenta().conSaldo(Money.of(100)).build();

        var flujo = adapter.guardar(cuenta)
            .then(adapter.buscarPorId(cuenta.id()));

        StepVerifier.create(flujo)
            .assertNext(c -> assertThat(c.saldo()).isEqualTo(Money.of(100)))
            .verifyComplete();
    }
}
```

Aplica el schema con Flyway/Liquibase o `@Sql` antes de las pruebas para que las tablas existan.

**MongoDB reactivo (`@DataMongoTest`):**

```java
@DataMongoTest
@Testcontainers
class MongoPedidoRepositoryIT {

    @Container
    static MongoDBContainer mongo = new MongoDBContainer("mongo:7");

    @DynamicPropertySource
    static void mongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongo::getReplicaSetUrl);
    }

    @Autowired MongoPedidoRepository repository;

    @Test
    void guardaYRecuperaUnPedido() {
        var pedido = unPedido().build();

        StepVerifier.create(repository.guardar(pedido).then(repository.buscarPorId(pedido.id())))
            .assertNext(found -> assertThat(found.id()).isEqualTo(pedido.id()))
            .verifyComplete();
    }
}
```

---

## Adaptador de salida HTTP externo — integración

MockWebServer (OkHttp) o WireMock para stubear el servicio remoto. Prueba el mapeo de
request/response, el manejo de códigos de error, timeouts y reintentos del cliente, sin tocar la
red real ni el proveedor verdadero.

```java
class TasaCambioClientIT {

    static MockWebServer server;
    TasaCambioClient client;

    @BeforeAll static void start() throws IOException {
        server = new MockWebServer();
        server.start();
    }
    @AfterAll static void stop() throws IOException { server.shutdown(); }

    @BeforeEach void setUp() {
        var base = server.url("/").toString();
        client = new TasaCambioClient(WebClient.create(base));
    }

    @Test
    void mapeaLaRespuestaExitosaADominio() {
        server.enqueue(new MockResponse()
            .setHeader("Content-Type", "application/json")
            .setBody("""
                { "base": "USD", "rate": 4100.0 }
                """));

        StepVerifier.create(client.obtenerTasa("USD"))
            .assertNext(t -> assertThat(t.rate()).isEqualTo(4100.0))
            .verifyComplete();
    }

    @Test
    void propagaErrorDeProveedorAnte5xx() {
        server.enqueue(new MockResponse().setResponseCode(503));

        StepVerifier.create(client.obtenerTasa("USD"))
            .expectError(ProveedorNoDisponibleException.class)
            .verify();
    }
}
```

Para timeouts y retries con backoff sin esperas reales, combina con tiempo virtual (ver
`reactor-patterns.md`).

---

## E2E / flujo crítico

`@SpringBootTest` con el contexto completo + Testcontainers. Pocos y selectivos: solo happy paths
de flujos críticos de negocio. Son el test más caro; los casos borde van en las capas internas.

```java
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Testcontainers
class RetiroFlujoCompletoIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry registry) { /* igual que arriba */ }

    @Autowired WebTestClient client;

    @Test
    void unUsuarioPuedeRetirarYVerSuSaldoActualizado() {
        // crea la cuenta vía API, retira, y verifica el saldo persistido — punta a punta
    }
}
```

Si varias clases E2E comparten contexto, extrae una clase base abstracta con `@SpringBootTest`
para que Spring reutilice el mismo contexto en vez de recargarlo por clase.

---

## Test de arquitectura (ArchUnit)

Una clase por proyecto que congela las reglas de dependencia hexagonales:

```java
@AnalyzeClasses(packages = "com.empresa.app")
class HexagonalArchitectureTest {

    @ArchTest
    ArchRule domainNoSpring = noClasses()
        .that().resideInAPackage("..domain..")
        .should().dependOnClassesThat()
        .resideInAPackage("org.springframework..");

    @ArchTest
    ArchRule layeredDependencies = layeredArchitecture()
        .consideringAllDependencies()
        .layer("Domain").definedBy("..domain..")
        .layer("Application").definedBy("..application..")
        .layer("Infrastructure").definedBy("..infrastructure..")
        .whereLayer("Domain").mayNotAccessLayersExcept()
        .whereLayer("Application").mayOnlyAccessLayers("Domain")
        .whereLayer("Infrastructure").mayOnlyAccessLayers("Application", "Domain");
}
```
