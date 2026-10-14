package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;

/** Una parte entre el usuario y otra persona (FIN-67). direction es OWED_TO_ME si el usuario pago el gasto. */
public record ShareWithAffiliateResponse(
        String id,
        String description,
        String occurredAt,
        BigDecimal amount,
        String currencyCode,
        String status,
        String resolvedAt,
        String direction) {

    public static ShareWithAffiliateResponse from(ShareWithAffiliate parte) {
        return new ShareWithAffiliateResponse(
                parte.id().toString(),
                parte.description(),
                parte.occurredAt().toString(),
                parte.amount(),
                parte.currencyCode(),
                parte.status().name(),
                parte.resolvedAt() == null ? null : parte.resolvedAt().toString(),
                parte.direction().name());
    }
}
