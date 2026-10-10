package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * La cuota que es un movimiento (FA-108): de que compra, que numero de cuantas, y su capital. El capital
 * no sale en las respuestas: es lo que la cuota compromete del cupo mientras no llega.
 */
public record InstallmentRef(UUID purchaseId, int number, int count, BigDecimal principal) {
}
