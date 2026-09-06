package com.oscargabriel.financeapp.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Reloj de la aplicacion, inyectable para que los casos de uso que dependen de "hoy" se puedan
 * probar con una fecha fija. La zona decide en que mes cae el rango por defecto de los reportes.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock(@Value("${app.timezone}") String timezone) {
        return Clock.system(ZoneId.of(timezone));
    }
}
