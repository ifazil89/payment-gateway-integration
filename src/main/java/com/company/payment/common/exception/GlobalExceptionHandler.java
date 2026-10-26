package com.company.payment.common.exception;

import com.company.payment.common.model.ErrorDetail;
import com.company.payment.common.model.ErrorResponse;
import com.company.payment.common.logging.LogFields;
import com.company.payment.ni.exception.NiException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

/**
 * Every error -> ErrorResponse {code, message, correlationId, traceId, timestamp, errors}
 * (common/model/ErrorResponse, documented in payment-api.yaml).
 *
 *   400 VALIDATION_ERROR / MALFORMED_REQUEST / MISSING_HEADER
 *   409, 422            ApiException (its own status and code)
 *   422 GATEWAY_REJECTED  N-Genius refused (outcome known)
 *   502 GATEWAY_ERROR     N-Genius server error
 *   504 GATEWAY_TIMEOUT   N-Genius did not answer: outcome UNKNOWN, retry with the same Idempotency-Key
 *   500 INTERNAL_ERROR
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErrorResponse> invalidBody(MethodArgumentNotValidException ex) {
        List<ErrorDetail> errors = new ArrayList<>();
        ex.getBindingResult().getFieldErrors().forEach(e -> errors.add(detail(e.getField(), e.getDefaultMessage())));
        ex.getBindingResult().getGlobalErrors().forEach(e -> errors.add(detail(e.getObjectName(), e.getDefaultMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", errors);
    }

    /** Spring MVC method validation (constraints on headers / parameters, and on the body in some setups). */
    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErrorResponse> invalidParameter(HandlerMethodValidationException ex) {
        List<ErrorDetail> errors = new ArrayList<>();
        ex.getParameterValidationResults().forEach(result -> {
            if (result instanceof ParameterErrors parameterErrors) {
                parameterErrors.getFieldErrors().forEach(e -> errors.add(detail(e.getField(), e.getDefaultMessage())));
            } else {
                String name = result.getMethodParameter().getParameterName();
                result.getResolvableErrors().forEach(e -> errors.add(detail(name, e.getDefaultMessage())));
            }
        });
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", errors);
    }

    /** Bean Validation on method parameters through an AOP proxy (@Validated on PaymentsApi). */
    @ExceptionHandler(ConstraintViolationException.class)
    ResponseEntity<ErrorResponse> constraintViolation(ConstraintViolationException ex) {
        List<ErrorDetail> errors = new ArrayList<>();
        ex.getConstraintViolations().forEach(v -> errors.add(detail(lastNode(v.getPropertyPath().toString()), v.getMessage())));
        return build(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Request validation failed", errors);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErrorResponse> unreadable(HttpMessageNotReadableException ex) {
        return build(HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST",
                "Request body is not valid JSON or has a wrong value (e.g. unknown cardType)", null);
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<ErrorResponse> missingHeader(MissingRequestHeaderException ex) {
        return build(HttpStatus.BAD_REQUEST, "MISSING_HEADER", "Header " + ex.getHeaderName() + " is required", null);
    }

    @ExceptionHandler(ApiException.class)
    ResponseEntity<ErrorResponse> api(ApiException ex) {
        List<ErrorDetail> errors = ex.getViolations().stream().map(v -> detail(v.field(), v.message())).toList();
        return build(ex.getStatus(), ex.getCode(), ex.getMessage(), errors);
    }

    @ExceptionHandler(NiException.class)
    ResponseEntity<ErrorResponse> ni(NiException ex) {
        log.atError().setMessage("N-Genius error: {}").addArgument(ex.getMessage())
                .addKeyValue("ni.status_code", ex.getStatus())
                .addKeyValue("ni.response.body", ex.getResponseBody())
                .log();
        return ex.isClientError()
                ? build(HttpStatus.valueOf(422), "GATEWAY_REJECTED", "N-Genius rejected the request", null)
                : build(HttpStatus.BAD_GATEWAY, "GATEWAY_ERROR", "N-Genius returned an error", null);
    }

    @ExceptionHandler(ResourceAccessException.class)
    ResponseEntity<ErrorResponse> timeout(ResourceAccessException ex) {
        log.error("N-Genius did not answer: {}", ex.getMessage());
        return build(HttpStatus.GATEWAY_TIMEOUT, "GATEWAY_TIMEOUT",
                "N-Genius did not answer; the outcome is unknown. Retry with the same Idempotency-Key.", null);
    }

    /**
     * Everything else.
     * Spring MVC's own errors (404 no route, 405 wrong method, 415 wrong content type, ...) implement
     * Spring's interface org.springframework.web.ErrorResponse and keep their own HTTP status.
     * (Checked with instanceof on purpose: our API model is ALSO called ErrorResponse, and an
     * @ExceptionHandler(ErrorResponse.class) would pick the wrong one.)
     */
    @ExceptionHandler(Exception.class)
    ResponseEntity<ErrorResponse> unexpected(Exception ex) {
        if (ex instanceof org.springframework.web.ErrorResponse springError) {
            return build(springError.getStatusCode(), "REQUEST_ERROR", ex.getMessage(), null);
        }
        log.error("Unexpected error", ex);   // full stack trace; ECS puts it in error.stack_trace
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "Unexpected error", null);
    }

    // ------------------------------------------------------------------ helpers

    private static ResponseEntity<ErrorResponse> build(HttpStatusCode status, String code, String message,
                                                       List<ErrorDetail> errors) {
        if (status.is4xxClientError()) {
            log.atWarn().setMessage("Request rejected: {} {}").addArgument(code).addArgument(message)
                    .addKeyValue("error.code", code)
                    .addKeyValue("error.fields", errors == null ? List.of() : errors.stream().map(ErrorDetail::getField).toList())
                    .log();
        }
        ErrorResponse body = new ErrorResponse()
                .code(code)
                .message(message)
                .correlationId(MDC.get(LogFields.CORRELATION_ID))
                .traceId(MDC.get(LogFields.TRACE_ID))
                .timestamp(OffsetDateTime.now(ZoneOffset.UTC))
                .errors(errors == null || errors.isEmpty() ? null : errors);
        return ResponseEntity.status(status).body(body);
    }

    private static ErrorDetail detail(String field, String message) {
        return new ErrorDetail().field(field).message(message);
    }

    /** "createPayment.idempotencyKey" -> "idempotencyKey" */
    private static String lastNode(String path) {
        int dot = path.lastIndexOf('.');
        return dot < 0 ? path : path.substring(dot + 1);
    }
}
