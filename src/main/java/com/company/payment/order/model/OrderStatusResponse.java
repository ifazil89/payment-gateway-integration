package com.company.payment.order.model;

import com.company.payment.payment.model.Amount;
import com.company.payment.payment.model.PaymentStatus;
import lombok.Builder;

import java.time.OffsetDateTime;

/**
 * Answer of GET /api/v1/orders/{orderReference}: the order as N-Genius reports it right now.
 * status is our payment status after this check; gatewayState is N-Genius' raw payment state (for support).
 */
@Builder
public record OrderStatusResponse(
        String orderReference,
        String paymentId,
        String merchantOrderId,
        String action,
        Amount amount,
        PaymentStatus status,
        String gatewayState,
        String cardBrand,
        String maskedPan,
        OffsetDateTime orderCreatedAt,
        OffsetDateTime updatedAt) {
}
