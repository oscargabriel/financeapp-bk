// Requerido en Spring Boot 4: WebProperties.Resources no se registra como bean automaticamente y
// AbstractErrorWebExceptionHandler lo recibe por constructor. Sin este @Bean el contexto no arranca.
package com.oscargabriel.financeapp.infrastructure.config;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ResourcesWebPropertiesConfig {

    @Bean
    public WebProperties.Resources resources() {
        return new WebProperties.Resources();
    }
}
