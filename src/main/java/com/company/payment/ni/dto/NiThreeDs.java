package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** "3ds2" block of a payment. transStatus: Y/A = authenticated, C = challenge required, N/R/U = not authenticated. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record NiThreeDs(
        @JsonProperty("transStatus") String transStatus,
        @JsonProperty("acsURL") String acsUrl,
        @JsonProperty("base64EncodedCReq") String base64EncodedCReq,
        @JsonProperty("eci") String eci,
        @JsonProperty("messageVersion") String messageVersion) {
}
