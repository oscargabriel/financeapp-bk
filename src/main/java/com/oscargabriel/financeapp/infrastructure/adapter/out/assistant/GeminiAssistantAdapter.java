package com.oscargabriel.financeapp.infrastructure.adapter.out.assistant;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.model.AssistantContext;
import com.oscargabriel.financeapp.domain.model.AssistantDecision;
import com.oscargabriel.financeapp.domain.port.out.AssistantModelPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * generateContent de Gemini con function calling, una llamada por mensaje (design.md de FA-77). La
 * clave va en el header, nunca en la URL. El log de una falla dice el status y el tipo, nunca la
 * clave, el prompt, el texto del usuario ni el cuerpo que devolvio Google.
 *
 * WebClient.builder() y no el bean: en Boot 4 el builder autoconfigurado vive en
 * spring-boot-starter-webclient, y para mandar un Map y leer un String no hace falta.
 */
@Component
@ConditionalOnProperty(name = "asistente.proveedor", havingValue = "gemini")
@Slf4j
public class GeminiAssistantAdapter implements AssistantModelPort {

    private static final String NO_DISPONIBLE = "El asistente no esta disponible en este momento; intenta de nuevo";

    private final WebClient webClient;
    private final String model;
    private final Duration timeout;
    private final ObjectMapper json;
    private final String instrucciones;
    private final JsonNode funciones;

    public GeminiAssistantAdapter(
            @Value("${asistente.gemini.api-key}") String apiKey,
            @Value("${asistente.gemini.model}") String model,
            @Value("${asistente.gemini.base-url}") String baseUrl,
            @Value("${asistente.gemini.timeout}") Duration timeout,
            ObjectMapper json) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader("x-goog-api-key", apiKey)
                .build();
        this.model = model;
        this.timeout = timeout;
        this.json = json;
        this.instrucciones = leer("asistente/instrucciones.txt");
        this.funciones = json.readTree(leer("asistente/funciones.json"));
    }

    @Override
    public Mono<AssistantDecision> interpretar(String texto, AssistantContext contexto) {
        return Mono.defer(() -> webClient.post()
                        .uri("/v1beta/models/{model}:generateContent", model)
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(cuerpo(texto, contexto))
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(timeout)
                        .map(this::decision))
                .onErrorMap(e -> !(e instanceof BadRequestException), GeminiAssistantAdapter::noDisponible);
    }

    private Map<String, Object> cuerpo(String texto, AssistantContext contexto) {
        return Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruccion(contexto)))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", texto)))),
                "tools", List.of(Map.of("functionDeclarations", funciones)),
                "toolConfig", Map.of("functionCallingConfig", Map.of("mode", "AUTO")));
    }

    private String instruccion(AssistantContext contexto) {
        return instrucciones
                .replace("{hoy}", contexto.hoy().toString())
                .replace("{cuentas}", lista(contexto.cuentas().stream()))
                .replace("{categorias}", lista(contexto.categorias().stream()
                        .map(c -> c.nombre() + ": " + c.ambito().name())));
    }

    private static String lista(java.util.stream.Stream<String> nombres) {
        String texto = nombres.map(n -> "- " + n).collect(Collectors.joining("\n"));
        return texto.isEmpty() ? "(ninguna)" : texto;
    }

    /**
     * La primera parte con functionCall decide. Sin candidatos (Gemini bloqueo el mensaje) o sin
     * funcion, no se hace nada: el texto que haya escrito el modelo no se usa.
     */
    private AssistantDecision decision(String respuesta) {
        JsonNode partes = json.readTree(respuesta).path("candidates").path(0).path("content").path("parts");
        return partes.valueStream()
                .map(parte -> parte.path("functionCall"))
                .filter(llamada -> !llamada.isMissingNode())
                .findFirst()
                .map(llamada -> FuncionesDelAsistente.decision(llamada.path("name").asString(), llamada.path("args")))
                .orElseGet(AssistantDecision.SinFuncion::new);
    }

    private static BadRequestException noDisponible(Throwable e) {
        if (e instanceof WebClientResponseException respuesta) {
            log.warn("Gemini respondio status={}", respuesta.getStatusCode().value());
        } else {
            log.warn("Gemini fallo: {}", e.getClass().getSimpleName());
        }
        return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR, NO_DISPONIBLE,
                "server", e);
    }

    private static String leer(String ruta) {
        try (InputStream entrada = new ClassPathResource(ruta).getInputStream()) {
            return new String(entrada.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + ruta, e);
        }
    }
}
