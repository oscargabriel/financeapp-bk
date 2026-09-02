package com.oscargabriel.financeapp.infrastructure.config.logging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.server.WebFilter;

@Configuration
public class WebConfig {

    private static final Logger log = LoggerFactory.getLogger(WebConfig.class);

    /**
     * Request/response a nivel DEBUG: en produccion (root INFO) no genera ruido y se activa
     * bajando el nivel del paquete cuando hace falta depurar. Sin exclusiones todavia: /api/status
     * es el unico endpoint y su log es justamente el que interesa ver. Cuando exista un monitor
     * haciendo poll, excluirlo aqui.
     */
    @Bean
    public WebFilter requestLogger() {
        return (exchange, chain) -> {
            log.debug(">> Request {} {}",
                    exchange.getRequest().getMethod(), exchange.getRequest().getURI());
            return chain.filter(exchange)
                    .doFinally(signal -> log.debug("<< Response {} for {}",
                            exchange.getResponse().getStatusCode(),
                            exchange.getRequest().getURI()));
        };
    }
}
