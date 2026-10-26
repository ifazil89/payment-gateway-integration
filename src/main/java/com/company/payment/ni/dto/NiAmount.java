package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** N-Genius amount: value in MINOR units (AED 1.00 = 100). */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiAmount(String currencyCode, Long value) {
}
