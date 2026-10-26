package com.company.payment.ni.dto;

/** PUT payment:saved-card. N-Genius requires cardToken, expiry, cvv and cardholderName. */
public record NiTokenCardRequest(String cardToken, String expiry, String cvv, String cardholderName) {

    @Override
    public String toString() {
        return "NiTokenCardRequest[***]";
    }
}
