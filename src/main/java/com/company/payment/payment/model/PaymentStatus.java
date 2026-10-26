package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Gets or Sets PaymentStatus
 */

public enum PaymentStatus {
  
  AUTHORISED("AUTHORISED"),
  
  CHALLENGE_REQUIRED("CHALLENGE_REQUIRED"),
  
  PENDING("PENDING"),
  
  DECLINED("DECLINED"),
  
  FAILED("FAILED");

  private final String value;

  PaymentStatus(String value) {
    this.value = value;
  }

  @JsonValue
  public String getValue() {
    return value;
  }

  @Override
  public String toString() {
    return String.valueOf(value);
  }

  @JsonCreator
  public static PaymentStatus fromValue(String value) {
    for (PaymentStatus b : PaymentStatus.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

