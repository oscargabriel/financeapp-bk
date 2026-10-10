package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.domain.model.TransactionReport;

/**
 * El reporte de movimientos. occurredAt sale en UTC, como en el resto del API. scheduled marca los
 * programados (FA-106): salen en la lista y no en los totales. recurrenceId es la serie de la que el
 * movimiento es ocurrencia (FA-107), o null, e installment la cuota de una compra (FA-108), o null.
 */
public record TransactionReportResponse(
        String from,
        String to,
        String currencyCode,
        List<Item> transactions,
        List<TypeTotal> totalsByType,
        List<CategoryTotal> totalsByCategory,
        BigDecimal net) {

    public record Item(
            String id,
            String type,
            String accountId,
            String destinationAccountId,
            String categoryId,
            String categoryName,
            BigDecimal amount,
            String currencyCode,
            BigDecimal amountBase,
            String description,
            String notes,
            String occurredAt,
            boolean scheduled,
            String recurrenceId,
            InstallmentResponse installment) {

        static Item from(ReportedTransaction t, boolean scheduled) {
            return new Item(
                    t.id().toString(),
                    t.type().name(),
                    t.accountId().toString(),
                    texto(t.destinationAccountId()),
                    texto(t.categoryId()),
                    t.categoryName(),
                    t.amount(),
                    t.currencyCode(),
                    t.amountBase(),
                    t.description(),
                    t.notes(),
                    t.occurredAt().toString(),
                    scheduled,
                    texto(t.recurrenceId()),
                    InstallmentResponse.from(t.installment()));
        }
    }

    public record TypeTotal(String type, BigDecimal total, long count) {
    }

    public record CategoryTotal(String categoryId, String categoryName, BigDecimal total, long count) {
    }

    public static TransactionReportResponse from(TransactionReport reporte) {
        return new TransactionReportResponse(
                reporte.filter().from().toString(),
                reporte.filter().to().toString(),
                reporte.currencyCode(),
                reporte.transactions().stream().map(t -> Item.from(t, reporte.scheduled(t))).toList(),
                reporte.totalsByType().stream()
                        .map(t -> new TypeTotal(t.type().name(), t.total(), t.count()))
                        .toList(),
                reporte.totalsByCategory().stream()
                        .map(c -> new CategoryTotal(c.categoryId().toString(), c.categoryName(), c.total(), c.count()))
                        .toList(),
                reporte.net());
    }

    private static String texto(UUID id) {
        return id == null ? null : id.toString();
    }
}
