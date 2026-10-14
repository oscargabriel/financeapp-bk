package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.ReportedTransaction;
import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.model.TransactionReport;

/**
 * El reporte de movimientos. occurredAt sale en UTC, como en el resto del API. scheduled marca los
 * programados (FA-106): salen en la lista y no en los totales. recurrenceId es la serie de la que el
 * movimiento es ocurrencia (FA-107), o null, e installment la cuota de una compra (FA-108), o null.
 * destinationAmount es lo que entro al destino de una transferencia entre monedas, o null (FIN-63). sharedBy,
 * sharedFrom y shares son lo compartido, como en la respuesta de un movimiento (FIN-66).
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
            BigDecimal destinationAmount,
            String description,
            String notes,
            String occurredAt,
            boolean scheduled,
            String recurrenceId,
            InstallmentResponse installment,
            SharingResponse.Person sharedBy,
            SharingResponse.SharedFrom sharedFrom,
            List<SharingResponse.Share> shares) {

        static Item from(ReportedTransaction t, boolean scheduled, Sharing compartido) {
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
                    t.destinationAmount(),
                    t.description(),
                    t.notes(),
                    t.occurredAt().toString(),
                    scheduled,
                    texto(t.recurrenceId()),
                    InstallmentResponse.from(t.installment()),
                    SharingResponse.Person.from(compartido.sharedBy()),
                    SharingResponse.SharedFrom.from(compartido.sharedFrom()),
                    SharingResponse.Share.from(compartido.shares()));
        }
    }

    public record TypeTotal(String type, BigDecimal total, long count) {
    }

    public record CategoryTotal(String categoryId, String categoryName, BigDecimal total, long count) {
    }

    /** compartido trae lo compartido de cada movimiento por su id; los que no estan no tienen nada (FIN-66). */
    public static TransactionReportResponse from(TransactionReport reporte, Map<UUID, Sharing> compartido) {
        return new TransactionReportResponse(
                reporte.filter().from().toString(),
                reporte.filter().to().toString(),
                reporte.currencyCode(),
                reporte.transactions().stream()
                        .map(t -> Item.from(t, reporte.scheduled(t), compartido.getOrDefault(t.id(), Sharing.NONE)))
                        .toList(),
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
