package com.oscargabriel.financeapp.domain.port.in;

import java.time.LocalDate;

import com.oscargabriel.financeapp.domain.model.ExchangeRate;

import reactor.core.publisher.Mono;

public interface ResolveExchangeRatePort {

    /**
     * La tasa de from a to en date, contra USD y con la fila mas reciente <= date. Nunca completa vacio:
     * una moneda que no esta activa en el catalogo es 400 en su campo, y la falta de tasa, 404 en date.
     */
    Mono<ExchangeRate> resolve(String from, String to, LocalDate date);
}
