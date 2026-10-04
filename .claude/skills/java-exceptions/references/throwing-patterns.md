# Cómo lanzar errores — imperativo y reactivo

Léelo al escribir el código que produce un error de negocio, o al revisar la propagación de
errores en un pipeline reactivo.

## Constructores de `BadRequestException`

**Un error — el caso más común:**

```java
throw new BadRequestException(
    HttpStatus.BAD_REQUEST,
    ErrorCodes.DATA_EMPTY,
    "No se encontraron registros para el periodo solicitado",
    "periodo"
);
```

**Un error envolviendo una causa técnica** — al capturar una excepción de bajo nivel, pasa la
causa: sin ella la raíz desaparece del stack trace y el error se vuelve muy caro de diagnosticar.

```java
throw new BadRequestException(
    HttpStatus.INTERNAL_SERVER_ERROR,
    ErrorCodes.ENCRYPTION_ERROR,
    "Error de encriptación",
    "encrypt",
    originalException   // Throwable cause
);
```

**Varios errores (validación por lote):**

```java
List<ErrorDetail> errors = new ArrayList<>();
if (request.getNombre() == null) {
    errors.add(ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
        "El nombre es requerido", "nombre"));
}
if (request.getFecha() == null) {
    errors.add(ErrorDetail.of(ErrorCodes.VALIDATION_ERROR.getCode(),
        "La fecha es requerida", "fecha"));
}
if (!errors.isEmpty()) {
    throw new BadRequestException(HttpStatus.BAD_REQUEST, errors);
}
```

## Guía de HTTP status

| Situación | Status |
|---|---|
| Input inválido, campo requerido | `BAD_REQUEST` (400) |
| Recurso no encontrado | `NOT_FOUND` (404) |
| Sin permisos | `FORBIDDEN` (403) |
| No autenticado | `UNAUTHORIZED` (401) |
| Conflicto (duplicado) | `CONFLICT` (409) |
| Servicio externo no disponible | `SERVICE_UNAVAILABLE` (503) |
| Error interno técnico | `INTERNAL_SERVER_ERROR` (500) |

## Desde pipelines reactivos

En WebFlux las excepciones no se lanzan con `throw` dentro de la cadena: se propagan como señales.
Cuatro patrones cubren casi todo.

**A — Envolver un error técnico con `onErrorMap`.** Para `Mono.fromCallable` u operaciones de I/O
que pueden lanzar. La causa original se preserva y queda visible en los logs.

```java
return Mono.fromCallable(() -> cipher.doFinal(data))
    .subscribeOn(Schedulers.boundedElastic())
    .onErrorMap(e -> new BadRequestException(
        HttpStatus.INTERNAL_SERVER_ERROR,
        ErrorCodes.ENCRYPTION_ERROR,
        "Error de encriptación", "encrypt", e));   // 'e' como causa
```

**B — Resultado vacío como error de negocio con `switchIfEmpty`.** Cuando la ausencia de datos es
un error (registro no encontrado).

```java
return repository.findById(id)
    .switchIfEmpty(Mono.defer(() -> Mono.error(
        new BadRequestException(
            HttpStatus.NOT_FOUND,
            ErrorCodes.DATA_NOT_FOUND,
            "No se encontró el registro con id: " + id,
            "id"))));
```

`Mono.defer` evita construir la excepción en cada ensamblado del pipeline, aunque nunca se use.

**C — Error condicional con `flatMap`.** Cuando hay que validar el resultado antes de seguir.

```java
return repository.findByPeriod(period)
    .flatMap(result -> result.isEmpty()
        ? Mono.error(new BadRequestException(
            HttpStatus.BAD_REQUEST,
            ErrorCodes.DATA_EMPTY,
            "No hay datos para el periodo: " + period,
            "periodo"))
        : Mono.just(result));
```

**D — `onErrorMap` con predicado, para no re-envolver.** Remapea solo lo que aún no es una
excepción de dominio; sin el predicado, un `BadRequestException` correcto se envolvería en otro y
perdería su status.

```java
return checkOracle()
    .onErrorMap(e -> !(e instanceof BadRequestException), e -> {
        log.warn("Oracle no disponible: {}", e.getMessage());
        return new BadRequestException(
            HttpStatus.SERVICE_UNAVAILABLE,
            ErrorCodes.ORACLE_UNAVAILABLE,
            "Oracle no disponible", "oracle");
    });
```

Ejemplos completos y ejecutables de los cuatro patrones en `examples/ServiceUsageExample.java`.

## Crear una excepción propia

`BadRequestException` es la plantilla. El principio de diseño: **la excepción carga su propio HTTP
status y su payload de error**, así el handler queda delgado y no necesita entender lógica de
negocio.

```java
package <domain.package>.exceptions;

import <domain.package>.exceptions.responses.ErrorDetail;
import <domain.package>.exceptions.responses.ErrorResponse;
import lombok.Getter;
import org.springframework.http.HttpStatus;
import java.io.Serial;
import java.util.List;

@Getter
public class NotFoundException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    private final HttpStatus httpStatus = HttpStatus.NOT_FOUND;
    private final ErrorResponse errorResponse;

    public NotFoundException(ErrorCodes errorCode, String description, String field) {
        super(description);
        this.errorResponse = new ErrorResponse(List.of(
            ErrorDetail.of(errorCode.getCode(), description, field)));
    }
}
```

Y se registra en el `WebExceptionHandler` de `infrastructure/`:

```java
case NotFoundException nfe ->
    respond(nfe.getErrorResponse(), nfe.getHttpStatus());
```

## Extender el `WebExceptionHandler`

Usa un `switch` pattern expression (Java 21+). Los casos nuevos se agregan **antes** del
`default`, que debe quedar último.

```java
// Inline, para mapeos simples:
case NotFoundException nfe ->
    respond(new ErrorResponse(List.of(
        ErrorDetail.of(ErrorCodes.DATA_NOT_FOUND.getCode(), nfe.getMessage(), "id"))),
        HttpStatus.NOT_FOUND);

// Método aparte, si el mapeo es complejo:
case DuplicateKeyException dke ->
    respond(fromDuplicateKey(dke), HttpStatus.CONFLICT);
```

Ya cubiertos por la plantilla, no necesitan caso nuevo: `WebExchangeBindException` (de `@Valid`),
`NumberFormatException`, `IllegalArgumentException` y `ServerWebInputException` (body JSON mal
formado).
