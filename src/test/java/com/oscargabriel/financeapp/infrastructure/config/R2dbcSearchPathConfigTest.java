package com.oscargabriel.financeapp.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;

import io.r2dbc.spi.ConnectionFactoryOptions;
import io.r2dbc.spi.Option;

/**
 * Que Neon aplique de verdad el search_path lo verifica el arranque contra Neon documentado en
 * FA-47 (log DEBUG de io.r2dbc.postgresql.client); aqui solo la opcion que llega al driver.
 */
class R2dbcSearchPathConfigTest {

    private static final Option<Object> OPTIONS = Option.valueOf("options");

    @Test
    void mandaElSearchPathComoOpcionDeArranqueQueNeonRespeta() {
        ConnectionFactoryOptions.Builder builder = ConnectionFactoryOptions.builder();

        new R2dbcSearchPathConfig().searchPathFinance().customize(builder);

        assertThat(builder.build().getValue(OPTIONS))
                .isEqualTo(Map.of("options", "-c search_path=finance"));
    }
}
