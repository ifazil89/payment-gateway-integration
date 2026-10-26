package com.company.payment.common.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Returned for every non-200 response. &#x60;errors&#x60; is present only for VALIDATION_ERROR.
 */

public class ErrorResponse {

  private String code;

  private String message;

  private @Nullable String correlationId;

  private @Nullable String traceId;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime timestamp;

  private List<@Valid ErrorDetail> errors = new ArrayList<>();

  public ErrorResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public ErrorResponse(String code, String message, OffsetDateTime timestamp) {
    this.code = code;
    this.message = message;
    this.timestamp = timestamp;
  }

  public ErrorResponse code(String code) {
    this.code = code;
    return this;
  }

  /**
   * VALIDATION_ERROR, MALFORMED_REQUEST, MISSING_HEADER, UNAUTHORIZED, IDEMPOTENCY_KEY_REUSED, REQUEST_IN_PROGRESS, INVALID_AMOUNT, GATEWAY_REJECTED, GATEWAY_ERROR, GATEWAY_TIMEOUT, REQUEST_ERROR, INTERNAL_ERROR 
   * @return code
   */
  @NotNull 
  @JsonProperty("code")
  public String getCode() {
    return code;
  }

  @JsonProperty("code")
  public void setCode(String code) {
    this.code = code;
  }

  public ErrorResponse message(String message) {
    this.message = message;
    return this;
  }

  /**
   * Get message
   * @return message
   */
  @NotNull 
  @JsonProperty("message")
  public String getMessage() {
    return message;
  }

  @JsonProperty("message")
  public void setMessage(String message) {
    this.message = message;
  }

  public ErrorResponse correlationId(@Nullable String correlationId) {
    this.correlationId = correlationId;
    return this;
  }

  /**
   * X-Correlation-Id of the request (sent by you or generated). Quote it when reporting a problem.
   * @return correlationId
   */
  
  @JsonProperty("correlationId")
  public @Nullable String getCorrelationId() {
    return correlationId;
  }

  @JsonProperty("correlationId")
  public void setCorrelationId(@Nullable String correlationId) {
    this.correlationId = correlationId;
  }

  public ErrorResponse traceId(@Nullable String traceId) {
    this.traceId = traceId;
    return this;
  }

  /**
   * Distributed trace id (also in the X-Trace-Id response header)
   * @return traceId
   */
  
  @JsonProperty("traceId")
  public @Nullable String getTraceId() {
    return traceId;
  }

  @JsonProperty("traceId")
  public void setTraceId(@Nullable String traceId) {
    this.traceId = traceId;
  }

  public ErrorResponse timestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
    return this;
  }

  /**
   * Get timestamp
   * @return timestamp
   */
  @NotNull @Valid 
  @JsonProperty("timestamp")
  public OffsetDateTime getTimestamp() {
    return timestamp;
  }

  @JsonProperty("timestamp")
  public void setTimestamp(OffsetDateTime timestamp) {
    this.timestamp = timestamp;
  }

  public ErrorResponse errors(List<@Valid ErrorDetail> errors) {
    this.errors = errors;
    return this;
  }

  public ErrorResponse addErrorsItem(ErrorDetail errorsItem) {
    if (this.errors == null) {
      this.errors = new ArrayList<>();
    }
    this.errors.add(errorsItem);
    return this;
  }

  /**
   * One entry per invalid field (VALIDATION_ERROR only)
   * @return errors
   */
  
  @JsonProperty("errors")
  public List<@Valid ErrorDetail> getErrors() {
    return errors;
  }

  @JsonProperty("errors")
  public void setErrors(List<@Valid ErrorDetail> errors) {
    this.errors = errors;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ErrorResponse errorResponse = (ErrorResponse) o;
    return Objects.equals(this.code, errorResponse.code) &&
        Objects.equals(this.message, errorResponse.message) &&
        Objects.equals(this.correlationId, errorResponse.correlationId) &&
        Objects.equals(this.traceId, errorResponse.traceId) &&
        Objects.equals(this.timestamp, errorResponse.timestamp) &&
        Objects.equals(this.errors, errorResponse.errors);
  }

  @Override
  public int hashCode() {
    return Objects.hash(code, message, correlationId, traceId, timestamp, errors);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class ErrorResponse {\n");
    sb.append("    code: ").append(toIndentedString(code)).append("\n");
    sb.append("    message: ").append(toIndentedString(message)).append("\n");
    sb.append("    correlationId: ").append(toIndentedString(correlationId)).append("\n");
    sb.append("    traceId: ").append(toIndentedString(traceId)).append("\n");
    sb.append("    timestamp: ").append(toIndentedString(timestamp)).append("\n");
    sb.append("    errors: ").append(toIndentedString(errors)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

