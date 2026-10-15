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
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TelegramUpdateRequest(

        @JsonProperty("update_id")
        Long updateId,

        @Valid
        Message message) {

    public Optional<MensajeDeTelegram> mensaje() {
        return Optional.ofNullable(message).map(m -> new MensajeDeTelegram(m.chat().id()));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(

            @NotNull(message = "El mensaje no trae el chat")
            @Valid
            Chat chat) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(

            @NotNull(message = "El chat no trae su id")
            Long id) {
    }
}
