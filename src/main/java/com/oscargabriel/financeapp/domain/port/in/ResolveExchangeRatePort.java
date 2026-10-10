package com.oscargabriel.financeapp.domain.port.in;

import java.time.LocalDate;
import java.util.Collection;

import com.oscargabriel.financeapp.domain.model.ExchangeRate;

import reactor.core.publisher.Mono;

public interface ResolveExchangeRatePort {

    /**
     * La tasa de from a to en date, contra USD y con la fila mas reciente <= date, o la mas antigua si no hay
     * anterior (FA-122). Nunca completa vacio: una moneda que no esta activa en el catalogo es 400 en su campo,
     * y un par sin ninguna tasa, 404 en date.
     */
    Mono<ExchangeRate> resolve(String from, String to, LocalDate date);

    /**
     * Pide al proveedor las tasas de hoy, una vez y solo si a alguna de estas monedas le falta la fila de hoy
     * (FA-122). Un fallo del proveedor no sale: se sigue con lo guardado.
     */
    Mono<Void> refreshToday(Collection<String> currencies);
}
