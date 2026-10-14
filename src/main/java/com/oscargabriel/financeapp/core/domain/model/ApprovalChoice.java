package com.oscargabriel.financeapp.core.domain.model;

/**
 * La cuenta y la categoria con que se aprueba un pendiente que llego sin ellas (FIN-66), tal como llegan: los ids
 * viajan como texto para que uno mal formado salga como error de su campo.
 */
public record ApprovalChoice(String accountId, String categoryId) {

    public static final ApprovalChoice NONE = new ApprovalChoice(null, null);
}
