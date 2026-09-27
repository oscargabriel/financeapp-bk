package com.oscargabriel.financeapp.infrastructure.config;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.boot.r2dbc.autoconfigure.ConnectionFactoryOptionsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.r2dbc.spi.Option;

/**
 * Fija el search_path de cada conexion en el esquema finance, que no es public. Neon descarta el
 * parametro de arranque search_path que el driver manda con {@code ?schema=} en la URL, pero
 * respeta {@code options=-c search_path=...}, como PGOPTIONS en psql (FA-47). La URL no puede
 * llevarlo: el driver parte las opciones por '=' y el valor contiene uno.
 *
 * El driver reemplaza su mapa de opciones con este, asi que un {@code ?schema=} en la URL queda
 * sin efecto: este bean es el unico lugar que decide el esquema.
 */
@Configuration
public class R2dbcSearchPathConfig {

    private static final Option<Map<String, String>> OPTIONS = Option.valueOf("options");

    @Bean
    public ConnectionFactoryOptionsBuilderCustomizer searchPathFinance() {
        Map<String, String> opciones = new LinkedHashMap<>();
        opciones.put("options", "-c search_path=finance");
        return builder -> builder.option(OPTIONS, opciones);
    }
}
