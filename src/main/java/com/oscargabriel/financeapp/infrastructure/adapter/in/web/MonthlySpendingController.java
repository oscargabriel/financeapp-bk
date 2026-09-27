package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.in.GetMonthlySpendingPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.MonthlySpendingResponse;

import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/monthly-spending")
public class MonthlySpendingController {

    private static final Pattern FORMATO_MES = Pattern.compile("[0-9]{4}-[0-9]{2}");

    private final GetMonthlySpendingPort getMonthlySpending;

    public MonthlySpendingController(GetMonthlySpendingPort getMonthlySpending) {
        this.getMonthlySpending = getMonthlySpending;
    }

    @GetMapping
    public Flux<MonthlySpendingResponse> monthlySpending(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        return Flux.defer(() -> getMonthlySpending.get(
                        UsuarioDelToken.de(jwt), parseMonth(from, "from"), parseMonth(to, "to")))
                .map(MonthlySpendingResponse::from);
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
