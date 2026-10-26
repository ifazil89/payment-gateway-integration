package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.lang.Nullable;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * MAJOR units (AED 250.00). Max decimals &#x3D; the currency&#39;s decimals (AED 2, KWD 3).
 */

public class Amount {

  private BigDecimal value;

  private String currency;

  public Amount() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public Amount(BigDecimal value, String currency) {
    this.value = value;
    this.currency = currency;
  }

  public Amount value(BigDecimal value) {
    this.value = value;
    return this;
  }

  /**
   * Get value
   * minimum: 0.001
   * @return value
   */
  @NotNull @Valid @DecimalMin(value = "0.001") 
  @JsonProperty("value")
  public BigDecimal getValue() {
    return value;
  }

  @JsonProperty("value")
  public void setValue(BigDecimal value) {
    this.value = value;
  }

  public Amount currency(String currency) {
    this.currency = currency;
    return this;
  }

  /**
   * ISO 4217
   * @return currency
   */
  @NotNull @Pattern(regexp = "^[A-Z]{3}$") 
  @JsonProperty("currency")
  public String getCurrency() {
    return currency;
  }

  @JsonProperty("currency")
  public void setCurrency(String currency) {
    this.currency = currency;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Amount amount = (Amount) o;
    return Objects.equals(this.value, amount.value) &&
        Objects.equals(this.currency, amount.currency);
  }

  @Override
  public int hashCode() {
    return Objects.hash(value, currency);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class Amount {\n");
    sb.append("    value: ").append(toIndentedString(value)).append("\n");
    sb.append("    currency: ").append(toIndentedString(currency)).append("\n");
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

