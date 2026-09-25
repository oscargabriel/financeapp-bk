package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.util.Set;
import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;

@Component
public class CategoryR2dbcAdapter implements CategoryQueryPort {

    private static final String SQL = """
            SELECT id,
                   name,
                   applies_to,
                   icon,
                   color,
                   is_system
              FROM finance.categories
             WHERE user_id = :userId
               AND deleted_at IS NULL
               AND applies_to = ANY(:scopes)
             ORDER BY sort_order, lower(name)
            """;

    private final DatabaseClient databaseClient;

    public CategoryR2dbcAdapter(DatabaseClient databaseClient) {
        this.databaseClient = databaseClient;
    }

    @Override
    public Flux<Category> findActiveByUser(UUID userId, Set<CategoryScope> scopes) {
        return databaseClient.sql(SQL)
                .bind("userId", userId)
                .bind("scopes", scopes.stream().map(Enum::name).toArray(String[]::new))
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    private static Category toDomain(Row row) {
        return new Category(
                row.get("id", UUID.class),
                row.get("name", String.class),
                CategoryScope.valueOf(row.get("applies_to", String.class)),
                row.get("icon", String.class),
                row.get("color", String.class),
                Boolean.TRUE.equals(row.get("is_system", Boolean.class)));
    }
}
