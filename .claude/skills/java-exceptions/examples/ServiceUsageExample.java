package <BASE_PACKAGE>.application.service;

// This file shows the four patterns for throwing BadRequestException from reactive pipelines.
// It is NOT a deployable class — it's a reference showing how to use the exception structure
// correctly in Spring WebFlux (Mono/Flux) service methods.

import <BASE_PACKAGE>.domain.exceptions.BadRequestException;
import <BASE_PACKAGE>.domain.exceptions.ErrorCodes;
import <BASE_PACKAGE>.domain.exceptions.responses.ErrorDetail;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;

public class ServiceUsageExample {

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern A: Wrapping a technical error with onErrorMap
  //
  // Use when a Mono.fromCallable or I/O call can throw a checked/runtime exception.
  // Always pass the original exception as 'cause' — it's needed for debugging.
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<String> encrypt(String data) {
    return Mono.fromCallable(() -> performEncryption(data))
        .subscribeOn(Schedulers.boundedElastic())
        .onErrorMap(e -> new BadRequestException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            ErrorCodes.ENCRYPTION_ERROR,
            "Error de encriptación",
            "data",
            e));  // <-- pass 'e' as cause so the stack trace is preserved in logs
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern B: Empty result as a domain error with switchIfEmpty
  //
  // Use when "no record found" is a business error (e.g., DB lookup by ID).
  // Mono.defer() ensures the exception is created lazily, only if switchIfEmpty fires.
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<String> findById(String id) {
    return repository.findById(id)
        .switchIfEmpty(Mono.defer(() -> Mono.error(
            new BadRequestException(
                HttpStatus.NOT_FOUND,
                ErrorCodes.DATA_NOT_FOUND,
                "No se encontró el registro con id: " + id,
                "id"))));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern C: Conditional error after retrieving a result
  //
  // Use when you need to validate the content of a successful result
  // (e.g., list is empty, value is out of range).
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<List<String>> findByPeriod(String period) {
    return repository.findByPeriod(period)
        .flatMap(results -> results.isEmpty()
            ? Mono.error(new BadRequestException(
                HttpStatus.BAD_REQUEST,
                ErrorCodes.DATA_EMPTY,
                "No hay datos para el periodo: " + period,
                "periodo"))
            : Mono.just(results));
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern D: External service error with onErrorMap + predicate
  //
  // Use when checking external dependencies (DB, message bus, etc.).
  // The predicate `e -> !(e instanceof BadRequestException)` prevents double-wrapping
  // if a BadRequestException has already been thrown deeper in the chain.
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<Boolean> checkOracle() {
    return performOracleCheck()
        .onErrorMap(
            e -> !(e instanceof BadRequestException),
            e -> {
              log.warn("Oracle check failed: {}", e.getMessage());
              return new BadRequestException(
                  HttpStatus.SERVICE_UNAVAILABLE,
                  ErrorCodes.ORACLE_UNAVAILABLE,
                  "Oracle no disponible",
                  "oracle");
              // Note: no cause passed here — this is an availability signal,
              // not a wrapped exception. The original error is already logged.
            });
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern E: Explicit validation before starting the pipeline
  //
  // Use for synchronous input validation before entering the reactive chain.
  // This is the only valid use of `throw` — it's outside a reactive operator.
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<String> process(String input) {
    if (input == null || input.isBlank()) {
      throw new BadRequestException(
          HttpStatus.BAD_REQUEST,
          ErrorCodes.VALIDATION_ERROR,
          "El campo input es requerido",
          "input");
    }
    return Mono.just(input.trim());
  }

  // ─────────────────────────────────────────────────────────────────────────
  // Pattern F: Batch validation — report all failures at once
  //
  // Use when validating multiple fields and you want to surface all errors
  // in a single response rather than one per request.
  // ─────────────────────────────────────────────────────────────────────────
  public Mono<Void> validateRequest(Request request) {
    List<ErrorDetail> errors = new ArrayList<>();

    if (request.nombre() == null || request.nombre().isBlank()) {
      errors.add(ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
          "El nombre es requerido", "nombre"));
    }
    if (request.fecha() == null) {
      errors.add(ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
          "La fecha es requerida", "fecha"));
    }
    if (!errors.isEmpty()) {
      return Mono.error(new BadRequestException(HttpStatus.BAD_REQUEST, errors));
    }
    return Mono.empty();
  }
}
