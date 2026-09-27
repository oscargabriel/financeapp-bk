package com.oscargabriel.financeapp.domain.port.out;

import reactor.core.publisher.Mono;

public interface CurrencyQueryPort {

    /** TRUE si el codigo existe en el catalogo y esta activo. */
    Mono<Boolean> exists(String code);
}
