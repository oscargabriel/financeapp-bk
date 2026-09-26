package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.domain.model.Transaction;
import com.oscargabriel.financeapp.domain.port.out.TransactionRepositoryPort;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
public class TransactionR2dbcAdapter implements TransactionRepositoryPort {

    /**
     * Solo COP: exchange_rate queda en su DEFAULT 1 y amount_base es el mismo amount. current_balance
     * lo mueve trg_transactions_sync_balance fila a fila, dentro de la misma transaccion.
     */
    private static final String INSERTAR = """
            INSERT INTO finance.transactions
                   (id, user_id, account_id, destination_account_id, category_id, type,
                    amount, currency_code, amount_base, description, notes, occurred_at)
            VALUES (:id, :userId, :accountId, :destinationAccountId, :categoryId, :type,
                    :amount, :currencyCode, :amount, :description, :notes, :occurredAt)
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transaccion;

    public TransactionR2dbcAdapter(DatabaseClient databaseClient, ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    /**
     * concatMap y no flatMap: los INSERT salen en el orden del lote, y el lote vuelve en ese orden.
     * La lista se junta dentro de la transaccion y se emite despues del commit: aguas abajo nadie
     * ve un movimiento que un INSERT posterior pueda deshacer.
     */
    @Override
    public Flux<Transaction> saveAll(List<Transaction> transacciones) {
        return Flux.fromIterable(transacciones)
                .concatMap(t -> insertar(t).thenReturn(t))
                .collectList()
                .as(transaccion::transactional)
                .flatMapIterable(guardadas -> guardadas);
    }

    private Mono<Long> insertar(Transaction t) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(INSERTAR)
                .bind("id", t.id())
                .bind("userId", t.userId())
                .bind("accountId", t.accountId())
                .bind("type", t.type().name())
                .bind("amount", t.amount())
                .bind("currencyCode", t.currencyCode())
                .bind("description", t.description())
                .bind("occurredAt", OffsetDateTime.ofInstant(t.occurredAt(), ZoneOffset.UTC));

        sentencia = t.destinationAccountId() == null
                ? sentencia.bindNull("destinationAccountId", UUID.class)
                : sentencia.bind("destinationAccountId", t.destinationAccountId());
        sentencia = t.categoryId() == null
                ? sentencia.bindNull("categoryId", UUID.class)
                : sentencia.bind("categoryId", t.categoryId());
        sentencia = t.notes() == null
                ? sentencia.bindNull("notes", String.class)
                : sentencia.bind("notes", t.notes());

        return sentencia.fetch().rowsUpdated();
    }
}
