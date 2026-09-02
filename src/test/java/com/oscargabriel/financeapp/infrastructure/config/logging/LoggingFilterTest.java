package com.oscargabriel.financeapp.infrastructure.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

class LoggingFilterTest {

    private static final String REQUEST_ID = "requestId";

    private final LoggingFilter filter = new LoggingFilter();

    @Test
    void dejaUnRequestIdUuidEnLosAtributosDelExchange() {
        MockServerWebExchange exchange = exchange();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(new AtomicReference<>())))
                .verifyComplete();

        assertThat(exchange.getAttributes()).containsKey(REQUEST_ID);
        assertThat(UUID.fromString((String) exchange.getAttributes().get(REQUEST_ID))).isNotNull();
    }

    @Test
    void propagaElMismoRequestIdAlContextoReactivoDeLaCadena() {
        MockServerWebExchange exchange = exchange();
        AtomicReference<String> vistoAguasAbajo = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, chainThatCaptures(vistoAguasAbajo)))
                .verifyComplete();

        assertThat(vistoAguasAbajo.get())
                .isNotNull()
                .isEqualTo(exchange.getAttributes().get(REQUEST_ID));
    }

    @Test
    void generaUnRequestIdDistintoPorPeticion() {
        MockServerWebExchange primera = exchange();
        MockServerWebExchange segunda = exchange();

        StepVerifier.create(filter.filter(primera, chainThatCaptures(new AtomicReference<>())))
                .verifyComplete();
        StepVerifier.create(filter.filter(segunda, chainThatCaptures(new AtomicReference<>())))
                .verifyComplete();

        assertThat(primera.getAttributes().get(REQUEST_ID))
                .isNotEqualTo(segunda.getAttributes().get(REQUEST_ID));
    }

    private static MockServerWebExchange exchange() {
        return MockServerWebExchange.from(MockServerHttpRequest.get("/api/status").build());
    }

    private static WebFilterChain chainThatCaptures(AtomicReference<String> destino) {
        return exchange -> Mono.deferContextual(ctx -> {
            destino.set(ctx.getOrDefault(REQUEST_ID, null));
            return Mono.empty();
        });
    }
}
