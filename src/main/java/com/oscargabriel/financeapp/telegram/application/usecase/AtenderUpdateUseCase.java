package com.oscargabriel.financeapp.telegram.application.usecase;

import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import com.oscargabriel.financeapp.telegram.domain.exceptions.GrupoYaNoPendienteException;
import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.BotonDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.EventoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.TecladoDelBot;
import com.oscargabriel.financeapp.telegram.domain.model.UsuarioDelBot;
import com.oscargabriel.financeapp.telegram.domain.port.in.AtenderUpdatePort;
import com.oscargabriel.financeapp.telegram.domain.port.out.AsistenteDelBotPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.BotDeTelegramPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.ChatsVinculadosPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.GruposDelBotPort;
import com.oscargabriel.financeapp.telegram.domain.port.out.UpdatesAtendidosPort;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

/**
 * Decide que contesta el bot. Solo atiende chats privados y vincula el que comparte el contacto propio (FIN-60). El
 * chat vinculado habla con el asistente, y sus botones confirman o descartan lo que registro (FIN-61). Un update se
 * atiende como mucho una vez. Ni los telefonos ni el texto del usuario van al log.
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

    static final RespuestaDelBot REINTENTAR = new RespuestaDelBot(
            "No pude procesar tu mensaje. Intenta de nuevo en un momento.", TecladoDelBot.NINGUNO);

    static final RespuestaDelBot NO_ADMITIDO = new RespuestaDelBot(
            "Solo entiendo texto, notas de voz, audios y fotos.", TecladoDelBot.NINGUNO);

    static final RespuestaDelBot ARCHIVO_NO_ADMITIDO = new RespuestaDelBot(
            "No pude leer ese archivo. Manda una foto o una nota de voz de hasta 5 MB.", TecladoDelBot.NINGUNO);

    static final RespuestaDelBot YA_NO_PENDIENTE = new RespuestaDelBot(
            "Esos movimientos ya no están pendientes.", TecladoDelBot.NINGUNO);

    /** Deja los botones: se puede reintentar o resolver en la web (design.md de FIN-61, decision 5). */
    static final RespuestaDelBot NO_GUARDADO = new RespuestaDelBot(
            "No pude guardar los movimientos ahora. Intenta de nuevo o revísalos en Pendientes de la web.",
            TecladoDelBot.NINGUNO);

    static final RespuestaDelBot DESCARTADOS = new RespuestaDelBot(
            "Descarté los movimientos. No se guardó nada.", TecladoDelBot.NINGUNO);

    /** La misma expresion E.164 que el phone del alta (FIN-59): telegram no importa el core. */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9][0-9]{6,14}$");

    /** Los datos de los botones de ConfirmarODescartar, con el UUID canonico: UUID.fromString acepta mas. */
    private static final Pattern DATOS_DEL_BOTON = Pattern.compile(
            "^(confirmar|descartar):([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})$");

    private final BotDeTelegramPort bot;
    private final ChatsVinculadosPort chats;
    private final UpdatesAtendidosPort updates;
    private final AsistenteDelBotPort asistente;
    private final GruposDelBotPort grupos;

    /**
     * Termina vacio aun si algo falla, para que el webhook de 200 y Telegram no reintente: el fallo se responde en el
     * chat, y si el envio tambien falla ya quedo en el log de su adapter.
     */
    @Override
    public Mono<Void> atender(long updateId, EventoDeTelegram evento) {
        return switch (evento) {
            case MensajeDeTelegram mensaje -> atenderMensaje(updateId, mensaje);
            case BotonDeTelegram boton -> atenderBoton(updateId, boton);
        };
    }

    private Mono<Void> atenderMensaje(long updateId, MensajeDeTelegram mensaje) {
        return updates.primeraVez(updateId)
                .filter(Boolean::booleanValue)
                .flatMap(primera -> responder(mensaje))
                .onErrorResume(error -> fallo(updateId, error, REINTENTAR))
                .flatMap(respuesta -> enviar(mensaje.chatId(), respuesta));
    }

    private Mono<RespuestaDelBot> responder(MensajeDeTelegram mensaje) {
        if (!mensaje.privado()) {
            return Mono.just(SOLO_PRIVADOS);
        }
        return chats.usuarioDelChat(mensaje.chatId())
                .map(usuario -> vinculado(usuario, mensaje))
                .defaultIfEmpty(Mono.defer(() -> sinVincular(mensaje)))
                .flatMap(respuesta -> respuesta);
    }

    private Mono<RespuestaDelBot> vinculado(UsuarioDelBot usuario, MensajeDeTelegram mensaje) {
        if (mensaje.contactoCompartido().isPresent() || mensaje.esStart()) {
            return Mono.just(yaVinculado(usuario));
        }
        if (mensaje.sinContenido()) {
            return Mono.just(NO_ADMITIDO);
        }
        ArchivoDeTelegram archivo = mensaje.archivo();
        if (archivo == null) {
            return asistente.atender(usuario.id(), mensaje.texto(), null, null)
                    .map(respuesta -> respuesta(respuesta, usuario.zona()));
        }
        if (archivo.tamano() != null && archivo.tamano() > ArchivoDeTelegram.TOPE_ARCHIVO) {
            return Mono.just(ARCHIVO_NO_ADMITIDO);
        }
        return bot.descargar(archivo.fileId())
                .flatMap(bytes -> asistente.atender(usuario.id(), mensaje.texto(), archivo.mime(), bytes))
                .map(respuesta -> respuesta(respuesta, usuario.zona()));
    }

    /** Sin default: una respuesta nueva del asistente sin texto no compila. */
    private static RespuestaDelBot respuesta(RespuestaDelAsistente respuesta, ZoneId zona) {
        return switch (respuesta) {
            case RespuestaDelAsistente.Texto texto -> new RespuestaDelBot(texto.mensaje(), TecladoDelBot.NINGUNO);
            case RespuestaDelAsistente.Registro registro -> new RespuestaDelBot(TextosDelBot.registro(registro, zona),
                    new TecladoDelBot.ConfirmarODescartar(registro.grupo()));
            case RespuestaDelAsistente.Movimientos consulta -> new RespuestaDelBot(
                    TextosDelBot.movimientos(consulta, zona), TecladoDelBot.NINGUNO);
            case RespuestaDelAsistente.Saldo saldo -> new RespuestaDelBot(TextosDelBot.saldo(saldo),
                    TecladoDelBot.NINGUNO);
            case RespuestaDelAsistente.ArchivoNoAdmitido noAdmitido -> ARCHIVO_NO_ADMITIDO;
        };
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

    /**
     * Cierra la espera del boton siempre. Sin el mensaje del boton no hay a que chat responder ni que botones quitar;
     * el usuario sale del chat vinculado, nunca de los datos del boton (design.md de FIN-61, decision 4).
     */
    private Mono<Void> atenderBoton(long updateId, BotonDeTelegram boton) {
        return updates.primeraVez(updateId)
                .filter(Boolean::booleanValue)
                .flatMap(primera -> cerrarEspera(boton).then(Mono.defer(() -> responder(boton))))
                .onErrorResume(error -> fallo(updateId, error, NO_GUARDADO))
                .flatMap(respuesta -> boton.chatId() == null ? Mono.empty() : enviar(boton.chatId(), respuesta));
    }

    private Mono<RespuestaDelBot> responder(BotonDeTelegram boton) {
        if (boton.chatId() == null) {
            return Mono.empty();
        }
        if (!boton.privado()) {
            return Mono.just(SOLO_PRIVADOS);
        }
        return chats.usuarioDelChat(boton.chatId())
                .map(usuario -> resolverGrupo(usuario, boton))
                .defaultIfEmpty(Mono.just(SIN_VINCULAR))
                .flatMap(respuesta -> respuesta);
    }

    /** Datos que no son de ConfirmarODescartar: nada mas que la espera ya cerrada. */
    private Mono<RespuestaDelBot> resolverGrupo(UsuarioDelBot usuario, BotonDeTelegram boton) {
        Matcher datos = DATOS_DEL_BOTON.matcher(boton.datos() == null ? "" : boton.datos());
        if (!datos.matches()) {
            return Mono.empty();
        }
        UUID grupo = UUID.fromString(datos.group(2));
        Mono<RespuestaDelBot> resultado = datos.group(1).equals("confirmar")
                ? grupos.confirmar(usuario.id(), grupo)
                        .map(cuantos -> new RespuestaDelBot(TextosDelBot.confirmados(cuantos), TecladoDelBot.NINGUNO))
                : grupos.descartar(usuario.id(), grupo).thenReturn(DESCARTADOS);
        return resultado
                .onErrorResume(GrupoYaNoPendienteException.class, yaNo -> Mono.just(YA_NO_PENDIENTE))
                .flatMap(respuesta -> quitarBotones(boton).thenReturn(respuesta))
                .onErrorResume(error -> {
                    log.warn("No se pudo resolver el grupo {} del chat {}: {}", grupo, boton.chatId(),
                            error.getClass().getSimpleName());
                    return Mono.just(NO_GUARDADO);
                });
    }

    private Mono<Void> cerrarEspera(BotonDeTelegram boton) {
        return bot.cerrarEspera(boton.callbackId()).onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> quitarBotones(BotonDeTelegram boton) {
        return bot.quitarBotones(boton.chatId(), boton.messageId()).onErrorResume(error -> Mono.empty());
    }

    private Mono<Void> enviar(long chatId, RespuestaDelBot respuesta) {
        return bot.enviar(chatId, respuesta).onErrorResume(error -> Mono.empty());
    }

    /** Solo el tipo del error: el mensaje de una excepcion podria llevar lo que escribio el usuario. */
    private static Mono<RespuestaDelBot> fallo(long updateId, Throwable error, RespuestaDelBot respuesta) {
        log.warn("No se pudo atender el update {}: {}", updateId, error.getClass().getSimpleName());
        return Mono.just(respuesta);
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
