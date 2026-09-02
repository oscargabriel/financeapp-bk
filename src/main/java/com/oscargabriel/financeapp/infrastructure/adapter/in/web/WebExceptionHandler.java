// Depende de tipos de WebFlux (RouterFunction, ServerResponse): vive en infrastructure, no en domain.
package com.oscargabriel.financeapp.infrastructure.adapter.in.web;

import java.util.List;
import java.util.Map;

import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.RequestPredicates;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebInputException;

import com.oscargabriel.financeapp.domain.exceptions.BadRequestException;
import com.oscargabriel.financeapp.domain.exceptions.ErrorCodes;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorDetail;
import com.oscargabriel.financeapp.domain.exceptions.responses.ErrorResponse;
import com.oscargabriel.financeapp.infrastructure.config.ResourcesWebPropertiesConfig;

import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

/**
 * Handler global de errores para WebFlux — la alternativa reactiva a @ControllerAdvice, que es
 * servlet-only. Todos los errores salen con el mismo JSON: {"errors":[{code, description, field}]}.
 *
 * Para agregar un tipo de excepcion: un case nuevo en el switch, SIEMPRE antes del default y
 * antes de su supertipo (el switch de patrones no admite un subtipo dominado).
 *
 * El @Import trae el bean WebProperties.Resources que exige el constructor: @WebFluxTest registra
 * este handler pero no las @Configuration del proyecto, y sin el import cada slice web tendria que
 * acordarse de importarlo.
 */
@Component
@Import(ResourcesWebPropertiesConfig.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
@Slf4j
public class WebExceptionHandler extends AbstractErrorWebExceptionHandler {

    private final ObjectMapper objectMapper;

    public WebExceptionHandler(ErrorAttributes errorAttributes,
            WebProperties.Resources resources,
            ApplicationContext applicationContext,
            ServerCodecConfigurer configurer,
            ObjectMapper objectMapper) {
        super(errorAttributes, resources, applicationContext);
        setMessageWriters(configurer.getWriters());
        this.objectMapper = objectMapper;
    }

    @Override
    protected RouterFunction<ServerResponse> getRoutingFunction(ErrorAttributes errorAttributes) {
        return RouterFunctions.route(RequestPredicates.all(), this::renderErrorResponse);
    }

    private Mono<ServerResponse> renderErrorResponse(ServerRequest request) {
        Throwable ex = getError(request);

        return switch (ex) {
            case BadRequestException bre -> respond(bre.getErrorResponse(), bre.getHttpStatus());

            case WebExchangeBindException bindEx -> {
                List<ErrorDetail> errors = bindEx.getBindingResult().getAllErrors().stream()
                        .map(err -> err instanceof FieldError fe
                                ? ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
                                        fe.getDefaultMessage(), fe.getField())
                                : ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
                                        err.getDefaultMessage(), "unknown"))
                        .toList();
                yield respond(new ErrorResponse(errors), HttpStatus.BAD_REQUEST);
            }

            case ServerWebInputException swie -> {
                String description = swie.getReason() != null && !swie.getReason().isBlank()
                        ? swie.getReason()
                        : "Cuerpo de la peticion invalido";
                yield respond(single(ErrorCodes.JSON_PARSING_ERROR, description, "body"),
                        HttpStatus.BAD_REQUEST);
            }

            case NumberFormatException nfe -> {
                log.warn("Formato numerico invalido: {}", nfe.getMessage());
                yield respond(single(ErrorCodes.INVALID_NUMBER_FORMAT,
                        "Formato numerico invalido", "parameter"), HttpStatus.BAD_REQUEST);
            }

            case IllegalArgumentException iae -> respond(
                    single(ErrorCodes.INVALID_ARGUMENT, iae.getMessage(), "parameter"),
                    HttpStatus.BAD_REQUEST);

            // Debe ir despues de ServerWebInputException, que es su subtipo.
            case ResponseStatusException rse -> {
                HttpStatusCode status = rse.getStatusCode();
                if (status.value() == HttpStatus.NOT_FOUND.value()) {
                    yield respond(single(ErrorCodes.NOT_FOUND, "Recurso no encontrado", "request"),
                            HttpStatus.NOT_FOUND);
                }
                log.warn("Peticion rechazada con status={}: {}", status.value(), rse.getReason());
                yield respond(single(ErrorCodes.VALIDATION_ERROR,
                        "La peticion no pudo ser procesada", "request"), HttpStatus.valueOf(status.value()));
            }

            // Agregar casos nuevos arriba de este default.
            default -> {
                log.error("Excepcion no manejada: {}", ex.getMessage(), ex);
                yield respond(single(ErrorCodes.INTERNAL_SERVER_ERROR,
                        "Ocurrio un error inesperado", "server"), HttpStatus.INTERNAL_SERVER_ERROR);
            }
        };
    }

    private static ErrorResponse single(ErrorCodes code, String description, String field) {
        return new ErrorResponse(List.of(ErrorDetail.of(code.getCode(), description, field)));
    }

    private Mono<ServerResponse> respond(ErrorResponse errorResponse, HttpStatus status) {
        return Mono.fromCallable(() -> objectMapper.writeValueAsString(errorResponse))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(body -> ServerResponse.status(status)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(BodyInserters.fromValue(body)))
                .onErrorResume(e -> {
                    log.error("Error serializando la respuesta de error", e);
                    return ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(BodyInserters.fromValue(
                                    Map.of("errors", List.of(Map.of(
                                            "code", ErrorCodes.INTERNAL_SERVER_ERROR.getCode(),
                                            "description", "Ocurrio un error inesperado",
                                            "field", "server")))));
                });
    }
}
