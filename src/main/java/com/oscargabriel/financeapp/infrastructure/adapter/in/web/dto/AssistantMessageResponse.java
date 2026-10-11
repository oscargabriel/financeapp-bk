package com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto;

import java.time.Instant;

import com.oscargabriel.financeapp.domain.model.AssistantReply;

/**
 * Los tres objetos son las mismas respuestas de /transactions y /reports, para que el cliente no
 * aprenda dos formas del mismo dato. Solo viene el que corresponde al intent; los otros, en null.
 */
public record AssistantMessageResponse(
        String intent,
        String message,
        TransactionResponse transaction,
        TransactionReportResponse report,
        BalanceResponse balance) {

    public static AssistantMessageResponse from(AssistantReply respuesta, Instant ahora) {
        return new AssistantMessageResponse(
                respuesta.intent().name(),
                respuesta.message(),
                respuesta.transaction() == null ? null : TransactionResponse.from(respuesta.transaction(), ahora),
                respuesta.report() == null ? null : TransactionReportResponse.from(respuesta.report()),
                respuesta.balance() == null ? null : BalanceResponse.from(respuesta.balance()));
    }
}
