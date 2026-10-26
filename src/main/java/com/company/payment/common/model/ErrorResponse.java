package com.company.payment.common.model;

import lombok.Builder;

import java.time.OffsetDateTime;
import java.util.List;

/** Body of every non-200 response. errors is only set for VALIDATION_ERROR (null fields are not serialized). */
@Builder
public record ErrorResponse(
        String code,
        String message,
        String correlationId,
        String traceId,
        OffsetDateTime timestamp,
        List<ErrorDetail> errors) {
}
