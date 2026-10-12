package com.oscargabriel.financeapp.core.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.core.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.core.domain.model.Transaction;
import com.oscargabriel.financeapp.core.domain.model.TransactionOrigin;

import reactor.core.publisher.Flux;

public interface CreateTransactionsPort {

    /**
     * Todo o nada: o entran todos los movimientos, en el orden del lote, o no entra ninguno. El origen
     * decide el estado con que entran (TransactionOrigin#estadoInicial).
     */
    Flux<Transaction> create(UUID userId, TransactionOrigin origen, List<CreateTransactionCommand> lote);
}
