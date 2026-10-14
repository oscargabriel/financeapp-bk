package com.oscargabriel.financeapp.core.infrastructure.adapter.in.web.dto;

import com.oscargabriel.financeapp.core.domain.model.ApprovalChoice;

/**
 * El cuerpo opcional de la aprobacion (FIN-66): la cuenta y la categoria de un pendiente que llego sin ellas. Si
 * sobran, faltan o no son del usuario lo decide el caso de uso, que conoce el pendiente.
 */
public record ApprovePendingRequest(String accountId, String categoryId) {

    public static ApprovalChoice toChoice(ApprovePendingRequest cuerpo) {
        return cuerpo == null ? ApprovalChoice.NONE : new ApprovalChoice(cuerpo.accountId(), cuerpo.categoryId());
    }
}
