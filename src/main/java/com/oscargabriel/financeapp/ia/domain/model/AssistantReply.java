package com.oscargabriel.financeapp.ia.domain.model;

/**
 * La respuesta del asistente. message sale siempre de textos fijos del back, nunca del modelo; de
 * group, report y balance solo viene el que corresponde al intent.
 */
public record AssistantReply(
        AssistantIntent intent,
        String message,
        CreatedGroup group,
        AssistantReport report,
        AssistantBalance balance) {

    public enum AssistantIntent {
        CREATE_TRANSACTION,
        LIST_TRANSACTIONS,
        GET_BALANCE,
        NEEDS_CLARIFICATION,
        UNSUPPORTED
    }

    public static AssistantReply creado(String message, CreatedGroup group) {
        return new AssistantReply(AssistantIntent.CREATE_TRANSACTION, message, group, null, null);
    }

    public static AssistantReply reporte(String message, AssistantReport report) {
        return new AssistantReply(AssistantIntent.LIST_TRANSACTIONS, message, null, report, null);
    }

    public static AssistantReply saldo(String message, AssistantBalance balance) {
        return new AssistantReply(AssistantIntent.GET_BALANCE, message, null, null, balance);
    }

    public static AssistantReply aclaracion(String message) {
        return new AssistantReply(AssistantIntent.NEEDS_CLARIFICATION, message, null, null, null);
    }

    public static AssistantReply noSoportado(String message) {
        return new AssistantReply(AssistantIntent.UNSUPPORTED, message, null, null, null);
    }
}
