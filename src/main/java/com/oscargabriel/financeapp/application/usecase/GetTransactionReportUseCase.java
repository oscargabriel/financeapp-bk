package com.oscargabriel.financeapp.application.usecase;

import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.TransactionReport;
import com.oscargabriel.financeapp.domain.model.TransactionReportFilter;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.in.GetTransactionReportPort;
import com.oscargabriel.financeapp.domain.port.out.TransactionReportQueryPort;

import reactor.core.publisher.Mono;

@Service
@AllArgsConstructor
public class GetTransactionReportUseCase implements GetTransactionReportPort {

    private final TransactionReportQueryPort query;

    /** El defer hace que un rango invertido salga como senal de error, no al ensamblar la cadena. */
    @Override
    public Mono<TransactionReport> get(UUID userId, LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<TransactionType> types) {
        return Mono.defer(() -> {
            TransactionReportFilter filtro = filtro(from, to, categoryIds, types);
            return Mono.zip(query.findBaseCurrency(userId), query.findByUser(userId, filtro).collectList())
                    .map(t -> TransactionReport.of(t.getT1(), filtro, t.getT2()));
        });
    }

    private static TransactionReportFilter filtro(LocalDate from, LocalDate to, Set<UUID> categoryIds,
            Set<TransactionType> types) {
        try {
            return new TransactionReportFilter(from, to, categoryIds, types);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(HttpStatus.BAD_REQUEST, ErrorCodes.VALIDATION_ERROR,
                    e.getMessage(), "from", e);
        }
    }
}
