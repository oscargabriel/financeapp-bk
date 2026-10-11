package com.oscargabriel.financeapp.application.usecase;

import java.math.BigDecimal;

import com.oscargabriel.financeapp.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.model.TransactionStatus;

/**
 * Un elemento del lote ya validado al que le puede faltar la conversion (FA-51). Con monedaRecibida, el amount
 * del movimiento llego en esa moneda y hay que llevarlo a su currencyCode; con monedaDestino, lo que entra al
 * destino de la transferencia se calcula desde el amount ya convertido.
 */
record Borrador(Transaction movimiento, String monedaRecibida, String monedaDestino) {

    boolean porConvertir() {
        return monedaRecibida != null || monedaDestino != null;
    }

    /** Lo convertido queda pendiente: la tasa interna solo aproxima el cargo real. */
    Transaction convertido(BigDecimal monto, BigDecimal destino) {
        Transaction t = movimiento;
        OriginalAmount original = monedaRecibida == null ? null : new OriginalAmount(t.amount(), monedaRecibida);
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(), t.categoryId(),
                monto, t.currencyCode(), t.description(), t.notes(), t.occurredAt(), TransactionStatus.PENDING,
                t.origin(), t.recurrenceId(), t.installment(), destino, original);
    }
}
