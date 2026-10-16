package com.oscargabriel.financeapp.compartido.domain.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.compartido.domain.exceptions.responses.ErrorDetail;

class BadRequestExceptionTest {

    @Test
    void sinDetallesElMensajeEsGenerico() {
        var ex = new BadRequestException(HttpStatus.BAD_REQUEST, List.of());

        assertThat(ex.getMessage()).isEqualTo("Bad Request");
        assertThat(ex.getErrorResponse().getErrors()).isEmpty();
    }

    @Test
    void conDetallesElMensajeEsLaDescripcionDelPrimero() {
        var detalles = List.of(
                ErrorDetail.of("VALIDATION_ERROR", "El monto debe ser positivo", "[0].amount"),
                ErrorDetail.of("VALIDATION_ERROR", "La fecha es obligatoria", "[1].date"));

        var ex = new BadRequestException(HttpStatus.BAD_REQUEST, detalles);

        assertThat(ex.getMessage()).isEqualTo("El monto debe ser positivo");
        assertThat(ex.getErrorResponse().getErrors()).isEqualTo(detalles);
        assertThat(ex.getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    }
}
