package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * An N-Genius order (create order, retrieve order). A one-time payment order contains one payment (index 0).
 * There is no order-level state: the outcome is the state of the payment in _embedded.payment.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiOrderResponse(
        String reference,
        String action,
        NiAmount amount,
        OffsetDateTime createDateTime,
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

    /** The payment with this reference; the first one if it is not found (or the reference is unknown). */
    public NiPaymentResponse payment(String paymentReference) {
        NiPaymentResponse first = firstPayment();
        return embedded.payment().stream()
                .filter(p -> paymentReference != null && paymentReference.equals(p.reference()))
                .findFirst()
                .orElse(first);
    }
}
