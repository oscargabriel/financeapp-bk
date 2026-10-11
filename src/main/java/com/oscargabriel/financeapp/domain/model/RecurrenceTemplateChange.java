package com.oscargabriel.financeapp.domain.model;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Lo que una edicion en grupo cambia en las ocurrencias de su alcance. Null es "no cambia": una
 * ocurrencia editada a mano conserva lo que el parche no toca.
 */
public record RecurrenceTemplateChange(UUID accountId, UUID categoryId, BigDecimal amount, String description) {
}
