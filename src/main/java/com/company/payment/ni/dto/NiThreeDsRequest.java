package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * POST cnp:3ds2-authentication
 *   deviceChannel   "BRW" (browser)
 *   threeDSCompInd  "Y"
 *   notificationURL where the bank posts the challenge result
 */
public record NiThreeDsRequest(
        @JsonProperty("deviceChannel") String deviceChannel,
        @JsonProperty("threeDSCompInd") String threeDsCompInd,
        @JsonProperty("notificationURL") String notificationUrl,
        @JsonProperty("browserInfo") NiBrowserInfo browserInfo) {
}
