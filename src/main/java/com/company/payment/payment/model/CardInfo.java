package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * One card object for both card types; &#x60;cardType&#x60; decides which fields are required.
 */

public class CardInfo {

  private @Nullable String pan;

  private @Nullable String token;

  private String expiry;

  private String cvv;

  private String cardholderName;

  public CardInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public CardInfo(String expiry, String cvv, String cardholderName) {
    this.expiry = expiry;
    this.cvv = cvv;
    this.cardholderName = cardholderName;
  }

  public CardInfo pan(@Nullable String pan) {
    this.pan = pan;
    return this;
  }

  /**
   * Card number, digits only. CARD only.
   * @return pan
   */
  @Pattern(regexp = "^\\d{12,19}$") 
  @JsonProperty("pan")
  public @Nullable String getPan() {
    return pan;
  }

  @JsonProperty("pan")
  public void setPan(@Nullable String pan) {
    this.pan = pan;
  }

  public CardInfo token(@Nullable String token) {
    this.token = token;
    return this;
  }

  /**
   * Saved N-Genius card token. CARD_ON_FILE only.
   * @return token
   */
  @Size(min = 1, max = 512) 
  @JsonProperty("token")
  public @Nullable String getToken() {
    return token;
  }

  @JsonProperty("token")
  public void setToken(@Nullable String token) {
    this.token = token;
  }

  public CardInfo expiry(String expiry) {
    this.expiry = expiry;
    return this;
  }

  /**
   * YYYY-MM
   * @return expiry
   */
  @NotNull @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$") 
  @JsonProperty("expiry")
  public String getExpiry() {
    return expiry;
  }

  @JsonProperty("expiry")
  public void setExpiry(String expiry) {
    this.expiry = expiry;
  }

  public CardInfo cvv(String cvv) {
    this.cvv = cvv;
    return this;
  }

  /**
   * Get cvv
   * @return cvv
   */
  @NotNull @Pattern(regexp = "^\\d{3,4}$") 
  @JsonProperty("cvv")
  public String getCvv() {
    return cvv;
  }

  @JsonProperty("cvv")
  public void setCvv(String cvv) {
    this.cvv = cvv;
  }

  public CardInfo cardholderName(String cardholderName) {
    this.cardholderName = cardholderName;
    return this;
  }

  /**
   * Get cardholderName
   * @return cardholderName
   */
  @NotNull @Size(min = 1, max = 100) 
  @JsonProperty("cardholderName")
  public String getCardholderName() {
    return cardholderName;
  }

  @JsonProperty("cardholderName")
  public void setCardholderName(String cardholderName) {
    this.cardholderName = cardholderName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    CardInfo cardInfo = (CardInfo) o;
    return Objects.equals(this.pan, cardInfo.pan) &&
        Objects.equals(this.token, cardInfo.token) &&
        Objects.equals(this.expiry, cardInfo.expiry) &&
        Objects.equals(this.cvv, cardInfo.cvv) &&
        Objects.equals(this.cardholderName, cardInfo.cardholderName);
  }

  @Override
  public int hashCode() {
    return Objects.hash(pan, token, expiry, cvv, cardholderName);
  }

  /** pan, token and cvv are never printed (PCI DSS): this object may end up in a log line. */
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class CardInfo {\n");
    sb.append("    pan: ").append(pan == null ? "null" : "***").append("\n");
    sb.append("    token: ").append(token == null ? "null" : "***").append("\n");
    sb.append("    expiry: ").append(toIndentedString(expiry)).append("\n");
    sb.append("    cvv: ").append(cvv == null ? "null" : "***").append("\n");
    sb.append("    cardholderName: ").append(toIndentedString(cardholderName)).append("\n");
    sb.append("}");
    return sb.toString();
  }

  /**
   * Convert the given object to string with each line indented by 4 spaces
   * (except the first line).
   */
  private String toIndentedString(@Nullable Object o) {
    return o == null ? "null" : o.toString().replace("\n", "\n    ");
  }
}

