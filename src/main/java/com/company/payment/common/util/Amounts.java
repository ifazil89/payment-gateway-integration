package com.company.payment.common.util;

import com.company.payment.common.exception.ApiException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Currency;

/**
 * Amounts are MAJOR units (AED 250.00) with the currency's own decimals: AED/SAR/USD 2, KWD/BHD/OMR 3.
 * N-Genius wants MINOR units (25000).
 */
public final class Amounts {

    private Amounts() {
    }

    /** Checks currency and decimals; returns the value with the currency's scale (250 -> 250.00). */
    public static BigDecimal normalize(BigDecimal value, String currency) {
        int decimals = decimals(currency);
        if (value.stripTrailingZeros().scale() > decimals) {
            throw ApiException.unprocessable("INVALID_AMOUNT",
                    currency + " allows " + decimals + " decimals, got " + value.toPlainString());
        }
        return value.setScale(decimals, RoundingMode.UNNECESSARY);
    }

    /** AED 250.00 -> 25000, KWD 1.250 -> 1250 */
    public static long toMinorUnits(BigDecimal value, String currency) {
        return normalize(value, currency).movePointRight(decimals(currency)).longValueExact();
    }

    /** 25000 AED -> 250.00, 1250 KWD -> 1.250 */
    public static BigDecimal fromMinorUnits(long minorUnits, String currency) {
        return BigDecimal.valueOf(minorUnits, decimals(currency));
    }

    private static int decimals(String currency) {
        try {
            return Currency.getInstance(currency).getDefaultFractionDigits();
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw ApiException.unprocessable("INVALID_AMOUNT", "Unknown currency: " + currency);
        }
    }
}
