package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto.validation;

import com.oscargabriel.financeapp.core.domain.model.TransactionType;

/** El tipo de un elemento del lote, si es valido: sin el, las reglas que dependen del tipo no opinan. */
final class Tipos {

    private Tipos() {
    }

    static TransactionType de(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            return TransactionType.valueOf(valor.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
