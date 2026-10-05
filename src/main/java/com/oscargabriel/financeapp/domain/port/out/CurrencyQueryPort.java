package com.oscargabriel.financeapp.domain.port.out;

import com.oscargabriel.financeapp.domain.model.Currency;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CurrencyQueryPort {

    /** TRUE si el codigo existe en el catalogo y esta activo. */
    Mono<Boolean> exists(String code);

    /** Las monedas activas del catalogo, por codigo. */
    Flux<Currency> findActive();
}
