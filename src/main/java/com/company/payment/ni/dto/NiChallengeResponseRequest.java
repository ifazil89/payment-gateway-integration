package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** POST cnp:3ds2-challenge-response: the CRes the card issuer's ACS posted back after the challenge. */
public record NiChallengeResponseRequest(@JsonProperty("base64EncodedCRes") String base64EncodedCRes) {
}
