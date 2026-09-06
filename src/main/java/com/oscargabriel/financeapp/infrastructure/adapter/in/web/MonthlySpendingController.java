package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.GetMonthlySpendingPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.MonthlySpendingResponse;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/users/{userId}/monthly-spending")
public class MonthlySpendingController {

    private static final Pattern FORMATO_MES = Pattern.compile("[0-9]{4}-[0-9]{2}");

    private final GetMonthlySpendingPort getMonthlySpending;

    public MonthlySpendingController(GetMonthlySpendingPort getMonthlySpending) {
        this.getMonthlySpending = getMonthlySpending;
    }

    @GetMapping
    public Flux<MonthlySpendingResponse> monthlySpending(
            @PathVariable String userId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Flux.defer(() -> getMonthlySpending.get(
                        parseUserId(userId), parseMonth(from, "from"), parseMonth(to, "to")))
                .map(MonthlySpendingResponse::from);
    }

    /**
     * El UUID se parsea aqui y no via @PathVariable UUID: la conversion fallida de Spring termina
     * en ServerWebInputException, que el handler global reporta como JSON_PARSING_ERROR sobre el
     * body — engañoso para un parametro de ruta.
     */
    private static UUID parseUserId(String userId) {
        try {
            return UUID.fromString(userId);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.INVALID_ARGUMENT,
                    "El identificador de usuario no es un UUID valido", "userId", e);
        }
    }

    private static YearMonth parseMonth(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            return null;
        }
        try {
            if (!FORMATO_MES.matcher(valor).matches()) {
                throw new DateTimeParseException("formato invalido", valor, 0);
            }
            return YearMonth.parse(valor);
        } catch (DateTimeParseException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    "El mes debe venir en formato YYYY-MM", campo, e);
        }
    }
}
