package com.oscargabriel.financeapp.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.User;
import com.oscargabriel.financeapp.domain.model.UserCredentials;
import com.oscargabriel.financeapp.domain.port.out.UserRepositoryPort;

import reactor.core.publisher.Mono;

@Component
public class UserR2dbcAdapter implements UserRepositoryPort {

    private static final String EXISTE_EMAIL = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.users
                 WHERE lower(email) = lower(:email)
                   AND deleted_at IS NULL
            )
            """;

    /**
     * is_active y deleted_at se filtran aqui, no en el caso de uso: asi no hay forma de emitir un
     * token para una cuenta desactivada por haberse olvidado una condicion aguas arriba.
     */
    private static final String CREDENCIALES_ACTIVAS = """
            SELECT id, password_hash
              FROM finance.users
             WHERE lower(email) = lower(:email)
               AND deleted_at IS NULL
               AND is_active
            """;

    private static final String INSERTAR_USUARIO = """
            INSERT INTO finance.users
                   (id, email, password_hash, first_name, last_name, base_currency_code, timezone)
            VALUES (:id, :email, :passwordHash, :firstName, :lastName, :baseCurrencyCode, :timezone)
            """;

    /**
     * uuidv7() es nativo desde PostgreSQL 18: copiar las categorias en una sola sentencia conserva
     * el mismo tipo de identificador que genera la aplicacion, sin traerse la semilla a memoria
     * para insertarla fila por fila.
     */
    private static final String COPIAR_CATEGORIAS = """
            INSERT INTO finance.categories
                   (id, user_id, name, applies_to, icon, color, sort_order, is_system)
            SELECT uuidv7(), :userId, d.name, d.applies_to, d.icon, d.color, d.sort_order, TRUE
              FROM finance.default_categories d
             WHERE d.is_active
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transaccion;

    public UserR2dbcAdapter(DatabaseClient databaseClient, ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    @Override
    public Mono<Boolean> existsByEmail(String email) {
        return databaseClient.sql(EXISTE_EMAIL)
                .bind("email", email)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<UserCredentials> findActiveByEmail(String email) {
        return databaseClient.sql(CREDENCIALES_ACTIVAS)
                .bind("email", email)
                .map((row, metadata) -> new UserCredentials(
                        row.get("id", UUID.class),
                        row.get("password_hash", String.class)))
                .one();
    }

    @Override
    public Mono<Long> createWithDefaultCategories(User user) {
        return insertarUsuario(user)
                .then(copiarCategorias(user))
                .as(transaccion::transactional)
                .onErrorMap(DataIntegrityViolationException.class, this::comoConflicto);
    }

    private Mono<Long> insertarUsuario(User user) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(INSERTAR_USUARIO)
                .bind("id", user.id())
                .bind("email", user.email())
                .bind("passwordHash", user.passwordHash())
                .bind("firstName", user.firstName())
                .bind("baseCurrencyCode", user.baseCurrencyCode())
                .bind("timezone", user.timezone());

        sentencia = user.lastName() == null
                ? sentencia.bindNull("lastName", String.class)
                : sentencia.bind("lastName", user.lastName());

        return sentencia.fetch().rowsUpdated();
    }

    private Mono<Long> copiarCategorias(User user) {
        return databaseClient.sql(COPIAR_CATEGORIAS)
                .bind("userId", user.id())
                .fetch()
                .rowsUpdated();
    }

    /**
     * El caso de uso ya pregunta si el email esta libre, pero entre esa consulta y este INSERT cabe
     * otro registro con el mismo correo. Cuando pasa, el unico ux_users_email lo corta y aqui se
     * traduce al mismo 409 en vez de dejar que salga como un 500.
     */
    private BadRequestException comoConflicto(DataIntegrityViolationException e) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta registrada con ese correo", "email", e);
    }
}
