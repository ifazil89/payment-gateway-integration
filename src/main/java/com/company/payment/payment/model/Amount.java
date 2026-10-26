package com.company.payment.payment.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Money in major units (250.00 AED), ISO 4217 currency. The allowed decimals are checked by Amounts. */
public record Amount(
        @NotNull @DecimalMin("0.001") BigDecimal value,
        @NotNull @Pattern(regexp = "^[A-Z]{3}$") String currency) {
}
