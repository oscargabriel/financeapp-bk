package com.oscargabriel.financeapp.domain.exceptions;

import java.io.Serial;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorResponse;

import lombok.Getter;

@Getter
public class BadRequestException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    @JsonProperty("httpStatus")
    private final HttpStatus httpStatus;

    @JsonProperty("errorResponse")
    private final ErrorResponse errorResponse;

    public BadRequestException(HttpStatus httpStatus, ErrorCodes errorCode,
            String description, String field) {
        super(description);
        this.httpStatus = httpStatus;
        this.errorResponse = new ErrorResponse(List.of(
                ErrorDetail.of(errorCode.getCode(), description, field)));
    }

    /** Preserva la causa raiz para el log cuando se envuelve una excepcion de bajo nivel. */
    public BadRequestException(HttpStatus httpStatus, ErrorCodes errorCode,
            String description, String field, Throwable cause) {
        super(description, cause);
        this.httpStatus = httpStatus;
        this.errorResponse = new ErrorResponse(List.of(
                ErrorDetail.of(errorCode.getCode(), description, field)));
    }

    /** Varios errores en una sola respuesta: validacion que reporta todos los campos a la vez. */
    public BadRequestException(HttpStatus httpStatus, List<ErrorDetail> errorDetails) {
        super(errorDetails.isEmpty() ? "Bad Request" : errorDetails.get(0).getDescription());
        this.httpStatus = httpStatus;
        this.errorResponse = new ErrorResponse(errorDetails);
    }
}
