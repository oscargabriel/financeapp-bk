package com.oscargabriel.financeapp.domain.port.out;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

import com.oscargabriel.financeapp.domain.model.UsdRate;

import reactor.core.publisher.Mono;

/** Las tasas USD→X de exchange_rates (FA-120). */
public interface ExchangeRateRepositoryPort {

    /**
     * La fila USD→currency con la rate_date mas reciente <= date, sin limite de antiguedad, o la mas antigua si
     * no hay ninguna anterior (FA-122). Vacio solo si el par no tiene ninguna fila.
     */
    Mono<UsdRate> findLatestFromUsd(String currency, LocalDate date);

    /** Una fila USD→X con source API por moneda, en date. La que ya exista para el par y la fecha no se toca. */
    Mono<Void> saveFromUsd(LocalDate date, Map<String, BigDecimal> rates);
}
