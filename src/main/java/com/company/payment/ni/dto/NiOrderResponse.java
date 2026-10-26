package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** An N-Genius order. A one-time payment order contains exactly one payment (index 0). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiOrderResponse(
        String reference,
        NiAmount amount,
        @JsonProperty("_embedded") Embedded embedded) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Embedded(List<NiPaymentResponse> payment) {
    }

    public NiPaymentResponse firstPayment() {
        if (embedded == null || embedded.payment() == null || embedded.payment().isEmpty()) {
            throw new IllegalStateException("N-Genius order " + reference + " contains no payment");
        }
        return embedded.payment().getFirst();
    }
}
