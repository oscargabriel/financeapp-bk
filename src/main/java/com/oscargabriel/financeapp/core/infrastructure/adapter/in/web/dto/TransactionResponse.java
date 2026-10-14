package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.model.Transaction;

/**
 * occurredAt sale en UTC (Instant): el cliente lo muestra en la zona que quiera. scheduled se calcula
 * contra el instante de la respuesta: con fecha posterior, el movimiento esta programado (FA-106).
 * recurrenceId es la serie de la que es ocurrencia (FA-107), o null; installment, la cuota de una compra que
 * es (FA-108), o null. destinationAmount es lo que entra al destino de una transferencia entre monedas, y
 * originalAmount con originalCurrencyCode lo que llego antes de convertirse a la moneda de la cuenta (FA-51). groupId
 * es el grupo de pendientes con que entro, o null si entro confirmado (FIN-58). sharedBy, sharedFrom y shares son lo
 * compartido (FIN-66), cada uno null si no aplica; accountId es null en un pendiente compartido sin aprobar.
 */
public record TransactionResponse(
        String id,
        String type,
        String accountId,
        String destinationAccountId,
        String categoryId,
        BigDecimal amount,
        String currencyCode,
        String description,
        String notes,
        String occurredAt,
        String status,
        String origin,
        boolean scheduled,
        String recurrenceId,
        InstallmentResponse installment,
        BigDecimal destinationAmount,
        BigDecimal originalAmount,
        String originalCurrencyCode,
        String groupId,
        SharingResponse.Person sharedBy,
        SharingResponse.SharedFrom sharedFrom,
        List<SharingResponse.Share> shares) {

    public static TransactionResponse from(Transaction t, Instant ahora, Sharing compartido) {
        return new TransactionResponse(
                t.id().toString(),
                t.type().name(),
                t.accountId() == null ? null : t.accountId().toString(),
                t.destinationAccountId() == null ? null : t.destinationAccountId().toString(),
                t.categoryId() == null ? null : t.categoryId().toString(),
                t.amount(),
                t.currencyCode(),
                t.description(),
                t.notes(),
                t.occurredAt().toString(),
                t.status().name(),
                t.origin().name(),
                t.scheduledAt(ahora),
                t.recurrenceId() == null ? null : t.recurrenceId().toString(),
                InstallmentResponse.from(t.installment()),
                t.destinationAmount(),
                t.original() == null ? null : t.original().amount(),
                t.original() == null ? null : t.original().currencyCode(),
                t.groupId() == null ? null : t.groupId().toString(),
                SharingResponse.Person.from(compartido.sharedBy()),
                SharingResponse.SharedFrom.from(compartido.sharedFrom()),
                SharingResponse.Share.from(compartido.shares()));
    }
}
