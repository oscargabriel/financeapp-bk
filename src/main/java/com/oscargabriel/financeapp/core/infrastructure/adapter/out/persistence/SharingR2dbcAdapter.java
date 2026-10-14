package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.ShareStatus;
import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.port.out.SharingQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class SharingR2dbcAdapter implements SharingQueryPort {

    /**
     * Quien compartio el movimiento y el gasto del que sale su parte (FIN-66). El total va en la moneda de la parte:
     * el original del gasto si se convirtio desde ella. La descripcion es la copia de la parte, la que se compartio.
     */
    private static final String RECIBIDOS = """
            SELECT t.id, u.email, u.first_name, u.last_name,
                   s.description AS share_description, s.currency_code AS share_currency,
                   CASE WHEN src.original_currency_code = s.currency_code THEN src.original_amount
                        ELSE src.amount END AS source_amount,
                   src.id AS source_id
              FROM finance.transactions t
              JOIN finance.users u ON u.id = t.shared_by_user_id
              LEFT JOIN finance.expense_shares s ON s.id = t.share_id
              LEFT JOIN finance.transactions src ON src.id = s.source_transaction_id
             WHERE t.user_id = :userId
               AND t.id = ANY(:ids)
            """;

    /** Las partes de los gastos de quien pago, con la persona que debe cada una, en el orden en que se crearon. */
    private static final String PARTES = """
            SELECT s.source_transaction_id, s.id, s.amount, s.currency_code, s.status,
                   u.email, u.first_name, u.last_name
              FROM finance.expense_shares s
              JOIN finance.users u ON u.id = s.debtor_id
             WHERE s.payer_id = :userId
               AND s.source_transaction_id = ANY(:ids)
             ORDER BY s.source_transaction_id, s.id
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Mono<Map<UUID, Sharing>> findFor(UUID userId, List<UUID> transactionIds) {
        UUID[] ids = transactionIds.toArray(UUID[]::new);
        Mono<Map<UUID, Sharing>> recibidos = databaseClient.sql(RECIBIDOS)
                .bind("userId", userId)
                .bind("ids", ids)
                .map((row, metadata) -> Map.entry(row.get("id", UUID.class), recibido(row)))
                .all()
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
        Mono<Map<UUID, List<Sharing.Share>>> partes = databaseClient.sql(PARTES)
                .bind("userId", userId)
                .bind("ids", ids)
                .map((row, metadata) -> Map.entry(row.get("source_transaction_id", UUID.class), parte(row)))
                .all()
                .collectMultimap(Map.Entry::getKey, Map.Entry::getValue)
                .map(porGasto -> {
                    Map<UUID, List<Sharing.Share>> listas = new HashMap<>();
                    porGasto.forEach((gasto, suyas) -> listas.put(gasto, List.copyOf(suyas)));
                    return listas;
                });
        return Mono.zip(recibidos, partes).map(t -> {
            Map<UUID, Sharing> todo = new HashMap<>(t.getT1());
            t.getT2().forEach((gasto, suyas) -> todo.merge(gasto, new Sharing(null, null, suyas),
                    (recibido, conPartes) -> new Sharing(recibido.sharedBy(), recibido.sharedFrom(), suyas)));
            return todo;
        });
    }

    private static Sharing recibido(Row row) {
        Sharing.Source origen = row.get("source_id", UUID.class) == null ? null : new Sharing.Source(
                row.get("share_description", String.class),
                row.get("source_amount", BigDecimal.class),
                row.get("share_currency", String.class));
        return new Sharing(persona(row), origen, null);
    }

    private static Sharing.Share parte(Row row) {
        return new Sharing.Share(
                row.get("id", UUID.class),
                persona(row),
                row.get("amount", BigDecimal.class),
                row.get("currency_code", String.class),
                ShareStatus.valueOf(row.get("status", String.class)));
    }

    private static Sharing.Person persona(Row row) {
        return new Sharing.Person(row.get("email", String.class), row.get("first_name", String.class),
                row.get("last_name", String.class));
    }
}
