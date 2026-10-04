package com.oscargabriel.financeapp.application.usecase;

import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.model.Currency;
import com.oscargabriel.financeapp.domain.port.in.ListCurrenciesPort;
import com.oscargabriel.financeapp.domain.port.out.CurrencyQueryPort;

import reactor.core.publisher.Flux;

@Service
@AllArgsConstructor
public class ListCurrenciesUseCase implements ListCurrenciesPort {

    private final CurrencyQueryPort query;

    @Override
    public Flux<Currency> listActive() {
        return Flux.defer(query::findActive);
    }
}
