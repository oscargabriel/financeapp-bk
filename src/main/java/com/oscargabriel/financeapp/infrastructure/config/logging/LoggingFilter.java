package com.oscargabriel.financeapp.infrastructure.config.logging;

import java.util.UUID;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import reactor.core.publisher.Mono;

/**
 * Genera un requestId por peticion y lo deja en los atributos del exchange (acceso imperativo) y
 * en el Reactor Context (acceso reactivo, de donde lo toma ReactorMdcHook al cruzar hilos).
 *
 * HIGHEST_PRECEDENCE a proposito: la cadena de Spring Security corre en orden -100, y solo por
 * delante de ella el requestId aparece tambien en los logs de las respuestas 401/403.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class LoggingFilter implements WebFilter {

    private static final String REQUEST_ID = "requestId";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = UUID.randomUUID().toString();

        exchange.getAttributes().put(REQUEST_ID, requestId);
        MDC.put(REQUEST_ID, requestId);

        return chain.filter(exchange)
                .contextWrite(ctx -> ctx.put(REQUEST_ID, requestId))
                .doFinally(signal -> MDC.remove(REQUEST_ID));
    }
}
