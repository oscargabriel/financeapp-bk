package com.oscargabriel.financeapp.core.domain.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * Un elemento del lote tal como llega del cliente, antes de validarse. Los ids y la fecha viajan
 * como texto para que un valor mal formado salga como error del campo con su indice, y no como un
 * JSON_PARSING_ERROR sobre el cuerpo entero.
 *
 * currencyCode es la moneda en que llego amount, y destinationAmount lo que entra al destino de una transferencia
 * entre monedas distintas (FA-51). shares son las partes de un movimiento compartido, vacia si no se comparte
 * (FIN-66).
 */
public record CreateTransactionCommand(
        String type,
        String accountId,
        String destinationAccountId,
        String categoryId,
        BigDecimal amount,
        BigDecimal destinationAmount,
        String currencyCode,
        String description,
        String notes,
        String occurredAt,
        List<ShareCommand> shares) {

    public CreateTransactionCommand {
        shares = shares == null ? List.of() : shares;
    }
}
