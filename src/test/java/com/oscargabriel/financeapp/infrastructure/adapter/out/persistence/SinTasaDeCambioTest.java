package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.UncategorizedR2dbcException;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;

import io.r2dbc.spi.R2dbcNonTransientResourceException;

class SinTasaDeCambioTest {

    private static UncategorizedR2dbcException deLaBase(String sqlState) {
        return new UncategorizedR2dbcException("INSERT", "INSERT INTO finance.transactions ...",
                new R2dbcNonTransientResourceException("No hay tasa de cambio USD→EUR para el movimiento", sqlState));
    }

    @Test
    void elFx001DelTriggerEsUn502SinDetalleInterno() {
        Throwable traducido = SinTasaDeCambio.traducir(deLaBase("FX001"));

        assertThat(traducido).isInstanceOf(BadRequestException.class);
        BadRequestException bre = (BadRequestException) traducido;
        assertThat(bre.getHttpStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
        assertThat(bre.getErrorResponse().getErrors()).singleElement().satisfies(detalle -> {
            assertThat(detalle.getCode()).isEqualTo(ErrorCodes.EXTERNAL_SERVICE_ERROR.getCode());
            assertThat(detalle.getField()).isEqualTo("server");
            assertThat(detalle.getDescription()).doesNotContain("USD→EUR", "INSERT", "FX001");
        });
    }

    @Test
    void otroErrorDeLaBasePasaIgual() {
        UncategorizedR2dbcException otro = deLaBase("23514");

        assertThat(SinTasaDeCambio.traducir(otro)).isSameAs(otro);
    }
}
