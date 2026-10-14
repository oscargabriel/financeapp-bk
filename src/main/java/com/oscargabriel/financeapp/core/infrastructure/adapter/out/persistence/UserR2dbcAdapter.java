package com.oscargabriel.financeapp.core.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.core.domain.model.User;
import com.oscargabriel.financeapp.core.domain.model.UserCredentials;
import com.oscargabriel.financeapp.core.domain.model.UserProfile;
import com.oscargabriel.financeapp.core.domain.port.out.UserRepositoryPort;

import io.r2dbc.spi.Row;
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
            SELECT id, password_hash, token_version, google_sub
              FROM finance.users
             WHERE lower(email) = lower(:email)
               AND deleted_at IS NULL
               AND is_active
            """;

    /** Mismo filtro que CREDENCIALES_ACTIVAS, por la cuenta Google vinculada (FIN-52). */
    private static final String CREDENCIALES_GOOGLE_ACTIVAS = """
            SELECT id, password_hash, token_version, google_sub
              FROM finance.users
             WHERE google_sub = :googleSub
               AND deleted_at IS NULL
               AND is_active
            """;

    /** Mismo criterio que ux_users_google_sub: solo entre los no borrados. */
    private static final String EXISTE_GOOGLE = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.users
                 WHERE google_sub = :googleSub
                   AND deleted_at IS NULL
            )
            """;

    /**
     * google_sub IS NULL en el WHERE: si otra peticion vinculo entre la lectura y este UPDATE, no lo
     * pisa y no actualiza ninguna fila.
     */
    private static final String VINCULAR_GOOGLE = """
            UPDATE finance.users
               SET google_sub = :googleSub
             WHERE id = :id
               AND google_sub IS NULL
               AND deleted_at IS NULL
               AND is_active
            """;

    /** Mismo filtro que CREDENCIALES_ACTIVAS: un token vigente de un usuario desactivado no ve su perfil. */
    private static final String PERFIL_ACTIVO = """
            SELECT id, email, first_name, last_name, phone, base_currency_code, timezone
              FROM finance.users
             WHERE id = :id
               AND deleted_at IS NULL
               AND is_active
            """;

    /**
     * Un usuario registrado con Google no tiene hash: el texto vacio no coincide con ninguna contrasena
     * y evita un nulo en el mapeo (design.md de FIN-52, decision 7, hasta FIN-53).
     */
    private static final String HASH_ACTIVO = """
            SELECT coalesce(password_hash, '') AS password_hash
              FROM finance.users
             WHERE id = :id
               AND deleted_at IS NULL
               AND is_active
            """;

    /** Mismo criterio que ux_users_email: sin distinguir mayusculas y solo entre los no borrados. */
    private static final String EXISTE_EMAIL_DE_OTRO = """
            SELECT EXISTS (
                SELECT 1
                  FROM finance.users
                 WHERE lower(email) = lower(:email)
                   AND deleted_at IS NULL
                   AND id <> :id
            )
            """;

    /** updated_at lo pone trg_users_updated_at. */
    private static final String ACTUALIZAR_PERFIL = """
            UPDATE finance.users
               SET email = :email,
                   first_name = :firstName,
                   last_name = :lastName,
                   phone = :phone,
                   base_currency_code = :baseCurrencyCode,
                   timezone = :timezone
             WHERE id = :id
               AND deleted_at IS NULL
               AND is_active
            RETURNING id, email, first_name, last_name, phone, base_currency_code, timezone
            """;

    /**
     * Sube token_version en la misma sentencia (FIN-47): todo access y todo refresh emitido con la
     * version anterior deja de servir en el acto, sin tocar refresh_tokens.
     */
    private static final String ACTUALIZAR_CLAVE = """
            UPDATE finance.users
               SET password_hash = :passwordHash,
                   token_version = token_version + 1
             WHERE id = :id
               AND deleted_at IS NULL
               AND is_active
            """;

    private static final String INSERTAR_USUARIO = """
            INSERT INTO finance.users
                   (id, email, password_hash, first_name, last_name, phone, base_currency_code, timezone,
                    google_sub)
            VALUES (:id, :email, :passwordHash, :firstName, :lastName, :phone, :baseCurrencyCode, :timezone,
                    :googleSub)
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
                .map((row, metadata) -> credenciales(row))
                .one();
    }

    @Override
    public Mono<UserCredentials> findActiveByGoogleSub(String googleSub) {
        return databaseClient.sql(CREDENCIALES_GOOGLE_ACTIVAS)
                .bind("googleSub", googleSub)
                .map((row, metadata) -> credenciales(row))
                .one();
    }

    @Override
    public Mono<Boolean> existsByGoogleSub(String googleSub) {
        return databaseClient.sql(EXISTE_GOOGLE)
                .bind("googleSub", googleSub)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }

    /**
     * Un sub que otro usuario tomo entre la busqueda y el UPDATE choca con ux_users_google_sub: para
     * el caso de uso es lo mismo que no haber podido vincular.
     */
    @Override
    public Mono<Boolean> linkGoogle(UUID id, String googleSub) {
        return databaseClient.sql(VINCULAR_GOOGLE)
                .bind("id", id)
                .bind("googleSub", googleSub)
                .fetch()
                .rowsUpdated()
                .map(filas -> filas > 0)
                .onErrorResume(DataIntegrityViolationException.class, e -> Mono.just(false));
    }

    private static UserCredentials credenciales(Row row) {
        return new UserCredentials(
                row.get("id", UUID.class),
                row.get("password_hash", String.class),
                row.get("token_version", Integer.class),
                row.get("google_sub", String.class));
    }

    @Override
    public Mono<UserProfile> findActiveProfile(UUID id) {
        return databaseClient.sql(PERFIL_ACTIVO)
                .bind("id", id)
                .map((row, metadata) -> perfil(row))
                .one();
    }

    @Override
    public Mono<String> findActivePasswordHash(UUID id) {
        return databaseClient.sql(HASH_ACTIVO)
                .bind("id", id)
                .map((row, metadata) -> row.get("password_hash", String.class))
                .one();
    }

    @Override
    public Mono<Boolean> existsByEmailForOtherUser(String email, UUID id) {
        return databaseClient.sql(EXISTE_EMAIL_DE_OTRO)
                .bind("email", email)
                .bind("id", id)
                .map((row, metadata) -> row.get(0, Boolean.class))
                .one();
    }

    @Override
    public Mono<UserProfile> updateProfile(UserProfile profile) {
        DatabaseClient.GenericExecuteSpec sentencia = databaseClient.sql(ACTUALIZAR_PERFIL)
                .bind("id", profile.id())
                .bind("email", profile.email())
                .bind("firstName", profile.firstName())
                .bind("baseCurrencyCode", profile.baseCurrencyCode())
                .bind("timezone", profile.timezone());
        sentencia = bindOpcional(sentencia, "lastName", profile.lastName());
        sentencia = bindOpcional(sentencia, "phone", profile.phone());

        return sentencia.map((row, metadata) -> perfil(row))
                .one()
                .onErrorMap(DataIntegrityViolationException.class, this::comoConflicto);
    }

    @Override
    public Mono<Void> updatePassword(UUID id, String passwordHash) {
        return databaseClient.sql(ACTUALIZAR_CLAVE)
                .bind("id", id)
                .bind("passwordHash", passwordHash)
                .fetch()
                .rowsUpdated()
                .then();
    }

    private static UserProfile perfil(Row row) {
        return new UserProfile(
                row.get("id", UUID.class),
                row.get("email", String.class),
                row.get("first_name", String.class),
                row.get("last_name", String.class),
                row.get("phone", String.class),
                row.get("base_currency_code", String.class),
                row.get("timezone", String.class));
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
                .bind("firstName", user.firstName())
                .bind("baseCurrencyCode", user.baseCurrencyCode())
                .bind("timezone", user.timezone());

        sentencia = bindOpcional(sentencia, "passwordHash", user.passwordHash());
        sentencia = bindOpcional(sentencia, "lastName", user.lastName());
        sentencia = bindOpcional(sentencia, "phone", user.phone());
        sentencia = bindOpcional(sentencia, "googleSub", user.googleSub());

        return sentencia.fetch().rowsUpdated();
    }

    /** bind no admite null: un opcional ausente va con bindNull y su tipo. */
    private static DatabaseClient.GenericExecuteSpec bindOpcional(
            DatabaseClient.GenericExecuteSpec sentencia, String nombre, String valor) {
        return valor == null ? sentencia.bindNull(nombre, String.class) : sentencia.bind(nombre, valor);
    }

    private Mono<Long> copiarCategorias(User user) {
        return databaseClient.sql(COPIAR_CATEGORIAS)
                .bind("userId", user.id())
                .fetch()
                .rowsUpdated();
    }

    /**
     * El caso de uso ya pregunta si el email esta libre, pero entre esa consulta y el INSERT del alta
     * o el UPDATE del perfil cabe otro registro con el mismo correo. Cuando pasa, el unico ux_users_email lo corta y aqui se
     * traduce al mismo 409 en vez de dejar que salga como un 500.
     */
    private BadRequestException comoConflicto(DataIntegrityViolationException e) {
        return new BadRequestException(HttpStatus.CONFLICT, ErrorCodes.DUPLICATE_RESOURCE,
                "Ya hay una cuenta registrada con ese correo", "email", e);
    }
}
