package com.oscargabriel.financeapp.telegram.support;

import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;

import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.UsuarioDelBot;

/**
 * El secreto del webhook y los updates de Telegram para los tests. El secreto tiene que ser el mismo
 * que declara src/test/resources/application.yaml en telegram.webhook-secret: si dejan de coincidir,
 * todo update de la suite sale 401.
 */
public final class TelegramMother {

    public static final String CABECERA = "X-Telegram-Bot-Api-Secret-Token";

    public static final String SECRETO = "secreto-del-webhook-de-pruebas-0123456789";

    public static final String TOKEN = "123456:token-del-bot-de-pruebas";

    public static final long CHAT = 123456L;

    public static final long GRUPO = -100123456L;

    /** El celular admitido de ana(), con el + que le agrega el caso de uso. */
    public static final String TELEFONO = "+573009990001";

    private TelegramMother() {
    }

    public static UsuarioDelBot ana() {
        return new UsuarioDelBot(UUID.fromString("10000000-0000-7000-8000-000000000006"), "Ana");
    }

    /** Un mensaje del chat privado CHAT, cuyo remitente es el mismo CHAT, con o sin contacto. */
    public static MensajeDeTelegram privado(MensajeDeTelegram.Contacto contacto) {
        return new MensajeDeTelegram(CHAT, true, CHAT, contacto);
    }

    /** Un mensaje del grupo GRUPO, escrito por CHAT. */
    public static MensajeDeTelegram deGrupo(MensajeDeTelegram.Contacto contacto) {
        return new MensajeDeTelegram(GRUPO, false, CHAT, contacto);
    }

    /** El contacto del propio remitente CHAT. */
    public static MensajeDeTelegram.Contacto contactoPropio(String telefono) {
        return new MensajeDeTelegram.Contacto(CHAT, telefono);
    }

    public static Consumer<HttpHeaders> cabecera() {
        return headers -> headers.set(CABECERA, SECRETO);
    }

    public static Consumer<HttpHeaders> cabeceraConSecretoEquivocado() {
        return headers -> headers.set(CABECERA, SECRETO + "-mal");
    }

    public static String mensajeDeTexto() {
        return """
                {"update_id":10001,"message":{"message_id":1,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Prueba"},
                 "chat":{"id":%d,"type":"private","first_name":"Prueba"},"text":"hola"}}"""
                .formatted(CHAT, CHAT);
    }

    /** Una foto: el mensaje llega sin text ni contacto. */
    public static String mensajeSinTexto() {
        return """
                {"update_id":10002,"message":{"message_id":2,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Prueba"},
                 "chat":{"id":%d,"type":"private"},
                 "photo":[{"file_id":"foto","file_unique_id":"foto","width":90,"height":90}]}}"""
                .formatted(CHAT, CHAT);
    }

    /** El contacto propio del remitente, con el phone_number sin + como lo manda Telegram a veces. */
    public static String mensajeConContactoPropio() {
        return """
                {"update_id":10006,"message":{"message_id":5,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},
                 "chat":{"id":%d,"type":"private","first_name":"Ana"},
                 "contact":{"phone_number":"573009990001","first_name":"Ana","user_id":%d}}}"""
                .formatted(CHAT, CHAT, CHAT);
    }

    /** Un chat que no dice su tipo: se trata como no privado. */
    public static String mensajeDeChatSinTipo() {
        return """
                {"update_id":10007,"message":{"message_id":6,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Prueba"},
                 "chat":{"id":%d},"text":"hola"}}"""
                .formatted(CHAT, CHAT);
    }

    /** Un mensaje editado: el update no trae message. */
    public static String sinMensaje() {
        return """
                {"update_id":10003,"edited_message":{"message_id":1,"date":1791590401,
                 "chat":{"id":%d,"type":"private"},"text":"hola de nuevo"}}"""
                .formatted(CHAT);
    }

    public static String mensajeSinChat() {
        return """
                {"update_id":10004,"message":{"message_id":3,"date":1791590401,"text":"hola"}}""";
    }
}
