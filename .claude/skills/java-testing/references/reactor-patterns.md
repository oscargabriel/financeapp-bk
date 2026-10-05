# Aserciones reactivas — StepVerifier + WebTestClient

Recetario para tests reactivos en Spring Boot 4 + WebFlux (Reactor Test 3.7+). Toda aserción
sobre `Mono`/`Flux` pasa por `StepVerifier`; `.block()` en un test puede hacer deadlock en el
scheduler y oculta señales de completado y backpressure.

Requiere `io.projectreactor:reactor-test` en scope `test` (versión gestionada por el BOM de
Spring Boot).

## Tabla de contenido
- [Mono: valor, vacío, error](#mono-valor-vacio-error)
- [Verificar el contenido del valor emitido](#verificar-el-contenido-del-valor-emitido)
- [Flux: múltiples elementos](#flux-multiples-elementos)
- [No emisión](#no-emision)
- [Tiempo virtual](#tiempo-virtual)
- [PublisherProbe: verificar suscripción](#publisherprobe-verificar-suscripcion)
- [Simular fallos de puertos](#simular-fallos-de-puertos)
- [Contexto reactivo (MDC / requestId)](#contexto-reactivo-mdc--requestid)
- [WebTestClient por escenario](#webtestclient-por-escenario)
- [Depurar un StepVerifier](#depurar-un-stepverifier)
- [Errores comunes](#errores-comunes)

---

## Mono: valor, vacío, error

```java
// Emite un valor y completa
StepVerifier.create(mono)
    .expectNext(esperado)
    .verifyComplete();

// Completa vacío (Mono.empty() — "no encontrado" modelado como ausencia)
StepVerifier.create(mono)
    .verifyComplete();

// Termina en error
StepVerifier.create(mono)
    .expectError(SaldoInsuficienteException.class)
    .verify();

// Error verificando mensaje o contenido
StepVerifier.create(mono)
    .expectErrorSatisfies(e -> assertThat(e)
        .isInstanceOf(SaldoInsuficienteException.class)
        .hasMessageContaining("saldo"))
    .verify();
```

Verificar el payload de un error de dominio estructurado (ver skill `java-exceptions`):

```java
StepVerifier.create(useCase.ejecutar(commandConIdInexistente))
    .expectErrorMatches(error ->
        error instanceof BadRequestException bre &&
        bre.getHttpStatus() == HttpStatus.NOT_FOUND &&
        bre.getErrorResponse().errors().get(0).code().equals("DATA_NOT_FOUND"))
    .verify();
```

`verify()` y `verifyComplete()` son bloqueantes y terminales: sin ellos el verifier no se
suscribe y el test no ejecuta nada.

## Verificar el contenido del valor emitido

```java
// Preferido: AssertJ dentro de assertNext — al fallar dice qué campo falló
StepVerifier.create(mono)
    .assertNext(cuenta -> {
        assertThat(cuenta.saldo()).isEqualTo(Money.of(60));
        assertThat(cuenta.estado()).isEqualTo(Estado.ACTIVO);
    })
    .verifyComplete();

// Con predicado: al fallar solo dice "el predicado no se cumplió"
StepVerifier.create(mono)
    .expectNextMatches(c -> c.saldo().equals(Money.of(60)))
    .verifyComplete();
```

## Flux: múltiples elementos

```java
// Valores conocidos
StepVerifier.create(flux)
    .expectNext(primero, segundo, tercero)
    .verifyComplete();

// Los primeros conocidos, luego solo cantidad
StepVerifier.create(flux)
    .expectNext(primero)
    .expectNextCount(8)
    .verifyComplete();

// Todos los elementos cumplen una condición
StepVerifier.create(service.buscarConfirmados())
    .thenConsumeWhile(p -> p.estado() == EstadoPedido.CONFIRMADO)
    .verifyComplete();

// Valores y después error en la misma secuencia
StepVerifier.create(flux.concatWith(Flux.error(new RuntimeException("fallo"))))
    .expectNext(primero, segundo)
    .expectError(RuntimeException.class)
    .verify();
```

## No emisión

```java
// Flux vacío
StepVerifier.create(flux)
    .verifyComplete();

// No emite nada en una ventana de tiempo y sigue vivo
StepVerifier.create(flux)
    .expectSubscription()
    .expectNoEvent(Duration.ofSeconds(1))
    .thenCancel()
    .verify();
```

## Tiempo virtual

Para retries, timeouts, `delayElements`, `Mono.delay` o `Flux.interval`. El reloj virtual avanza
sin que el test espere de verdad.

```java
StepVerifier.withVirtualTime(() -> servicio.conReintentoCadaHora())
    .expectSubscription()
    .thenAwait(Duration.ofHours(1))
    .expectNext(resultado)
    .verifyComplete();

// Scheduler / generación periódica
StepVerifier.withVirtualTime(() -> generador.pagosDiarios())
    .thenAwait(Duration.ofDays(3))
    .expectNextCount(3)
    .thenCancel()
    .verify();
```

El publisher se crea **dentro** del `Supplier`. Creado fuera, el operador temporal ya quedó atado
al scheduler real y el tiempo virtual no aplica.

## PublisherProbe: verificar suscripción

Cuando importa si un puerto fue suscrito, no solo invocado — típico para probar ramas
("ante error de validación el repositorio nunca se consulta").

```java
PublisherProbe<Void> probe = PublisherProbe.empty();
when(eventos.publicar(any())).thenReturn(probe.mono());

StepVerifier.create(casoDeUso.ejecutar(command))
    .verifyComplete();

probe.assertWasSubscribed();      // o assertWasNotSubscribed()
```

## Simular fallos de puertos

Los puertos devuelven tipos reactivos, así que el fallo es una señal de error, no una excepción
lanzada de forma síncrona:

```java
// correcto
when(repo.buscar(any())).thenReturn(Mono.error(new TimeoutException()));

// incorrecto: rompe la cadena, no prueba el manejo de error del flujo
when(repo.buscar(any())).thenThrow(new TimeoutException());
```

## Contexto reactivo (MDC / requestId)

```java
StepVerifier.create(
        service.procesar("input")
            .contextWrite(ctx -> ctx.put("requestId", "test-uuid-1234")))
    .assertNext(result -> assertThat(result).isNotNull())
    .verifyComplete();
```

Para comprobar que el valor llega al final del pipeline, captúralo desde el contexto con
`Mono.deferContextual` en el punto de interés y verifícalo con un `AtomicReference`. Ver skill
`java-logging` para el patrón de propagación completo.

## WebTestClient por escenario

```java
// GET que retorna un objeto
webTestClient.get().uri("/v1/pedidos/{id}", pedidoId)
    .exchange()
    .expectStatus().isOk()
    .expectBody(PedidoDto.class)
    .value(body -> assertThat(body.id()).isEqualTo(pedidoId));

// POST con body — 201
webTestClient.post().uri("/v1/pedidos")
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new CrearPedidoRequest(List.of(item)))
    .exchange()
    .expectStatus().isCreated()
    .expectBody(PedidoDto.class)
    .value(body -> assertThat(body.estado()).isEqualTo(EstadoPedido.PENDIENTE));

// GET que retorna lista
webTestClient.get().uri("/v1/pedidos")
    .exchange()
    .expectStatus().isOk()
    .expectBodyList(PedidoDto.class)
    .hasSize(2)
    .value(list -> assertThat(list).extracting(PedidoDto::estado)
        .containsOnly(EstadoPedido.PENDIENTE));

// DELETE — 204 sin body
webTestClient.delete().uri("/v1/pedidos/{id}", pedidoId)
    .exchange()
    .expectStatus().isNoContent()
    .expectBody().isEmpty();

// PUT — 200 con body actualizado
webTestClient.put().uri("/v1/pedidos/{id}", pedidoId)
    .contentType(MediaType.APPLICATION_JSON)
    .bodyValue(new ActualizarPedidoRequest(nuevaLinea))
    .exchange()
    .expectStatus().isOk()
    .expectBody(PedidoDto.class)
    .value(body -> assertThat(body.id()).isEqualTo(pedidoId));

// Error estructurado (formato de la skill java-exceptions)
webTestClient.post().uri("/v1/pedidos/{id}/confirmar", unknownId)
    .exchange()
    .expectStatus().isNotFound()
    .expectBody()
    .jsonPath("$.errors").isArray()
    .jsonPath("$.errors[0].code").isEqualTo("DATA_NOT_FOUND")
    .jsonPath("$.errors[0].field").isEqualTo("pedidoId");

// Endpoint protegido sin credenciales (cobertura mínima de seguridad, OWASP A01)
webTestClient.get().uri("/v1/pedidos")
    .exchange()
    .expectStatus().isUnauthorized();
```

## Depurar un StepVerifier

```java
// Ver las señales del pipeline
StepVerifier.create(mono.log("debug"))
    .expectNext(value)
    .verifyComplete();

// Assembly traces: stack traces que apuntan al operador de origen
@BeforeAll
static void enableReactorDebug() {
    ReactorDebugAgent.init();     // requiere io.projectreactor:reactor-tools en test
}

// O acotar el operador sospechoso
mono.checkpoint("nombre-servicio:nombre-operacion")

// Timeout explícito (verifyComplete() espera indefinidamente por defecto)
StepVerifier.create(mono)
    .expectNext(value)
    .verifyComplete(Duration.ofSeconds(5));
```

Para diagnóstico avanzado de stack traces reactivos, ver la skill `java-debugging`.

## Errores comunes

- **Falta `verify()` / `verifyComplete()`**: el verifier no se suscribe y el test pasa sin probar
  nada. Es el fallo más frecuente.
- **`.block()` en el test**: pierdes la verificación de señales y bloqueas un thread.
- **Esperas reales** (`Thread.sleep`, `delayElement` sin tiempo virtual): tests lentos y flaky.
- **Publisher creado fuera del `Supplier` de `withVirtualTime`**: el tiempo virtual no aplica.
- **`thenThrow` en stubs de puertos reactivos**: rompe la cadena; usa `Mono.error`/`Flux.error`.
- **`expectNext` con objetos sin `equals` por valor**: compara por identidad y falla. Usa
  `assertNext`, o modela con `record` (trae `equals` gratis).
