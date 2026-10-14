package com.oscargabriel.financeapp.ia.infrastructure.adapter.out.assistant;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.oscargabriel.financeapp.compartido.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.compartido.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.ia.domain.model.AssistantContext;
import com.oscargabriel.financeapp.ia.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.ia.domain.port.out.AssistantModelPort;

import lombok.AllArgsConstructor;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * El modelo de mentira con el que bruno/ ejerce todo lo que va despues de Gemini (design.md de FA-77,
 * decision 7). Entiende "<funcion> <json de argumentos>", una llamada por linea como las varias partes functionCall de
 * Gemini (FIN-58); el json puede seguir en las lineas siguientes. Un texto que no empieza por una funcion es una
 * respuesta sin funcion. Con el perfil prod no existe aunque se pida, y entonces no hay adapter y no arranca.
 */
@Component
@Profile("!prod")
@ConditionalOnProperty(name = "asistente.proveedor", havingValue = "stub")
@AllArgsConstructor
public class StubAssistantAdapter implements AssistantModelPort {

    private final ObjectMapper json;

    @Override
    public Mono<AssistantDecision> interpretar(String texto, AssistantContext contexto) {
        return Mono.fromCallable(() -> FuncionesDelAsistente.decision(llamadas(texto))).onErrorMap(e -> new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR,
                "El asistente no esta disponible en este momento; intenta de nuevo", "server", e));
    }

    /** Una linea que empieza por una funcion abre una llamada; las demas siguen el json de la anterior. */
    private List<FuncionesDelAsistente.Llamada> llamadas(String texto) {
        List<String[]> abiertas = new ArrayList<>();
        for (String linea : texto.strip().split("\\R")) {
            String[] partes = linea.strip().split("\\s+", 2);
            if (FuncionesDelAsistente.existe(partes[0])) {
                abiertas.add(new String[] {partes[0], partes.length > 1 ? partes[1] : ""});
            } else if (abiertas.isEmpty()) {
                return List.of();
            } else {
                String[] ultima = abiertas.getLast();
                ultima[1] = ultima[1] + "\n" + linea;
            }
        }
        return abiertas.stream()
                .map(l -> new FuncionesDelAsistente.Llamada(l[0],
                        l[1].isBlank() ? json.createObjectNode() : json.readTree(l[1])))
                .toList();
    }
}
