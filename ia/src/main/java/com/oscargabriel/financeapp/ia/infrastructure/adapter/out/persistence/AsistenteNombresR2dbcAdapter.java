package com.oscargabriel.financeapp.ia.infrastructure.adapter.out.persistence;

import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.ia.domain.model.CategoryScope;
import com.oscargabriel.financeapp.ia.domain.model.UserAccount;
import com.oscargabriel.financeapp.ia.domain.model.UserCategory;
import com.oscargabriel.financeapp.ia.domain.port.out.NamesQueryPort;

import reactor.core.publisher.Flux;

/** Los mismos filtros y el mismo orden que las cuentas y categorias que el usuario ve en /accounts y /categories. */
@Component
@AllArgsConstructor
public class AsistenteNombresR2dbcAdapter implements NamesQueryPort {

    private static final String CUENTAS = """
            SELECT id, name, currency_code
              FROM finance.accounts
             WHERE user_id = :userId
               AND deleted_at IS NULL
               AND is_active
             ORDER BY lower(name)
            """;

    private static final String CATEGORIAS = """
            SELECT id, name, applies_to
              FROM finance.categories
             WHERE user_id = :userId
               AND deleted_at IS NULL
             ORDER BY sort_order, lower(name)
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Flux<UserAccount> findActiveAccounts(UUID userId) {
        return databaseClient.sql(CUENTAS)
                .bind("userId", userId)
                .map((row, metadata) -> new UserAccount(row.get("id", UUID.class), row.get("name", String.class),
                        row.get("currency_code", String.class)))
                .all();
    }

    @Override
    public Flux<UserCategory> findLiveCategories(UUID userId) {
        return databaseClient.sql(CATEGORIAS)
                .bind("userId", userId)
                .map((row, metadata) -> new UserCategory(row.get("id", UUID.class), row.get("name", String.class),
                        CategoryScope.valueOf(row.get("applies_to", String.class))))
                .all();
    }
}
