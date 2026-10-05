package <PACKAGE>.exceptions;

import lombok.Getter;

/**
 * Enum de códigos de error para respuestas estructuradas.
 *
 * CÓMO USAR ESTE ARCHIVO:
 * - Los códigos de abajo son un punto de partida, no una lista definitiva.
 * - Agrega, elimina o renombra libremente según las necesidades del dominio.
 * - Criterio para nombrar: SCREAMING_SNAKE_CASE, describe la CATEGORÍA del error
 *   (no el mensaje ni el detalle técnico).
 *
 * CATEGORÍAS RECOMENDADAS (mantén al menos INTERNAL_SERVER_ERROR y VALIDATION_ERROR):
 *   - Validación de entrada   → VALIDATION_ERROR, INVALID_ARGUMENT, INVALID_NUMBER_FORMAT
 *   - Datos                   → DATA_EMPTY, DATA_NOT_FOUND, DUPLICATE_ENTRY
 *   - Sistema / infraestructura → INTERNAL_SERVER_ERROR, ENCRYPTION_ERROR, SERIALIZATION_ERROR
 *   - Servicios externos       → agrega según tu stack: ORACLE_UNAVAILABLE, SERVICEBUS_UNAVAILABLE, etc.
 *   - Seguridad                → UNAUTHORIZED, FORBIDDEN, INVALID_JWT_TOKEN (si aplica JWT)
 *
 * FLEXIBILIDAD POR PROYECTO:
 *   Un microservicio de pagos puede necesitar: PAYMENT_DECLINED, INSUFFICIENT_FUNDS, CARD_EXPIRED.
 *   Un microservicio de archivos puede necesitar: FILE_TOO_LARGE, UNSUPPORTED_FORMAT.
 *   El enum vive en domain/ así que no importa Spring — puedes adaptarlo sin restricciones.
 */
@Getter
public enum ErrorCodes {

  // ── Validación de entrada ──────────────────────────────────────────────
  VALIDATION_ERROR("VALIDATION_ERROR"),
  INVALID_ARGUMENT("INVALID_ARGUMENT"),
  INVALID_NUMBER_FORMAT("INVALID_NUMBER_FORMAT"),
  INVALID_REQUEST_TYPE("INVALID_REQUEST_TYPE"),
  JSON_PARSING_ERROR("JSON_PARSING_ERROR"),

  // ── Datos ──────────────────────────────────────────────────────────────
  DATA_EMPTY("DATA_EMPTY"),
  DATA_NOT_FOUND("DATA_NOT_FOUND"),
  DUPLICATE_ENTRY("DUPLICATE_ENTRY"),

  // ── Sistema / infraestructura ─────────────────────────────────────────
  SERIALIZATION_ERROR("SERIALIZATION_ERROR"),
  ENCRYPTION_ERROR("ENCRYPTION_ERROR"),
  DECRYPTION_ERROR("DECRYPTION_ERROR"),
  INTERNAL_SERVER_ERROR("INTERNAL_SERVER_ERROR"),
  SERVICE_UNAVAILABLE("SERVICE_UNAVAILABLE"),
  EXTERNAL_SERVICE_ERROR("EXTERNAL_SERVICE_ERROR"),

  // ── Seguridad ──────────────────────────────────────────────────────────
  UNAUTHORIZED("UNAUTHORIZED"),
  FORBIDDEN("FORBIDDEN");

  // ── Códigos específicos del dominio ────────────────────────────────────
  // Agrega aquí los que necesite tu proyecto. Ejemplo:
  // ORACLE_UNAVAILABLE("ORACLE_UNAVAILABLE"),
  // SERVICEBUS_UNAVAILABLE("SERVICEBUS_UNAVAILABLE"),
  // PAYMENT_DECLINED("PAYMENT_DECLINED"),

  private final String code;

  ErrorCodes(String code) {
    this.code = code;
  }
}
