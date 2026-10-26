package com.company.payment.payment.model;

import lombok.Builder;

import java.time.OffsetDateTime;

/** Answer of POST /api/v1/payments. The client decides its next call from status. */
@Builder
public record PaymentResponse(
        String paymentId,
        String merchantOrderId,
        PaymentStatus status,
        Amount amount,
        String orderReference,
        String failureReason,
        OffsetDateTime createdAt) {
}
