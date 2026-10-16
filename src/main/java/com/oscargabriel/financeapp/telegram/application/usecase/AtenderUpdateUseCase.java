package com.oscargabriel.financeapp.telegram.application.usecase;

import java.util.Optional;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.UsuarioDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.in.AtenderUpdatePort;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Decide que contesta el bot (FIN-60): solo atiende chats privados, saluda al chat vinculado y vincula
 * el que comparte el contacto propio. El mensaje de un chat vinculado todavia no llega al asistente:
 * eso es FIN-61. Los telefonos no van al log.
 */
@Service
@AllArgsConstructor
@Slf4j
public class AtenderUpdateUseCase implements AtenderUpdatePort {

    static final RespuestaDelBot SOLO_PRIVADOS = new RespuestaDelBot(
            "Este bot solo atiende chats privados.", TecladoDelBot.NINGUNO);

    static final RespuestaDelBot SIN_VINCULAR = new RespuestaDelBot(
            "Este chat todavía no está vinculado a una cuenta de FinanceApp. Comparte tu número con el botón de"
                    + " abajo para vincularlo.",
            TecladoDelBot.PEDIR_CONTACTO);

    static final RespuestaDelBot CONTACTO_AJENO = new RespuestaDelBot(
            "Solo puedo vincular este chat con tu propio número. Compártelo con el botón de abajo.",
            TecladoDelBot.PEDIR_CONTACTO);

    /** El mismo texto para todo motivo: no dice si el telefono esta admitido ni si es de alguien. */
    static final RespuestaDelBot NO_VINCULADO = new RespuestaDelBot(
            "No pude vincular este chat con ese número.", TecladoDelBot.QUITAR);

    /** La misma expresion E.164 que el phone del alta (FIN-59): telegram no importa el core. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");

    private final BotDeTelegramPort bot;
    private final ChatsVinculadosPort chats;

    /**
     * Un fallo de la base o del envio ya quedo en el log de su adapter: aqui termina vacio, sin
     * respuesta, para que el webhook de 200 y Telegram no reintente.
     */
    @Override
    public Mono<Void> atender(MensajeDeTelegram mensaje) {
        return Mono.defer(() -> responder(mensaje))
                .flatMap(respuesta -> bot.enviar(mensaje.chatId(), respuesta))
                .onErrorResume(error -> Mono.empty());
    }

    private Mono<RespuestaDelBot> responder(MensajeDeTelegram mensaje) {
        if (!mensaje.privado()) {
            return Mono.just(SOLO_PRIVADOS);
        }
        return chats.usuarioDelChat(mensaje.chatId())
                .map(AtenderUpdateUseCase::yaVinculado)
                .switchIfEmpty(Mono.defer(() -> sinVincular(mensaje)));
    }

    private Mono<RespuestaDelBot> sinVincular(MensajeDeTelegram mensaje) {
        if (mensaje.contactoCompartido().isEmpty()) {
            return Mono.just(SIN_VINCULAR);
        }
        if (!mensaje.esContactoPropio()) {
            return Mono.just(CONTACTO_AJENO);
        }
        return telefonoE164(mensaje.contacto().telefono())
                .map(telefono -> chats.vincular(mensaje.chatId(), telefono)
                        .doOnNext(usuario -> log.info("Chat {} vinculado al usuario {}", mensaje.chatId(), usuario.id()))
                        .map(AtenderUpdateUseCase::recienVinculado))
                .orElseGet(Mono::empty)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    log.info("No se pudo vincular el chat {}", mensaje.chatId());
                    return NO_VINCULADO;
                }));
    }

    /** Telegram manda phone_number a veces sin +. */
    private static Optional<String> telefonoE164(String telefono) {
        return Optional.ofNullable(telefono)
                .map(String::trim)
                .map(t -> t.startsWith("+") ? t : "+" + t)
                .filter(t -> E164.matcher(t).matches());
    }

    private static RespuestaDelBot yaVinculado(UsuarioDelBot usuario) {
        return new RespuestaDelBot(
                "Hola, %s. Este chat ya está vinculado a tu cuenta de FinanceApp.".formatted(usuario.nombre()),
                TecladoDelBot.NINGUNO);
    }

    private static RespuestaDelBot recienVinculado(UsuarioDelBot usuario) {
        return new RespuestaDelBot(
                "Hola, %s. Este chat quedó vinculado a tu cuenta de FinanceApp.".formatted(usuario.nombre()),
                TecladoDelBot.QUITAR);
    }
}
