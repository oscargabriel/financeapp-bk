package com.oscargabriel.financeapp.telegram.infrastructure.adapter.out.persistence;

import java.util.UUID;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.ReactiveTransactionManager;
import org.springframework.transaction.reactive.TransactionalOperator;

import com.oscargabriel.financeapp.telegram.domain.model.UsuarioDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;

import io.r2dbc.spi.Row;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Los chats de Telegram sobre finance.users, con consultas propias del modulo y no un puente al core
 * (design.md de FIN-60, decision 1). Un error se loguea solo con su tipo: el mensaje de un error de la
 * base puede llevar los parametros, y entre ellos el telefono.
 */
@Component
@Slf4j
public class TelegramChatR2dbcAdapter implements ChatsVinculadosPort {

    /**
     * Vinculado = chat guardado, usuario activo y no borrado, y phone en la lista de admitidos. Es la misma
     * regla que telegram_linked en UserR2dbcAdapter (PERFIL_ACTIVO): si cambia aqui, cambia alli.
     */
    private static final String USUARIO_DEL_CHAT = """
            SELECT u.id, u.first_name
              FROM finance.users u
             WHERE u.telegram_chat_id = :chatId
               AND u.deleted_at IS NULL
               AND u.is_active
               AND EXISTS (SELECT 1 FROM finance.registration_allowlist a WHERE a.entry = u.phone)
            """;

    /** LIMIT 2: basta para saber si hay mas de uno, y con mas de uno no se vincula a ninguno. */
    private static final String USUARIOS_CON_EL_TELEFONO = """
            SELECT u.id, u.first_name
              FROM finance.users u
             WHERE u.phone = :telefono
               AND u.deleted_at IS NULL
               AND u.is_active
               AND EXISTS (SELECT 1 FROM finance.registration_allowlist a WHERE a.entry = :telefono)
             LIMIT 2
            """;

    /**
     * Una fila puede seguir con el chat despues de perder el acceso, y ux_users_telegram_chat_id cuenta
     * tambien a los inactivos: sin liberarlo, el UPDATE siguiente chocaria.
     */
    private static final String LIBERAR_CHAT = """
            UPDATE finance.users
               SET telegram_chat_id = NULL
             WHERE telegram_chat_id = :chatId
               AND deleted_at IS NULL
               AND id <> :id
            """;

    /** Pisa el chat que el usuario tuviera: solo queda el ultimo que se vinculo. */
    private static final String GUARDAR_CHAT = """
            UPDATE finance.users
               SET telegram_chat_id = :chatId
             WHERE id = :id
            """;

    private final DatabaseClient databaseClient;
    private final TransactionalOperator transaccion;

    public TelegramChatR2dbcAdapter(DatabaseClient databaseClient, ReactiveTransactionManager txManager) {
        this.databaseClient = databaseClient;
        this.transaccion = TransactionalOperator.create(txManager);
    }

    @Override
    public Mono<UsuarioDelBot> usuarioDelChat(long chatId) {
        return databaseClient.sql(USUARIO_DEL_CHAT)
                .bind("chatId", chatId)
                .map((row, metadata) -> usuario(row))
                .one()
                .doOnError(e -> log.warn("No se pudo resolver el chat {}: {}", chatId, e.getClass().getSimpleName()));
    }

    /**
     * Dos sentencias y no CTEs que modifican datos: esos UPDATE ven la misma instantanea y su orden no esta
     * garantizado, asi que el indice unico podria saltar al guardar antes de liberar (decision 3).
     */
    @Override
    public Mono<UsuarioDelBot> vincular(long chatId, String telefono) {
        Mono<UsuarioDelBot> vinculacion = databaseClient.sql(USUARIOS_CON_EL_TELEFONO)
                .bind("telefono", telefono)
                .map((row, metadata) -> usuario(row))
                .all()
                .collectList()
                .filter(usuarios -> usuarios.size() == 1)
                .map(usuarios -> usuarios.getFirst())
                .flatMap(usuario -> liberarYGuardar(chatId, usuario.id()).thenReturn(usuario));

        return transaccion.transactional(vinculacion)
                .doOnError(e -> log.warn("No se pudo vincular el chat {}: {}", chatId, e.getClass().getSimpleName()));
    }

    private Mono<Void> liberarYGuardar(long chatId, UUID id) {
        return databaseClient.sql(LIBERAR_CHAT)
                .bind("chatId", chatId)
                .bind("id", id)
                .fetch()
                .rowsUpdated()
                .then(Mono.defer(() -> databaseClient.sql(GUARDAR_CHAT)
                        .bind("chatId", chatId)
                        .bind("id", id)
                        .fetch()
                        .rowsUpdated()))
                .then();
    }

    private static UsuarioDelBot usuario(Row row) {
        return new UsuarioDelBot(row.get("id", UUID.class), row.get("first_name", String.class));
    }
}
