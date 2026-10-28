package com.company.payment.threeds.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The CRes the card issuer's ACS posted (form field "cres") to the client's returnUrl, passed on unchanged. */
public record ChallengeResponseRequest(@NotBlank @Size(max = 20000) String cres) {
}
