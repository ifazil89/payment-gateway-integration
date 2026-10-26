package com.company.payment.ni.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** N-Genius settings ("ni.*" in application.yml). The app does not start if one is missing. */
@Validated
@ConfigurationProperties("ni")
public record NiProperties(
        @NotBlank String baseUrl,
        @NotBlank String apiKey,
        @NotBlank String outletId,
        String realmName,
        @NotNull Duration connectTimeout,
        @NotNull Duration responseTimeout,
        int maxConnections,
        /** e.g. https://pay.example.com/api/v1/threeds/{paymentId}/notification */
        @NotBlank String threedsNotificationUrl) {

    public String threedsNotificationUrl(String paymentId) {
        return threedsNotificationUrl.replace("{paymentId}", paymentId);
    }

    @Override
    public String toString() {   // never print the API key
        return "NiProperties[baseUrl=" + baseUrl + ", outletId=" + outletId + "]";
    }
}
