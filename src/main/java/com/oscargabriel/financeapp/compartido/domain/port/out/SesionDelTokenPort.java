package com.oscargabriel.financeapp.compartido.domain.port.out;

import java.util.UUID;

import reactor.core.publisher.Mono;

/**
 * Si la sesion con la que se emitio un access sigue abierta. Vive aqui porque lo consulta el
 * decoder de la cadena Bearer, que es de todos los modulos; lo implementa el dueno de la tabla de
 * usuarios, que puede importar compartido sin que compartido lo importe a el.
 */
public interface SesionDelTokenPort {

    /**
     * Verdadero si el usuario existe, esta activo, no esta borrado y su version de sesion sigue
     * siendo {@code version}: la sube cada cambio de contrasena.
     */
    Mono<Boolean> abierta(UUID userId, long version);
}
