package com.oscargabriel.financeapp.infrastructure.adapter.out.exchangerate;

import java.math.BigDecimal;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.port.out.ExchangeRateProviderPort;

import reactor.core.publisher.Mono;

/**
 * Las tasas fijas con las que bruno/exchange-rates/ no depende del dia (design.md de FA-120, decision 6).
 * Con el perfil prod no existe aunque se pida, y entonces no hay proveedor y no arranca.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "tasas.proveedor", havingValue = "stub")
public class StubExchangeRateAdapter implements ExchangeRateProviderPort {

    private static final Map<String, BigDecimal> TASAS = Map.of(
            "COP", new BigDecimal("4000"),
            "EUR", new BigDecimal("0.8"),
            "MXN", new BigDecimal("18"),
            "VES", new BigDecimal("200"),
            "ARS", new BigDecimal("1000"),
            "CLP", new BigDecimal("900"),
            "PEN", new BigDecimal("3.5"),
            "BRL", new BigDecimal("5"));

    @Override
    public Mono<Map<String, BigDecimal>> latestFromUsd() {
        return Mono.just(TASAS);
    }
}
