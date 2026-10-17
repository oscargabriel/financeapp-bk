package com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.PartEvent;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.oscargabriel.financeapp.compartido.infrastructure.adapter.in.web.UsuarioDelToken;
import com.oscargabriel.financeapp.ia.domain.model.AssistantChannel;
import com.oscargabriel.financeapp.ia.domain.model.AssistantMessage;
import com.oscargabriel.financeapp.ia.domain.port.in.AssistantPort;
import com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web.dto.AssistantMessageRequest;
import com.oscargabriel.financeapp.ia.infrastructure.adapter.in.web.dto.AssistantMessageResponse;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * El mismo mensaje en JSON (solo texto) o en multipart (un archivo y un texto opcional, FIN-57). Los dos son el chat
 * de la web (FIN-112).
 */
@RestController
@RequestMapping("/assistant")
@AllArgsConstructor
public class AssistantController {

    private final AssistantPort assistant;

    @PostMapping(path = "/messages", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<AssistantMessageResponse> message(@AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AssistantMessageRequest mensaje) {
        return Mono.defer(() -> assistant.atender(UsuarioDelToken.de(jwt), AssistantChannel.WEB_CHAT,
                        AssistantMessage.deTexto(mensaje.data())))
                .map(AssistantMessageResponse::from);
    }

    /** Flux de PartEvent y no FilePart: el lector por defecto de FilePart escribe en disco las partes grandes. */
    @PostMapping(path = "/messages", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public Mono<AssistantMessageResponse> messageConArchivo(@AuthenticationPrincipal Jwt jwt,
            @RequestBody Flux<PartEvent> partes) {
        return LectorDeMensajeMultipart.leer(partes)
                .flatMap(mensaje -> assistant.atender(UsuarioDelToken.de(jwt), AssistantChannel.WEB_CHAT, mensaje))
                .map(AssistantMessageResponse::from);
    }
}
