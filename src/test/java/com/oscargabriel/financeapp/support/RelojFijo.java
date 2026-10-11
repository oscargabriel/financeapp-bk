package com.oscargabriel.financeapp.support;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * El Clock de los slices web, que no cargan ClockConfig. AHORA va despues de las fechas de los Object
 * Mother: lo que tenga fecha posterior sale programado (FA-106).
 */
@TestConfiguration
public class RelojFijo {

    public static final Instant AHORA = Instant.parse("2026-10-01T00:00:00Z");

    /** Despues de AHORA: un movimiento con esta fecha esta programado. */
    public static final Instant DESPUES = Instant.parse("2026-11-08T17:00:00Z");

    @Bean
    Clock clock() {
        return Clock.fixed(AHORA, ZoneOffset.UTC);
    }
}
