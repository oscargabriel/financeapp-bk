package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;
import com.oscargabriel.financeapp.core.domain.model.TransactionStatus;

import reactor.core.publisher.Flux;

public interface CreateTransactionsPort {

    /**
     * Todo o nada: o entran todos los movimientos, en el orden del lote, o no entra ninguno. Entran con el estado
     * que pide quien registra, salvo lo convertido de otra moneda, que queda pendiente igual. Los pendientes del
     * lote comparten un grupo nuevo (FIN-58).
     */
    Flux<Transaction> create(UUID userId, TransactionOrigin origen, TransactionStatus estado,
            List<CreateTransactionCommand> lote);
}
