package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * CARD = new card (pan required, token empty). CARD_ON_FILE = saved N-Genius card token (token required, pan empty). 
 */

public enum CardType {
  
  CARD("CARD"),
  
  CARD_ON_FILE("CARD_ON_FILE");

  private final String value;

  CardType(String value) {
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
  public static CardType fromValue(String value) {
    for (CardType b : CardType.values()) {
      if (b.value.equals(value)) {
        return b;
      }
    }
    throw new IllegalArgumentException("Unexpected value '" + value + "'");
  }
}

