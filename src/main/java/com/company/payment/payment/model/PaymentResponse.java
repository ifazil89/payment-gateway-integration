package com.company.payment.payment.model;

import lombok.Builder;

import java.time.OffsetDateTime;

/** Answer of POST and GET /api/v1/payments. The client decides its next call from status; threeDs only for CHALLENGE_REQUIRED. */
@Builder
public record PaymentResponse(
        String paymentId,
        String merchantOrderId,
        PaymentStatus status,
        Amount amount,
        String orderReference,
        String failureReason,
        OffsetDateTime createdAt,
        ThreeDsChallenge threeDs) {
}
