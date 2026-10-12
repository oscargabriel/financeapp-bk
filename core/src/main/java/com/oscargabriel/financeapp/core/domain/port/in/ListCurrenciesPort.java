package com.oscargabriel.financeapp.core.domain.port.in;

import com.oscargabriel.financeapp.core.domain.model.Currency;

import reactor.core.publisher.Flux;

public interface ListCurrenciesPort {

    /** Las monedas que acepta el alta de cuentas, por codigo. */
    Flux<Currency> listActive();
}
