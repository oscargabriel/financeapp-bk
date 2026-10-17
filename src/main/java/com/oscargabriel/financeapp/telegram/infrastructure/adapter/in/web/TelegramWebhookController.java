package com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.telegram.domain.port.in.AtenderUpdatePort;
import com.oscargabriel.financeapp.telegram.infrastructure.adapter.in.web.dto.TelegramUpdateRequest;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;

/**
 * Los updates que Telegram entrega por webhook (FIN-89). Llegan aqui ya autenticados con el secreto
 * por su propia cadena de SecurityConfig. Responde 200 aunque el bot no logre contestar: Telegram
 * reintenta todo update que no recibe 2xx.
 */
@RestController
@RequestMapping("/telegram")
@AllArgsConstructor
public class TelegramWebhookController {

    private final AtenderUpdatePort atenderUpdate;

    @PostMapping("/webhook")
    public Mono<Void> webhook(@Valid @RequestBody TelegramUpdateRequest update) {
        return Mono.justOrEmpty(update.evento())
                .flatMap(evento -> atenderUpdate.atender(update.updateId(), evento));
    }
}
