package com.oscargabriel.financeapp.core.application.usecase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

import com.oscargabriel.financeapp.core.domain.model.ExpenseShare;
import com.oscargabriel.financeapp.core.domain.model.ShareStatus;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;
import com.oscargabriel.financeapp.core.domain.model.TransactionType;

/**
 * Lo que un alta deja a otras personas (FIN-66): la parte de cada una en un gasto, y el pendiente que recibe, sin
 * cuenta ni categoria y con un grupo propio. En una transferencia compartida no hay parte: cada persona recibe un
 * ingreso. Todo en la moneda en que llego el monto, con la descripcion y la fecha del movimiento.
 */
record PartesDelAlta(List<ExpenseShare> partes, List<Transaction> deOtros) {

    /** borradores y movimientos van en el mismo orden: el movimiento i es el borrador i ya convertido. */
    static PartesDelAlta de(List<Borrador> borradores, List<Transaction> movimientos, Supplier<UUID> ids) {
        List<ExpenseShare> partes = new ArrayList<>();
        List<Transaction> deOtros = new ArrayList<>();
        for (int i = 0; i < borradores.size(); i++) {
            Borrador borrador = borradores.get(i);
            Transaction movimiento = movimientos.get(i);
            String moneda = borrador.monedaDeLasPartes();
            for (Borrador.Destinatario destinatario : borrador.partes()) {
                ExpenseShare parte = borrador.sinDeuda() ? null : new ExpenseShare(ids.get(), movimiento.id(),
                        movimiento.userId(), destinatario.persona(), destinatario.monto(), moneda,
                        movimiento.description(), movimiento.occurredAt(), ShareStatus.PENDING);
                if (parte != null) {
                    partes.add(parte);
                }
                deOtros.add(new Transaction(ids.get(), destinatario.persona(),
                        borrador.sinDeuda() ? TransactionType.INCOME : TransactionType.EXPENSE, null, null, null,
                        destinatario.monto(), moneda, movimiento.description(), null, movimiento.occurredAt(),
                        TransactionStatus.PENDING, movimiento.origin(), null, null, null, null, ids.get(),
                        movimiento.userId(), parte == null ? null : parte.id()));
            }
        }
        return new PartesDelAlta(List.copyOf(partes), List.copyOf(deOtros));
    }
}
