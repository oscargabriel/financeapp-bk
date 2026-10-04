// LOCATION: <BASE_PACKAGE>.infrastructure.adapter.in.web
// This class depends on Spring WebFlux types — it must live in infrastructure/, NOT in domain/.
package <BASE_PACKAGE>.infrastructure.adapter.in.web;

import lombok.extern.slf4j.Slf4j;
import <BASE_PACKAGE>.domain.exceptions.BadRequestException;
import <BASE_PACKAGE>.domain.exceptions.ErrorCodes;
import <BASE_PACKAGE>.domain.exceptions.responses.ErrorDetail;
import <BASE_PACKAGE>.domain.exceptions.responses.ErrorResponse;
import org.springframework.boot.autoconfigure.web.WebProperties;
import org.springframework.boot.webflux.autoconfigure.error.AbstractErrorWebExceptionHandler;
import org.springframework.boot.webflux.error.ErrorAttributes;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.stereotype.Component;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.*;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Map;

/**
 * Global exception handler for Spring WebFlux.
 *
 * Extends AbstractErrorWebExceptionHandler — the reactive alternative to @ControllerAdvice.
 * @Order(HIGHEST_PRECEDENCE) ensures this handler intercepts before Spring's default handler.
 *
 * To add a new exception type:
 *   1. Add a case in the switch inside renderErrorResponse()
 *   2. Place new cases BEFORE the default block
 */
@Component
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
    log.info("Handling exception: {} — {}", ex.getClass().getSimpleName(), ex.getMessage());

    return switch (ex) {
      case BadRequestException bre ->
          respond(bre.getErrorResponse(), bre.getHttpStatus());

      case WebExchangeBindException bindEx -> {
        List<ErrorDetail> errors = bindEx.getBindingResult().getAllErrors().stream()
            .map(err -> err instanceof FieldError fe
                ? ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), fe.getDefaultMessage(), fe.getField())
                : ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(), err.getDefaultMessage(), "unknown"))
            .toList();
        yield respond(new ErrorResponse(errors), HttpStatus.BAD_REQUEST);
      }

      case NumberFormatException nfe -> {
        String desc = "Formato numérico inválido" + (nfe.getMessage() != null ? ": " + nfe.getMessage() : "");
        yield respond(new ErrorResponse(List.of(
            ErrorDetail.of(ErrorCodes.INVALID_NUMBER_FORMAT.getCode(), desc, "parameter"))),
            HttpStatus.BAD_REQUEST);
      }

      case IllegalArgumentException iae ->
          respond(new ErrorResponse(List.of(
              ErrorDetail.of(ErrorCodes.INVALID_ARGUMENT.getCode(), iae.getMessage(), "parameter"))),
              HttpStatus.BAD_REQUEST);

      case ServerWebInputException swie -> {
        String msg = swie.getReason() != null && !swie.getReason().isBlank()
            ? swie.getReason() : "Invalid request body";
        yield respond(new ErrorResponse(List.of(
            ErrorDetail.of(ErrorCodes.JSON_PARSING_ERROR.getCode(), msg, "body"))),
            HttpStatus.BAD_REQUEST);
      }

      // Add new exception cases above this default block
      default -> {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        yield respond(new ErrorResponse(List.of(
            ErrorDetail.of(ErrorCodes.INTERNAL_SERVER_ERROR.getCode(),
                "An unexpected error occurred", "server"))),
            HttpStatus.INTERNAL_SERVER_ERROR);
      }
    };
  }

  private Mono<ServerResponse> respond(ErrorResponse errorResponse, HttpStatus status) {
    return Mono.fromCallable(() -> objectMapper.writeValueAsString(errorResponse))
        .subscribeOn(Schedulers.boundedElastic())
        .flatMap(body -> ServerResponse.status(status)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(body)))
        .onErrorResume(e -> ServerResponse.status(HttpStatus.INTERNAL_SERVER_ERROR)
            .contentType(MediaType.APPLICATION_JSON)
            .body(BodyInserters.fromValue(Map.of(
                "error", "Error serializing error response",
                "message", e.getMessage()))));
  }
}
