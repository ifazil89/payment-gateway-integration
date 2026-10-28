package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

/**
 * A payment inside an N-Genius order; also the response of the card, token and 3DS calls.
 * state: STARTED, AWAIT_3DS, AUTHORISED, CAPTURED, FAILED, DECLINED, REVERSED, ...
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiPaymentResponse(
        String reference,
        String orderReference,
        String state,
        NiAmount amount,
        @JsonProperty("3ds2") NiThreeDs threeDs,
        @JsonProperty("_links") Map<String, Object> links,
        NiPaymentMethod paymentMethod,
        OffsetDateTime updateDateTime) {

    public Optional<String> link(String relation) {
        return NiLinks.href(links, relation);
    }
}
