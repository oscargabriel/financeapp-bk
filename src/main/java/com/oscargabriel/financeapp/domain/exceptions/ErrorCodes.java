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
    INVALID_CREDENTIALS("INVALID_CREDENTIALS"),
    UNAUTHENTICATED("UNAUTHENTICATED"),
    REGISTRATION_NOT_ALLOWED("REGISTRATION_NOT_ALLOWED"),
    DUPLICATE_RESOURCE("DUPLICATE_RESOURCE"),
    RESOURCE_IN_USE("RESOURCE_IN_USE"),
    INVALID_STATE("INVALID_STATE"),
    PAYLOAD_TOO_LARGE("PAYLOAD_TOO_LARGE"),
    EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR"),
    INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR");

    private final String code;

    ErrorCodes(String code) {
        this.code = code;
    }
}
