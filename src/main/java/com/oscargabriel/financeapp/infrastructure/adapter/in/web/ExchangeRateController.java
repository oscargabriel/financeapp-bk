package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.ResolveExchangeRatePort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.ExchangeRateResponse;

import reactor.core.publisher.Mono;

/**
 * La tasa de un par en una fecha (FA-120). Que las monedas esten activas lo decide el caso de uso,
 * que necesita la base; aqui solo que lleguen y el formato de la fecha.
 */
@RestController
@RequestMapping("/exchange-rates")
@AllArgsConstructor
public class ExchangeRateController {

    private static final Pattern FORMATO_DIA = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final ResolveExchangeRatePort resolveExchangeRate;

    @GetMapping
    public Mono<ExchangeRateResponse> rate(
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String date) {
        return Mono.defer(() -> resolveExchangeRate.resolve(
                        obligatorio(from, "from"), obligatorio(to, "to"), parseDay(date)))
                .map(ExchangeRateResponse::from);
    }

    private static String obligatorio(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw invalido(campo + " es obligatorio", campo, null);
        }
        return valor;
    }

    /** El mismo parseo que TransactionReportController: si cambia uno, cambia el otro. */
    private static LocalDate parseDay(String valor) {
        obligatorio(valor, "date");
        try {
            if (!FORMATO_DIA.matcher(valor).matches()) {
                throw new DateTimeParseException("formato invalido", valor, 0);
            }
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw invalido("La fecha debe ser un dia existente en formato YYYY-MM-DD", "date", e);
        }
    }

    private static BadRequestException invalido(String mensaje, String campo, Exception causa) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, mensaje, campo, causa);
    }
}
