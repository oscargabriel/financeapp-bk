package com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web.dto;

import java.util.Optional;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oscargabriel.financeapp.telegram.domain.model.MensajeDeTelegram;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * Un update de la Bot API, con solo lo que el bot usa: Telegram manda mucho mas, y el resto se
 * ignora. Sin message (un mensaje editado, un callback) el update es valido y no se atiende.
 * from, chat.type y contact (FIN-60) no se validan: si faltan, el caso de uso lo trata como un mensaje
 * que no vincula, sin un 4xx que haria a Telegram reintentar.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(

        @JsonProperty("update_id")
        Long updateId,

        @Valid
        Message message) {

    private static final String PRIVADO = "private";

    public Optional<MensajeDeTelegram> mensaje() {
        return Optional.ofNullable(message).map(m -> new MensajeDeTelegram(
                m.chat().id(),
                PRIVADO.equals(m.chat().type()),
                m.from() == null ? null : m.from().id(),
                m.contact() == null ? null : new MensajeDeTelegram.Contacto(m.contact().userId(), m.contact().phoneNumber())));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(

            @NotNull(message = "El mensaje no trae el chat")
            @Valid
            Chat chat,

            From from,

            Contact contact) {
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
}
