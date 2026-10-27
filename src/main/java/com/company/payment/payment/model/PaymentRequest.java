package com.company.payment.payment.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/**
 * Body of POST /api/v1/payments.
 * returnUrl (optional): where the shopper is sent after a 3DS challenge; must match one of the client's
 * allowed return URLs. Without it the shopper sees a short "you can close this window" page.
 */
@Builder
public record PaymentRequest(
        @NotNull @Size(min = 1, max = 64) String merchantOrderId,
        @NotNull CardType cardType,
        @NotNull @Valid CardInfo card,
        @NotNull @Valid Amount amount,
        @NotNull @Valid BillingInfo billingInfo,
        @NotNull @Valid DeviceInfo deviceInfo,
        @Size(max = 2048) String returnUrl) {
}
