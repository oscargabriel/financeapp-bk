package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Una compra con tarjeta diferida a cuotas (FA-108). monthlyInterestRate es la de la tarjeta al
 * registrarla, 0 si no tenia: cambiarla despues no mueve las cuotas.
 */
public record InstallmentPurchase(
        UUID id,
        UUID userId,
        UUID accountId,
        UUID categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        LocalDate purchaseDate,
        int installmentCount,
        BigDecimal monthlyInterestRate) {
}