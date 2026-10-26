package com.company.payment.payment.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** The shopper; sent to N-Genius as the billing address. */
@Builder
public record BillingInfo(
        @NotNull @Size(min = 1, max = 60) String firstName,
        @NotNull @Size(min = 1, max = 60) String lastName,
        @NotNull @Size(max = 254) @Email String email,
        @Size(max = 200) String address,
        @Size(max = 60) String city,
        @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
}
