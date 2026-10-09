package com.oscargabriel.financeapp.infrastructure.adapter.out.assistant;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.AssistantContext;
import com.oscargabriel.financeapp.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.domain.port.out.AssistantModelPort;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El modelo de mentira con el que bruno/ ejerce todo lo que va despues de Gemini (design.md de FA-77,
 * decision 7). Entiende "<funcion> <json de argumentos>"; cualquier otro texto es una respuesta sin
 * funcion. Con el perfil prod no existe aunque se pida, y entonces no hay adapter y no arranca.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "asistente.proveedor", havingValue = "stub")
@AllArgsConstructor
public class StubAssistantAdapter implements AssistantModelPort {

    private final ObjectMapper json;

    @Override
    public Mono<AssistantDecision> interpretar(String texto, AssistantContext contexto) {
        return Mono.fromCallable(() -> {
            String[] partes = texto.strip().split("\\s+", 2);
            if (!FuncionesDelAsistente.existe(partes[0])) {
                return new AssistantDecision.SinFuncion();
            }
            JsonNode args = partes.length > 1 ? json.readTree(partes[1]) : json.createObjectNode();
            return FuncionesDelAsistente.decision(partes[0], args);
        }).onErrorMap(e -> new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "El asistente no esta disponible en este momento; intenta de nuevo", "server", e));
    }
}
