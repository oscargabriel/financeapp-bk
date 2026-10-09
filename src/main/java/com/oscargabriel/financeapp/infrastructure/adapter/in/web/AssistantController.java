package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.time.Clock;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.AssistantMessageRequest;
import com.oscargabriel.financeapp.infrastructure.adapter.in.web.dto.AssistantMessageResponse;

import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/assistant")
@AllArgsConstructor
public class AssistantController {

    private final AssistantPort assistant;
    private final Clock clock;

    @PostMapping("/messages")
    public Mono<AssistantMessageResponse> message(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssistantMessageRequest mensaje) {
        return Mono.defer(() -> assistant.atender(UsuarioDelToken.de(jwt), mensaje.data()))
                .map(r -> AssistantMessageResponse.from(r, clock.instant()));
    }
}
