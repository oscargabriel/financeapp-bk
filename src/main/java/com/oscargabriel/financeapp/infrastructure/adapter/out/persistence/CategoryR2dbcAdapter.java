package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.util.Set;
import java.util.UUID;

import lombok.AllArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.Category;
import com.oscargabriel.financeapp.domain.model.CategoryScope;
import com.oscargabriel.financeapp.domain.model.NewCategory;
import com.oscargabriel.financeapp.domain.model.TransactionType;
import com.oscargabriel.financeapp.domain.port.out.CategoryQueryPort;
import com.oscargabriel.financeapp.domain.port.out.CategoryRepositoryPort;

import io.r2dbc.spi.Row;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Component
@AllArgsConstructor
public class CategoryR2dbcAdapter implements CategoryQueryPort, CategoryRepositoryPort {

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

    /**
     * Al final de la lista: el mayor sort_order de las vivas del usuario mas 10, calculado en la misma
     * sentencia para no hacer otro viaje. Dos altas simultaneas pueden empatar; el listado desempata
     * por nombre.
     */
    private static final String INSERTAR = """
            INSERT INTO finance.categories
                   (id, user_id, name, applies_to, icon, color, sort_order)
            SELECT :id, :userId, :name, :appliesTo, :icon, :color,
                   COALESCE((SELECT max(sort_order)
                               FROM finance.categories
                              WHERE user_id = :userId
                                AND deleted_at IS NULL), 0) + 10
            RETURNING id, name, applies_to, icon, color, is_system
            """;

    private static final String POR_ID = """
            SELECT id,
                   name,
                   applies_to,
                   icon,
                   color,
                   is_system
              FROM finance.categories
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            """;

    private static final String TIENE_MOVIMIENTOS = """
            SELECT EXISTS (SELECT 1
                             FROM finance.transactions
                            WHERE category_id = :categoryId
                              AND type = :type) AS tiene
            """;

    /** Mismo filtro que POR_ID: borrada entre la lectura y la escritura, el RETURNING sale vacio. */
    private static final String ACTUALIZAR = """
            UPDATE finance.categories
               SET name = :name,
                   applies_to = :appliesTo,
                   icon = :icon,
                   color = :color
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            RETURNING id, name, applies_to, icon, color, is_system
            """;

    /** Ya borrada no cuenta como fila afectada: borrarla otra vez es el mismo 404 que una inexistente. */
    private static final String BORRAR = """
            UPDATE finance.categories
               SET deleted_at = now()
             WHERE id = :id
               AND user_id = :userId
               AND deleted_at IS NULL
            """;

    private final DatabaseClient databaseClient;

    @Override
    public Flux<Category> findActiveByUser(UUID userId, Set<CategoryScope> scopes) {
        return databaseClient.sql(SQL)
                .bind("userId", userId)
                .bind("scopes", scopes.stream().map(Enum::name).toArray(String[]::new))
                .map((row, metadata) -> toDomain(row))
                .all();
    }

    @Override
    public Mono<Category> create(NewCategory category) {
        return databaseClient.sql(INSERTAR)
                .bind("id", category.id())
                .bind("userId", category.userId())
                .bind("name", category.name())
                .bind("appliesTo", category.appliesTo().name())
                .bind("icon", category.icon())
                .bind("color", category.color())
                .map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, CategoryR2dbcAdapter::comoConflicto);
    }

    @Override
    public Mono<Category> findActiveByIdAndUser(UUID categoryId, UUID userId) {
        return databaseClient.sql(POR_ID)
                .bind("id", categoryId)
                .bind("userId", userId)
                .map((row, metadata) -> toDomain(row))
                .one();
    }

    @Override
    public Mono<Boolean> hasTransactionsOfType(UUID categoryId, TransactionType type) {
        return databaseClient.sql(TIENE_MOVIMIENTOS)
                .bind("categoryId", categoryId)
                .bind("type", type.name())
                .map((row, metadata) -> Boolean.TRUE.equals(row.get("tiene", Boolean.class)))
                .one();
    }

    @Override
    public Mono<Category> update(UUID userId, Category category) {
        return databaseClient.sql(ACTUALIZAR)
                .bind("id", category.id())
                .bind("userId", userId)
                .bind("name", category.name())
                .bind("appliesTo", category.appliesTo().name())
                .bind("icon", category.icon())
                .bind("color", category.color())
                .map((row, metadata) -> toDomain(row))
                .one()
                .onErrorMap(DuplicateKeyException.class, CategoryR2dbcAdapter::comoConflicto);
    }

    @Override
    public Mono<Boolean> softDelete(UUID categoryId, UUID userId) {
        return databaseClient.sql(BORRAR)
                .bind("id", categoryId)
                .bind("userId", userId)
                .fetch().rowsUpdated()
                .map(filas -> filas > 0);
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

    /**
     * El unico indice unico de la tabla ademas de la PK es ux_categories_user_name, y la PK es un v7
     * recien generado: un duplicado aqui es un nombre que ya usa otra categoria viva del usuario.
     */
    private static BadRequestException comoConflicto(DuplicateKeyException e) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una categoria con ese nombre", "name", e);
    }
}
