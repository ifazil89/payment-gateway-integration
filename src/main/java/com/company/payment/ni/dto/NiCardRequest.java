package com.company.payment.ni.dto;

/** PUT payment:card */
public record NiCardRequest(String pan, String expiry, String cvv, String cardholderName) {

    @Override
    public String toString() {
        return "NiCardRequest[***]";
    }
}
