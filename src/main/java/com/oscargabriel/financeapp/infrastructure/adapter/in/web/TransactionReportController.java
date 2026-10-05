package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.GetTransactionReportPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.TransactionReportResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/reports")
@AllArgsConstructor
public class TransactionReportController {

    private static final Pattern FORMATO_DIA = Pattern.compile("[0-9]{4}-[0-9]{2}-[0-9]{2}");

    private final GetTransactionReportPort getTransactionReport;

    /**
     * Los filtros llegan repetidos (?type=EXPENSE&type=INCOME) o separados por coma (?type=EXPENSE,INCOME):
     * Spring entrega cada aparicion del parametro tal cual, asi que la coma se separa aqui.
     */
    @GetMapping("/transactions")
    public Mono<TransactionReportResponse> transactions(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(name = "categoryId", required = false) List<String> categoryIds,
            @RequestParam(name = "accountId", required = false) List<String> accountIds,
            @RequestParam(name = "type", required = false) List<String> types) {
        return Mono.defer(() -> getTransactionReport.get(
                        UsuarioDelToken.de(jwt),
                        parseDay(from, "from"),
                        parseDay(to, "to"),
                        parseAll(categoryIds, "categoryId", "Cada categoryId debe ser un UUID", UUID::fromString),
                        parseAll(accountIds, "accountId", "Cada accountId debe ser un UUID", UUID::fromString),
                        parseAll(types, "type", "El tipo debe ser EXPENSE, INCOME o TRANSFER",
                                v -> TransactionType.valueOf(v.toUpperCase(Locale.ROOT)))))
                .map(TransactionReportResponse::from);
    }

    private static LocalDate parseDay(String valor, String campo) {
        if (valor == null || valor.isBlank()) {
            throw invalido("La fecha es obligatoria, en formato YYYY-MM-DD", campo, null);
        }
        try {
            if (!FORMATO_DIA.matcher(valor).matches()) {
                throw new DateTimeParseException("formato invalido", valor, 0);
            }
            return LocalDate.parse(valor);
        } catch (DateTimeParseException e) {
            throw invalido("La fecha debe ser un dia existente en formato YYYY-MM-DD", campo, e);
        }
    }

    private static <T> Set<T> parseAll(List<String> valores, String campo, String mensaje, Function<String, T> parser) {
        if (valores == null) {
            return Set.of();
        }
        return valores.stream()
                .flatMap(v -> Arrays.stream(v.split(",")))
                .map(String::trim)
                .filter(v -> !v.isEmpty())
                .map(v -> {
                    try {
                        return parser.apply(v);
                    } catch (IllegalArgumentException e) {
                        throw invalido(mensaje, campo, e);
                    }
                })
                .collect(Collectors.toUnmodifiableSet());
    }

    private static BadRequestException invalido(String mensaje, String campo, Exception causa) {
        return new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR, mensaje, campo, causa);
    }
}
