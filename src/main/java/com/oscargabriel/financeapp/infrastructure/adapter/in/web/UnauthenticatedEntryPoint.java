package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.server.ServerAuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorResponse;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import tools.jackson.databind.ObjectMapper;

/**
 * El 401 de la cadena de seguridad no pasa por WebExceptionHandler: se resuelve dentro del filtro,
 * antes de que exista una excepcion que el handler global pueda ver. Esta clase existe para que el
 * cuerpo sea el mismo {"errors":[...]} que el resto del API.
 *
 * El motivo real —token ausente, expirado, mal firmado, de otro emisor— solo va al log. El de
 * Spring lo publicaria en la cabecera WWW-Authenticate como error_description, con el detalle
 * tecnico incluido; aqui esa cabecera se emite sin descripcion a proposito (OWASP A05).
 */
@Component
@Slf4j
public class UnauthenticatedEntryPoint implements ServerAuthenticationEntryPoint {

    private static final String DESCRIPCION = "Autenticacion requerida";

    private static final String RETO = "Bearer";

    private final ObjectMapper objectMapper;

    public UnauthenticatedEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> commence(ServerWebExchange exchange, AuthenticationException denegada) {
        return Mono.defer(() -> {
            log.warn("Peticion sin autenticacion valida a {}: {}",
                    exchange.getRequest().getPath().value(), denegada.getMessage());

            ServerHttpResponse respuesta = exchange.getResponse();
            respuesta.setStatusCode(HttpStatus.UNAUTHORIZED);
            respuesta.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            respuesta.getHeaders().set(HttpHeaders.WWW_AUTHENTICATE, RETO);

            byte[] cuerpo = objectMapper.writeValueAsString(new ErrorResponse(List.of(
                    ErrorDetail.of(ErrorCodes.UNAUTHENTICATED.getCode(), DESCRIPCION, "authorization")))
            ).getBytes(StandardCharsets.UTF_8);

            DataBuffer buffer = respuesta.bufferFactory().wrap(cuerpo);
            return respuesta.writeWith(Mono.just(buffer));
        });
    }
}
