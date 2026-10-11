package com.oscargabriel.financeapp.infrastructure.adapter.out.exchangerate;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Profile;

import reactor.test.StepVerifier;

class StubExchangeRateAdapterTest {

    @Test
    void respondeLasTasasFijasDeLaVerificacionConBruno() {
        StepVerifier.create(new StubExchangeRateAdapter().latestFromUsd())
                .assertNext(tasas -> {
                    assertThat(tasas).containsOnlyKeys("COP", "EUR", "MXN", "VES", "ARS", "CLP", "PEN", "BRL");
                    assertThat(tasas.get("COP")).isEqualByComparingTo("4000");
                    assertThat(tasas.get("EUR")).isEqualByComparingTo("0.8");
                })
                .verifyComplete();
    }

    @Test
    void noExisteConElPerfilProd() {
        assertThat(StubExchangeRateAdapter.class.getAnnotation(Profile.class).value()).containsExactly("!prod");
    }
}
