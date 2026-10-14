package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;

/** Una parte de un elemento del lote tal como llega, antes de resolver la afiliacion (FIN-66). */
public record ShareCommand(String affiliationId, BigDecimal amount) {
}
