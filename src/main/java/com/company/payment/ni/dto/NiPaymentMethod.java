package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * "paymentMethod" of a payment: name = card brand (VISA, MASTERCARD...), pan = already masked by N-Genius
 * (411111******1111). Cardholder name and expiry are left out on purpose: they are not needed here.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiPaymentMethod(String name, String pan) {
}
