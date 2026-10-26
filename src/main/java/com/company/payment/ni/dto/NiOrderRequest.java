package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * POST /transactions/outlets/{outletId}/orders
 * action "AUTH" = two-stage: authorise now, capture later.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NiOrderRequest(
        String action,
        NiAmount amount,
        String emailAddress,
        String merchantOrderReference,
        NiBillingAddress billingAddress) {
}
