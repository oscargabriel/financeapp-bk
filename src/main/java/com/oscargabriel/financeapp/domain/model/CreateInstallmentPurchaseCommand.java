package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * El alta o la simulacion de una compra en cuotas (FA-108), con el formato ya validado por el request.
 * Que la tarjeta y la categoria sean del usuario, y que la fecha no sea futura en su zona, lo decide el
 * caso de uso.
 */
public record CreateInstallmentPurchaseCommand(
        String accountId,
        String categoryId,
        BigDecimal amount,
        String description,
        LocalDate purchaseDate,
        int installmentCount) {
}