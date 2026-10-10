package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;

import io.r2dbc.spi.R2dbcException;

/**
 * trg_transactions_usd_equivalent rechaza con FX001 un movimiento cuya moneda no tiene ninguna tasa (FA-122):
 * solo pasa con la base sin tasas y el proveedor caido, asi que sale como el servicio externo que no respondio.
 * El mensaje de la base nombra la moneda y va al log por la causa, no al cliente.
 */
final class SinTasaDeCambio {

    static final String SQLSTATE = "FX001";

    private SinTasaDeCambio() {
    }

    static Throwable traducir(Throwable error) {
        for (Throwable causa = error; causa != null; causa = causa.getCause()) {
            if (causa instanceof R2dbcException base && SQLSTATE.equals(base.getSqlState())) {
                return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                        "No hay tasa de cambio para registrar el movimiento; intenta de nuevo", "server", error);
            }
        }
        return error;
    }
}
