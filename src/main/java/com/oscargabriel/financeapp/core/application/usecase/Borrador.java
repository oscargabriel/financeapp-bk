package com.oscargabriel.financeapp.core.application.usecase;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.OriginalAmount;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;

/**
 * Un elemento del lote ya validado al que le puede faltar la conversion (FA-51). Con monedaRecibida, el amount
 * del movimiento llego en esa moneda y hay que llevarlo a su currencyCode; con monedaDestino, lo que entra al
 * destino de la transferencia se calcula desde el amount ya convertido.
 *
 * partes son las personas con quienes se comparte, ya resueltas (FIN-66). sinDeuda dice que vienen de una
 * transferencia compartida: cada persona recibe un ingreso y no queda debiendo nada.
 */
record Borrador(Transaction movimiento, String monedaRecibida, String monedaDestino, List<Destinatario> partes,
        boolean sinDeuda) {

    /** Una persona con quien se comparte y su monto, en la moneda de las partes. */
    record Destinatario(UUID persona, BigDecimal monto) {
    }

    boolean porConvertir() {
        return monedaRecibida != null || monedaDestino != null;
    }

    /** La moneda en que llego el monto: las partes no se convierten, cada quien lo hace al aprobar. */
    String monedaDeLasPartes() {
        return monedaRecibida == null ? movimiento.currencyCode() : monedaRecibida;
    }

    /** Lo convertido queda pendiente: la tasa interna solo aproxima el cargo real. */
    Transaction convertido(BigDecimal monto, BigDecimal destino) {
        Transaction t = movimiento;
        OriginalAmount original = monedaRecibida == null ? null : new OriginalAmount(t.amount(), monedaRecibida);
        return new Transaction(t.id(), t.userId(), t.type(), t.accountId(), t.destinationAccountId(), t.categoryId(),
                monto, t.currencyCode(), t.description(), t.notes(), t.occurredAt(), TransactionStatus.PENDING,
                t.origin(), t.recurrenceId(), t.installment(), destino, original, t.groupId(), t.sharedByUserId(), t.shareId());
    }
}
