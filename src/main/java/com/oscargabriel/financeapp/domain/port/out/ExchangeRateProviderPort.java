package com.oscargabriel.financeapp.domain.port.out;

import java.math.BigDecimal;
import java.util.Map;

import reactor.core.publisher.Mono;

/** El proveedor externo de tasas (FA-120). */
public interface ExchangeRateProviderPort {

    /** Las tasas USD→X vigentes, por codigo de moneda. Un fallo o una respuesta invalida salen como error. */
    Mono<Map<String, BigDecimal>> latestFromUsd();
}
