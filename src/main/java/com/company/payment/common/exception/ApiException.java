package com.company.payment.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

import java.util.List;

/**
 * The one exception for "this request can't be processed": HTTP status + stable error code
 * (+ field errors for validation). GlobalExceptionHandler turns it into an ErrorResponse.
 */
@Getter
public class ApiException extends RuntimeException {

    /** One invalid field, e.g. ("card.pan", "is required for cardType CARD"). */
    public record FieldViolation(String field, String message) {
    }

    private final HttpStatus status;
    private final String code;
    private final List<FieldViolation> violations;

    private ApiException(HttpStatus status, String code, String message, List<FieldViolation> violations) {
        super(message);
        this.status = status;
        this.code = code;
        this.violations = List.copyOf(violations);
    }

    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message, List.of());
    }

    /** 400 VALIDATION_ERROR with field errors (same shape as Bean Validation errors). */
    public static ApiException validation(List<FieldViolation> violations) {
        return new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", violations);
    }

    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message, List.of());
    }

    public static ApiException unprocessable(String code, String message) {
        return new ApiException(HttpStatus.valueOf(422), code, message, List.of());
    }
}
