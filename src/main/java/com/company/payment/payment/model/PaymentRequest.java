package com.company.payment.payment.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** Body of POST /api/v1/payments. */
@Builder
public record PaymentRequest(
        @NotNull @Size(min = 1, max = 64) String merchantOrderId,
        @NotNull CardType cardType,
        @NotNull @Valid CardInfo card,
        @NotNull @Valid Amount amount,
        @NotNull @Valid BillingInfo billingInfo,
        @NotNull @Valid DeviceInfo deviceInfo) {
}
