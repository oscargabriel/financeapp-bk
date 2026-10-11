package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.Currency;
import com.oscargabriel.financeapp.domain.model.ExchangeRate;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;

import reactor.core.publisher.Mono;

/**
 * Lleva los borradores de un lote a la moneda de sus cuentas (FA-51) con la tasa de la fecha de cada uno en la
 * zona de la app. Cada par y fecha se pide una vez por lote: el lote se convierte en orden, con concatMap, asi que
 * el mapa no necesita sincronizarse.
 */
final class ConversorDeMontos {

    /** La escala de una moneda que dejo de estar activa: la de la mayoria del catalogo. */
    private static final int DECIMALES_POR_DEFECTO = 2;

    private final ResolveExchangeRatePort tasas;
    private final Map<String, Currency> monedas;
    private final ZoneId zona;
    private final Map<String, Mono<ExchangeRate>> pedidas = new HashMap<>();

    ConversorDeMontos(ResolveExchangeRatePort tasas, Map<String, Currency> monedas, ZoneId zona) {
        this.tasas = tasas;
        this.monedas = monedas;
        this.zona = zona;
    }

    Mono<Transaction> convertir(Borrador borrador) {
        Transaction t = borrador.movimiento();
        if (!borrador.porConvertir()) {
            return Mono.just(t);
        }
        LocalDate fecha = LocalDate.ofInstant(t.occurredAt(), zona);
        Mono<BigDecimal> monto = borrador.monedaRecibida() == null
                ? Mono.just(t.amount())
                : a(borrador.monedaRecibida(), t.currencyCode(), fecha, t.amount());
        return monto.flatMap(convertido -> borrador.monedaDestino() == null
                ? Mono.just(borrador.convertido(convertido, t.destinationAmount()))
                : a(t.currencyCode(), borrador.monedaDestino(), fecha, convertido)
                        .map(destino -> borrador.convertido(convertido, destino)));
    }

    private Mono<BigDecimal> a(String desde, String hacia, LocalDate fecha, BigDecimal monto) {
        int decimales = monedas.containsKey(hacia) ? monedas.get(hacia).decimalPlaces() : DECIMALES_POR_DEFECTO;
        return pedidas.computeIfAbsent(desde + ">" + hacia + "@" + fecha,
                        clave -> tasas.resolve(desde, hacia, fecha).onErrorMap(ConversorDeMontos::sinTasa).cache())
                .map(tasa -> tasa.convertir(monto, decimales));
    }

    /**
     * Un par sin ninguna tasa solo pasa con la base sin tasas de una de las monedas y el proveedor caido: sale
     * como el servicio externo que no respondio, igual que el FX001 del trigger (FA-122).
     */
    private static Throwable sinTasa(Throwable error) {
        return error instanceof BadRequestException e && e.getHttpStatus() == HttpStatus.NOT_FOUND
                ? new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                        "No hay tasa de cambio para convertir el movimiento; intenta de nuevo", "server", error)
                : error;
    }
}
