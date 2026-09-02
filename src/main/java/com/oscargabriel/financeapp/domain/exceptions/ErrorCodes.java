package com.oscargabriel.financeapp.domain.exceptions;

import lombok.Getter;

/**
 * Categorias de error del API. Nombrar en SCREAMING_SNAKE_CASE describiendo la categoria, no el
 * mensaje ni el detalle tecnico. Los codigos de dominio se agregan cuando exista el dominio que
 * los necesite.
 */
@Getter
public enum ErrorCodes {

    VALIDATION_ERROR("VALIDATION_ERROR"),
    INVALID_ARGUMENT("INVALID_ARGUMENT"),
    INVALID_NUMBER_FORMAT("INVALID_NUMBER_FORMAT"),
    JSON_PARSING_ERROR("JSON_PARSING_ERROR"),
    NOT_FOUND("NOT_FOUND"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR");

    private final String code;

    ErrorCodes(String code) {
        this.code = code;
    }
}
