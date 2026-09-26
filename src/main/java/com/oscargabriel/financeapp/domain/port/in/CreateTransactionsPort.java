package com.oscargabriel.financeapp.domain.port.in;

import java.util.List;
import java.util.UUID;

import com.oscargabriel.financeapp.domain.model.CreateTransactionCommand;
import com.oscargabriel.financeapp.domain.model.Transaction;

import reactor.core.publisher.Flux;

public interface CreateTransactionsPort {

    /** Todo o nada: o entran todos los movimientos, en el orden del lote, o no entra ninguno. */
    Flux<Transaction> create(UUID userId, List<CreateTransactionCommand> lote);
}
