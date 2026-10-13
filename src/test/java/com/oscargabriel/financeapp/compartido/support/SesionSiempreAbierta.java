package com.oscargabriel.financeapp.compartido.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import com.oscargabriel.financeapp.compartido.domain.port.out.SesionDelTokenPort;

import reactor.core.publisher.Mono;

/**
 * Para los tests web: el decoder necesita el puerto de la sesion para construirse, y un IT con
 * TokenMother lo consultaria contra una base que la suite no tiene. Primary porque en un IT convive
 * con el adapter R2DBC real. Que la sesion cierre la prueban JwtConfigTest y JwtSecurityIT.
 */
@TestConfiguration
public class SesionSiempreAbierta {

    @Bean
    @Primary
    SesionDelTokenPort sesionDelTokenPort() {
        return (usuario, version) -> Mono.just(true);
    }
}
