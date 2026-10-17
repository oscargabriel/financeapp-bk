package com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web.dto;

import java.util.List;
import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oscargabriel.financeapp.telegram.domain.model.ArchivoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.BotonDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.EventoDeTelegram;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Un update de la Bot API, con solo lo que el bot usa: Telegram manda mucho mas, y el resto se
 * ignora. Sin message ni callback_query (un mensaje editado, un cambio de miembros) el update es valido
 * y no se atiende. update_id es obligatorio porque sin el no hay como saber si ya se atendio (FIN-61).
 * from, chat.type, contact y el mensaje de un boton no se validan: si faltan, el caso de uso lo trata
 * como un mensaje que no vincula o un boton sin chat, sin un 4xx que haria a Telegram reintentar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(

        @NotNull(message = "El update no trae su update_id")
        @JsonProperty("update_id")
        Long updateId,

        @Valid
        Message message,

        @Valid
        @JsonProperty("callback_query")
        CallbackQuery callbackQuery) {

    private static final String PRIVADO = "private";

    /** Telegram puede omitir el mime_type de una nota de voz, que siempre graba en Ogg. */
    private static final String MIME_DE_VOZ = "audio/ogg";

    /** Telegram no manda el mime de una foto: siempre la recomprime a JPEG. */
    private static final String MIME_DE_FOTO = "image/jpeg";

    public Optional<EventoDeTelegram> evento() {
        if (message != null) {
            return Optional.of(mensaje(message));
        }
        return Optional.ofNullable(callbackQuery).map(TelegramUpdateRequest::boton);
    }

    private static MensajeDeTelegram mensaje(Message m) {
        return new MensajeDeTelegram(
                m.chat().id(),
                PRIVADO.equals(m.chat().type()),
                m.from() == null ? null : m.from().id(),
                m.contact() == null ? null : new MensajeDeTelegram.Contacto(m.contact().userId(), m.contact().phoneNumber()),
                m.text() != null ? m.text() : m.caption(),
                archivo(m));
    }

    /** La foto en su tamano mas grande, que Telegram pone al final; si no, la nota de voz; si no, el audio. */
    private static ArchivoDeTelegram archivo(Message m) {
        if (m.photo() != null && !m.photo().isEmpty()) {
            PhotoSize foto = m.photo().getLast();
            return new ArchivoDeTelegram(foto.fileId(), MIME_DE_FOTO, foto.fileSize());
        }
        if (m.voice() != null) {
            String mime = m.voice().mimeType() == null ? MIME_DE_VOZ : m.voice().mimeType();
            return new ArchivoDeTelegram(m.voice().fileId(), mime, m.voice().fileSize());
        }
        if (m.audio() != null) {
            return new ArchivoDeTelegram(m.audio().fileId(), m.audio().mimeType(), m.audio().fileSize());
        }
        return null;
    }

    private static BotonDeTelegram boton(CallbackQuery c) {
        Chat chat = c.message() == null ? null : c.message().chat();
        return new BotonDeTelegram(
                c.id(),
                chat == null ? null : chat.id(),
                chat != null && PRIVADO.equals(chat.type()),
                c.message() == null ? null : c.message().messageId(),
                c.data());
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(

            @NotNull(message = "El mensaje no trae el chat")
            @Valid
            Chat chat,

            From from,

            Contact contact,

            String text,

            String caption,

            List<PhotoSize> photo,

            Media voice,

            Media audio) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(

            @NotNull(message = "El chat no trae su id")
            Long id,

            String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record From(Long id) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Contact(

            @JsonProperty("phone_number")
            String phoneNumber,

            @JsonProperty("user_id")
            Long userId) {

        /** Los telefonos no van a los logs, tampoco si el codec loguea el cuerpo decodificado. */
        @Override
        public String toString() {
            return "Contact[userId=" + userId + ", phoneNumber=***]";
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PhotoSize(

            @JsonProperty("file_id")
            String fileId,

            @JsonProperty("file_size")
            Long fileSize) {
    }

    /** Una nota de voz o un audio. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Media(

            @JsonProperty("file_id")
            String fileId,

            @JsonProperty("file_size")
            Long fileSize,

            @JsonProperty("mime_type")
            String mimeType) {
    }

    /** El message de un boton puede ser uno que Telegram ya no entrega completo: no se valida. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(

            @NotNull(message = "El boton no trae su id")
            String id,

            CallbackMessage message,

            String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackMessage(

            @JsonProperty("message_id")
            Long messageId,

            Chat chat) {
    }
}
