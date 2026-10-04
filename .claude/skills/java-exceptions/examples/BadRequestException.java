package <PACKAGE>.exceptions;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import <PACKAGE>.exceptions.responses.ErrorDetail;
import <PACKAGE>.exceptions.responses.ErrorResponse;
import org.springframework.http.HttpStatus;

import java.io.Serial;
import java.util.List;

@Getter
public class BadRequestException extends RuntimeException {

  @Serial
  private static final long serialVersionUID = 1L;

  @JsonProperty("httpStatus")
  private final HttpStatus httpStatus;

  @JsonProperty("errorResponse")
  private final ErrorResponse errorResponse;

  /** Single error — standard case. */
  public BadRequestException(HttpStatus httpStatus, ErrorCodes errorCode,
      String description, String field) {
    super(description);
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(List.of(
        ErrorDetail.of(errorCode.getCode(), description, field)));
  }

  /**
   * Single error wrapping a root cause.
   * Use this when catching a low-level exception — preserves the stack trace for debugging.
   */
  public BadRequestException(HttpStatus httpStatus, ErrorCodes errorCode,
      String description, String field, Throwable cause) {
    super(description, cause);
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(List.of(
        ErrorDetail.of(errorCode.getCode(), description, field)));
  }

  /** Multiple errors — useful for batch validation that reports all failures at once. */
  public BadRequestException(HttpStatus httpStatus, List<ErrorDetail> errorDetails) {
    super(errorDetails.isEmpty() ? "Bad Request" : errorDetails.get(0).getDescription());
    this.httpStatus = httpStatus;
    this.errorResponse = new ErrorResponse(errorDetails);
  }
}
