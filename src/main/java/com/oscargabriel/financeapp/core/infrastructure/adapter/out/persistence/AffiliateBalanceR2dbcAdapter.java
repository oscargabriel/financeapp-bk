package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.core.domain.model.AffiliateBalance;
import com.oscargabriel.financeapp.core.domain.model.AffiliationStatus;
import com.oscargabriel.financeapp.core.domain.model.CurrencyBalance;
import com.oscargabriel.financeapp.core.domain.model.ShareDirection;
import com.oscargabriel.financeapp.core.domain.model.ShareStatus;
import com.oscargabriel.financeapp.core.domain.model.ShareWithAffiliate;
import com.oscargabriel.financeapp.core.domain.model.Sharing;
import com.oscargabriel.financeapp.core.domain.port.out.AffiliateBalanceQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;

@Component
@AllArgsConstructor
public class AffiliateBalanceR2dbcAdapter implements AffiliateBalanceQueryPort {

    /**
     * Una fila por persona y moneda (FIN-67). relaciones deja una afiliacion por persona: la ACCEPTED, o la UNLINKED
     * desvinculada mas tarde. deudas suma las partes PENDING de cada persona y moneda en los dos sentidos. Salen las
     * ACCEPTED, con o sin deuda (la fila sin moneda es a paz y salvo), y las UNLINKED que tengan alguna.
     */
    private static final String SALDOS = """
            WITH relaciones AS (
                SELECT DISTINCT ON (otro) id, status, created_at, otro
                  FROM (SELECT id, status, created_at, updated_at,
                               CASE WHEN requester_id = :userId THEN addressee_id ELSE requester_id END AS otro
                          FROM finance.affiliations
                         WHERE (requester_id = :userId OR addressee_id = :userId)
                           AND status IN ('ACCEPTED', 'UNLINKED')) a
                 ORDER BY otro, status = 'ACCEPTED' DESC, updated_at DESC, id DESC
            ),
            deudas AS (
                SELECT CASE WHEN payer_id = :userId THEN debtor_id ELSE payer_id END AS otro,
                       currency_code,
                       COALESCE(SUM(amount) FILTER (WHERE payer_id = :userId), 0) AS owed_to_me,
                       COALESCE(SUM(amount) FILTER (WHERE debtor_id = :userId), 0) AS i_owe
                  FROM finance.expense_shares
                 WHERE status = 'PENDING'
                   AND (payer_id = :userId OR debtor_id = :userId)
                 GROUP BY 1, 2
            )
            SELECT r.id, r.status, u.email, u.first_name, u.last_name,
                   d.currency_code, d.owed_to_me, d.i_owe
              FROM relaciones r
              JOIN finance.users u ON u.id = r.otro
              LEFT JOIN deudas d ON d.otro = r.otro
             WHERE r.status = 'ACCEPTED' OR d.otro IS NOT NULL
             ORDER BY r.created_at DESC, r.id DESC, d.currency_code
            """;

    /** Las partes entre los dos, en cualquier estado; a_favor dice si el usuario es quien pago. */
    private static final String PARTES = """
            SELECT id, description, occurred_at, amount, currency_code, status, resolved_at,
                   payer_id = :userId AS a_favor
              FROM finance.expense_shares
             WHERE (payer_id = :userId AND debtor_id = :otherUserId)
                OR (payer_id = :otherUserId AND debtor_id = :userId)
             ORDER BY occurred_at DESC, id DESC
            """;

    private final DatabaseClient databaseClient;

    /** Las filas de una persona llegan juntas y en orden: se agrupan sin reordenar. */
    @Override
    public Flux<AffiliateBalance> findBalances(UUID userId) {
        return databaseClient.sql(SALDOS)
                .bind("userId", userId)
                .map((row, metadata) -> new Fila(saldoSinMonedas(row), moneda(row)))
                .all()
                .bufferUntilChanged(fila -> fila.saldo().affiliationId())
                .map(AffiliateBalanceR2dbcAdapter::agrupar);
    }

    @Override
    public Flux<ShareWithAffiliate> findSharesBetween(UUID userId, UUID otherUserId) {
        return databaseClient.sql(PARTES)
                .bind("userId", userId)
                .bind("otherUserId", otherUserId)
                .map((row, metadata) -> new ShareWithAffiliate(
                        row.get("id", UUID.class),
                        row.get("description", String.class),
                        instante(row, "occurred_at"),
                        row.get("amount", BigDecimal.class),
                        row.get("currency_code", String.class),
                        ShareStatus.valueOf(row.get("status", String.class)),
                        instante(row, "resolved_at"),
                        Boolean.TRUE.equals(row.get("a_favor", Boolean.class)) ? ShareDirection.OWED_TO_ME : ShareDirection.I_OWE))
                .all();
    }

    private record Fila(AffiliateBalance saldo, CurrencyBalance moneda) {
    }

    private static AffiliateBalance saldoSinMonedas(Row row) {
        return new AffiliateBalance(
                row.get("id", UUID.class),
                AffiliationStatus.valueOf(row.get("status", String.class)),
                new Sharing.Person(row.get("email", String.class), row.get("first_name", String.class),
                        row.get("last_name", String.class)),
                List.of());
    }

    /** null en la fila de una persona sin deudas. */
    private static CurrencyBalance moneda(Row row) {
        String codigo = row.get("currency_code", String.class);
        return codigo == null ? null : new CurrencyBalance(codigo, row.get("owed_to_me", BigDecimal.class),
                row.get("i_owe", BigDecimal.class));
    }

    private static AffiliateBalance agrupar(List<Fila> filas) {
        AffiliateBalance primera = filas.getFirst().saldo();
        return new AffiliateBalance(primera.affiliationId(), primera.status(), primera.person(),
                filas.stream().map(Fila::moneda).filter(Objects::nonNull).toList());
    }

    private static Instant instante(Row row, String columna) {
        OffsetDateTime valor = row.get(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }
}
