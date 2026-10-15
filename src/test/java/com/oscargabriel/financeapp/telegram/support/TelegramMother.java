package com.oscargabriel.financeapp.telegram.support;

import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;

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

    private TelegramMother() {
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

    /** Un contacto: el mensaje llega sin text. */
    public static String mensajeSinTexto() {
        return """
                {"update_id":10002,"message":{"message_id":2,"date":1791590401,
                 "chat":{"id":%d,"type":"private"},
                 "contact":{"phone_number":"+573001234567","first_name":"Prueba","user_id":%d}}}"""
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
