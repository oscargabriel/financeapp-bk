package com.oscargabriel.financeapp.telegram.support;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.UUID;
import java.util.function.Consumer;

import org.springframework.http.HttpHeaders;

import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.BotonDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.RespuestaDelAsistente;
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
        return new UsuarioDelBot(UUID.fromString("10000000-0000-7000-8000-000000000006"), "Ana",
                ZoneId.of("America/Bogota"));
    }

    /** Un mensaje del chat privado CHAT, cuyo remitente es el mismo CHAT, con o sin contacto. */
    public static MensajeDeTelegram privado(MensajeDeTelegram.Contacto contacto) {
        return new MensajeDeTelegram(CHAT, true, CHAT, contacto, null, null);
    }

    /** Un mensaje del chat privado CHAT con solo texto. */
    public static MensajeDeTelegram texto(String texto) {
        return new MensajeDeTelegram(CHAT, true, CHAT, null, texto, null);
    }

    /** Un mensaje del chat privado CHAT con un archivo y su pie, que puede ser nulo. */
    public static MensajeDeTelegram conArchivo(String pie, ArchivoDeTelegram archivo) {
        return new MensajeDeTelegram(CHAT, true, CHAT, null, pie, archivo);
    }

    /** Un gasto de 45000 COP en Billetera, categoria Mercado, el 17-10-2026 a las 22:00 de Bogota (ya 18 en UTC). */
    public static RespuestaDelAsistente.Movimiento gasto(String descripcion) {
        return new RespuestaDelAsistente.Movimiento("EXPENSE", "Billetera", null, "Mercado", new BigDecimal("45000"),
                "COP", descripcion, Instant.parse("2026-10-18T03:00:00Z"));
    }

    public static RespuestaDelAsistente.Movimiento transferencia() {
        return new RespuestaDelAsistente.Movimiento("TRANSFER", "Billetera", "Ahorros", null, new BigDecimal("100000.5"),
                "COP", "Al ahorro", Instant.parse("2026-10-17T15:00:00Z"));
    }

    public static ArchivoDeTelegram notaDeVoz() {
        return new ArchivoDeTelegram("voz-file-id", "audio/ogg", 12_000L);
    }

    public static ArchivoDeTelegram foto() {
        return new ArchivoDeTelegram("foto-grande", "image/jpeg", 90_000L);
    }

    /** El toque de un boton del mensaje 55 del chat privado CHAT. */
    public static BotonDeTelegram pulsar(String datos) {
        return new BotonDeTelegram("cb-1", CHAT, true, 55L, datos);
    }

    /** El toque de un boton cuyo mensaje Telegram ya no entrega. */
    public static BotonDeTelegram pulsarSinMensaje(String datos) {
        return new BotonDeTelegram("cb-1", null, false, null, datos);
    }

    public static BotonDeTelegram pulsarEnGrupo(String datos) {
        return new BotonDeTelegram("cb-1", GRUPO, false, 55L, datos);
    }

    /** Un mensaje del grupo GRUPO, escrito por CHAT. */
    public static MensajeDeTelegram deGrupo(MensajeDeTelegram.Contacto contacto) {
        return new MensajeDeTelegram(GRUPO, false, CHAT, contacto, null, null);
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

    /** Una foto con pie: Telegram manda varios tamanos, de menor a mayor. */
    public static String fotoConPie() {
        return """
                {"update_id":10010,"message":{"message_id":10,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},
                 "chat":{"id":%d,"type":"private"},"caption":"almuerzo 45000",
                 "photo":[{"file_id":"foto-chica","file_unique_id":"a","width":90,"height":90,"file_size":1200},
                          {"file_id":"foto-grande","file_unique_id":"b","width":1280,"height":960,"file_size":90000}]}}"""
                .formatted(CHAT, CHAT);
    }

    /** Una nota de voz sin pie y sin mime_type, que Telegram puede omitir. */
    public static String notaDeVozSinMime() {
        return """
                {"update_id":10011,"message":{"message_id":11,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},"chat":{"id":%d,"type":"private"},
                 "voice":{"file_id":"voz","file_unique_id":"c","duration":3,"file_size":12000}}}"""
                .formatted(CHAT, CHAT);
    }

    public static String audio() {
        return """
                {"update_id":10012,"message":{"message_id":12,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},"chat":{"id":%d,"type":"private"},
                 "audio":{"file_id":"cancion","file_unique_id":"d","duration":3,"mime_type":"audio/mpeg",
                          "file_size":40000}}}"""
                .formatted(CHAT, CHAT);
    }

    /** Un video: nada de lo que trae lo entiende el bot. */
    public static String video() {
        return """
                {"update_id":10013,"message":{"message_id":13,"date":1791590401,
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},"chat":{"id":%d,"type":"private"},
                 "video":{"file_id":"video","file_unique_id":"e","width":1,"height":1,"duration":3}}}"""
                .formatted(CHAT, CHAT);
    }

    /** El toque de un boton del mensaje 50 del chat privado CHAT. */
    public static String boton(String datos) {
        return """
                {"update_id":10014,"callback_query":{"id":"cb-1","chat_instance":"x",
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},
                 "message":{"message_id":50,"date":1791590401,"chat":{"id":%d,"type":"private"}},
                 "data":"%s"}}"""
                .formatted(CHAT, CHAT, datos);
    }

    /** Un boton cuyo mensaje Telegram ya no entrega. */
    public static String botonSinMensaje() {
        return """
                {"update_id":10015,"callback_query":{"id":"cb-2","chat_instance":"x",
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},"data":"confirmar:x"}}"""
                .formatted(CHAT);
    }

    public static String botonSinId() {
        return """
                {"update_id":10016,"callback_query":{"chat_instance":"x",
                 "from":{"id":%d,"is_bot":false,"first_name":"Ana"},"data":"confirmar:x"}}"""
                .formatted(CHAT);
    }

    public static String sinUpdateId() {
        return """
                {"message":{"message_id":1,"date":1791590401,"chat":{"id":%d,"type":"private"},"text":"hola"}}"""
                .formatted(CHAT);
    }

    public static String mensajeSinChat() {
        return """
                {"update_id":10004,"message":{"message_id":3,"date":1791590401,"text":"hola"}}""";
    }
}
