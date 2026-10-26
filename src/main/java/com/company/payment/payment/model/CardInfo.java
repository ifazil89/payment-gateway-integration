package com.company.payment.payment.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The card. Which of pan / token is required depends on cardType (checked by PaymentRequestValidator):
 * CARD needs pan, CARD_ON_FILE needs token (the saved N-Genius card token).
 *
 * No Lombok @Builder here: a builder's generated toString would print the card number and CVV.
 */
public record CardInfo(
        @Pattern(regexp = "^\\d{12,19}$") String pan,
        @Size(min = 1, max = 512) String token,
        @NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") String expiry,
        @NotNull @Pattern(regexp = "^\\d{3,4}$") String cvv,
        @NotNull @Size(min = 1, max = 100) String cardholderName) {

    /** pan, token and cvv are never printed (PCI DSS): this object may end up in a log line. */
    @Override
    public String toString() {
        return "CardInfo[pan=" + masked(pan) + ", token=" + masked(token) + ", expiry=" + expiry
                + ", cvv=" + masked(cvv) + ", cardholderName=" + cardholderName + "]";
    }

    private static String masked(String value) {
        return value == null ? "null" : "***";
    }
}
