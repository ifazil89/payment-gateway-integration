package com.company.payment.payment.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Builder;

/** The shopper's browser, for EMV 3DS 2.x (sent to N-Genius as browserInfo). */
@Builder
public record DeviceInfo(
        @NotNull @Size(min = 1, max = 45) String ip,
        @NotNull @Size(min = 1, max = 2048) String userAgent,
        @NotNull @Size(min = 1, max = 2048) String acceptHeader,
        @NotNull @Size(min = 1, max = 8) String language,
        @Pattern(regexp = "^-?\\d{1,4}$") String timeZoneOffset,
        @Pattern(regexp = "^\\d{1,6}$") String screenWidth,
        @Pattern(regexp = "^\\d{1,6}$") String screenHeight,
        @Pattern(regexp = "^\\d{1,2}$") String colorDepth,
        Boolean javascriptEnabled,
        Boolean javaEnabled,
        @Pattern(regexp = "^0[1-5]$") String challengeWindowSize) {
}
