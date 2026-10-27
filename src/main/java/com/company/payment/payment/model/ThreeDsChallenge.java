package com.company.payment.payment.model;

/**
 * What the shopper's browser needs for the 3DS challenge: POST a form with the field "creq" (this value)
 * to acsUrl. The card issuer shows its challenge (e.g. an OTP) and then posts the result to this service.
 */
public record ThreeDsChallenge(String acsUrl, String creq) {
}
