package com.oscargabriel.financeapp.infrastructure.adapter.out.exchangerate;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.port.out.ExchangeRateProviderPort;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * El endpoint gratuito de ExchangeRate-API, latest/USD (FA-30, FA-120). La URL sale de configuracion,
 * nunca de la peticion. El log de una falla dice el status o el tipo, nunca la URL ni el cuerpo.
 *
 * WebClient.builder() y no el bean, por lo mismo que GeminiAssistantAdapter.
 */
@Component
@ConditionalOnProperty(name = "tasas.proveedor", havingValue = "exchangerate-api")
@Slf4j
public class ExchangeRateApiAdapter implements ExchangeRateProviderPort {

    private static final String NO_DISPONIBLE = "El proveedor de tasas de cambio no esta disponible";

    /** NUMERIC(20,10) de exchange_rates: diez digitos enteros como mucho. */
    private static final BigDecimal TOPE = BigDecimal.TEN.pow(10);

    private final WebClient webClient;
    private final String url;
    private final Duration timeout;
    private final ObjectMapper json;

    public ExchangeRateApiAdapter(
            @Value("${tasas.exchangerate-api.url}") String url,
            @Value("${tasas.exchangerate-api.timeout}") Duration timeout,
            ObjectMapper json) {
        this.webClient = WebClient.builder().build();
        this.url = url;
        this.timeout = timeout;
        this.json = json;
    }

    @Override
    public Mono<Map<String, BigDecimal>> latestFromUsd() {
        return Mono.defer(() -> webClient.get()
                        .uri(url)
                        .retrieve()
                        .bodyToMono(String.class)
                        .timeout(timeout)
                        .map(this::tasas))
                .onErrorMap(e -> !(e instanceof BadRequestException), ExchangeRateApiAdapter::noDisponible);
    }

    /** Solo una respuesta exitosa con base USD; de ella, las tasas numericas en (0, 10^10). */
    private Map<String, BigDecimal> tasas(String respuesta) {
        JsonNode cuerpo = json.readTree(respuesta);
        if (!"success".equals(cuerpo.path("result").asString(""))
                || !"USD".equals(cuerpo.path("base_code").asString(""))) {
            log.warn("ExchangeRate-API respondio sin exito o con otra moneda base");
            throw new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR, NO_DISPONIBLE,
                    "server");
        }
        return cuerpo.path("rates").properties().stream()
                .filter(tasa -> tasa.getValue().isNumber())
                .filter(tasa -> tasa.getValue().decimalValue().signum() > 0
                        && tasa.getValue().decimalValue().compareTo(TOPE) < 0)
                .collect(Collectors.toMap(Map.Entry::getKey, tasa -> tasa.getValue().decimalValue()));
    }

    private static BadRequestException noDisponible(Throwable e) {
        if (e instanceof WebClientResponseException respuesta) {
            log.warn("ExchangeRate-API respondio status={}", respuesta.getStatusCode().value());
        } else {
            log.warn("ExchangeRate-API fallo: {}", e.getClass().getSimpleName());
        }
        return new BadRequestException(HttpStatus.BAD_GATEWAY, ErrorCodes.EXTERNAL_SERVICE_ERROR, NO_DISPONIBLE,
                "server", e);
    }
}
