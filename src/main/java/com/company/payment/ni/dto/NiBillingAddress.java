package com.company.payment.ni.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record NiBillingAddress(String firstName, String lastName, String address1, String city, String countryCode) {
}
