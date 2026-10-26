package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * BillingInfo
 */

public class BillingInfo {

  private String firstName;

  private String lastName;

  private String email;

  private @Nullable String address;

  private @Nullable String city;

  private @Nullable String countryCode;

  public BillingInfo() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public BillingInfo(String firstName, String lastName, String email) {
    this.firstName = firstName;
    this.lastName = lastName;
    this.email = email;
  }

  public BillingInfo firstName(String firstName) {
    this.firstName = firstName;
    return this;
  }

  /**
   * Get firstName
   * @return firstName
   */
  @NotNull @Size(min = 1, max = 60) 
  @JsonProperty("firstName")
  public String getFirstName() {
    return firstName;
  }

  @JsonProperty("firstName")
  public void setFirstName(String firstName) {
    this.firstName = firstName;
  }

  public BillingInfo lastName(String lastName) {
    this.lastName = lastName;
    return this;
  }

  /**
   * Get lastName
   * @return lastName
   */
  @NotNull @Size(min = 1, max = 60) 
  @JsonProperty("lastName")
  public String getLastName() {
    return lastName;
  }

  @JsonProperty("lastName")
  public void setLastName(String lastName) {
    this.lastName = lastName;
  }

  public BillingInfo email(String email) {
    this.email = email;
    return this;
  }

  /**
   * Get email
   * @return email
   */
  @NotNull @Size(max = 254) @Email
  @JsonProperty("email")
  public String getEmail() {
    return email;
  }

  @JsonProperty("email")
  public void setEmail(String email) {
    this.email = email;
  }

  public BillingInfo address(@Nullable String address) {
    this.address = address;
    return this;
  }

  /**
   * Get address
   * @return address
   */
  @Size(max = 200) 
  @JsonProperty("address")
  public @Nullable String getAddress() {
    return address;
  }

  @JsonProperty("address")
  public void setAddress(@Nullable String address) {
    this.address = address;
  }

  public BillingInfo city(@Nullable String city) {
    this.city = city;
    return this;
  }

  /**
   * Get city
   * @return city
   */
  @Size(max = 60) 
  @JsonProperty("city")
  public @Nullable String getCity() {
    return city;
  }

  @JsonProperty("city")
  public void setCity(@Nullable String city) {
    this.city = city;
  }

  public BillingInfo countryCode(@Nullable String countryCode) {
    this.countryCode = countryCode;
    return this;
  }

  /**
   * ISO 3166-1 alpha-2
   * @return countryCode
   */
  @Pattern(regexp = "^[A-Z]{2}$") 
  @JsonProperty("countryCode")
  public @Nullable String getCountryCode() {
    return countryCode;
  }

  @JsonProperty("countryCode")
  public void setCountryCode(@Nullable String countryCode) {
    this.countryCode = countryCode;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    BillingInfo billingInfo = (BillingInfo) o;
    return Objects.equals(this.firstName, billingInfo.firstName) &&
        Objects.equals(this.lastName, billingInfo.lastName) &&
        Objects.equals(this.email, billingInfo.email) &&
        Objects.equals(this.address, billingInfo.address) &&
        Objects.equals(this.city, billingInfo.city) &&
        Objects.equals(this.countryCode, billingInfo.countryCode);
  }

  @Override
  public int hashCode() {
    return Objects.hash(firstName, lastName, email, address, city, countryCode);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class BillingInfo {\n");
    sb.append("    firstName: ").append(toIndentedString(firstName)).append("\n");
    sb.append("    lastName: ").append(toIndentedString(lastName)).append("\n");
    sb.append("    email: ").append(toIndentedString(email)).append("\n");
    sb.append("    address: ").append(toIndentedString(address)).append("\n");
    sb.append("    city: ").append(toIndentedString(city)).append("\n");
    sb.append("    countryCode: ").append(toIndentedString(countryCode)).append("\n");
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

