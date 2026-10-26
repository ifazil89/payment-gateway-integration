package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/** "browserInfo" of the 3DS2 authentication request, with N-Genius' exact field names. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record NiBrowserInfo(
        @JsonProperty("browserAcceptHeader") String browserAcceptHeader,
        @JsonProperty("browserJavaEnabled") Boolean browserJavaEnabled,
        @JsonProperty("browserLanguage") String browserLanguage,
        @JsonProperty("browserTZ") String browserTz,
        @JsonProperty("browserUserAgent") String browserUserAgent,
        @JsonProperty("browserColorDepth") String browserColorDepth,
        @JsonProperty("browserScreenHeight") String browserScreenHeight,
        @JsonProperty("browserScreenWidth") String browserScreenWidth,
        @JsonProperty("browserJavascriptEnabled") Boolean browserJavascriptEnabled,
        @JsonProperty("browserIP") String browserIp,
        @JsonProperty("challengeWindowSize") String challengeWindowSize) {
}
