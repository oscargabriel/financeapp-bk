package com.oscargabriel.financeapp.domain.port.in;

import com.oscargabriel.financeapp.domain.model.Currency;

import reactor.core.publisher.Flux;

public interface ListCurrenciesPort {

    /** Las monedas que acepta el alta de cuentas, por codigo. */
    Flux<Currency> listActive();
}
