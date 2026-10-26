package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.lang.Nullable;

import java.util.Objects;

/**
 * PaymentRequest
 */

public class PaymentRequest {

  private String merchantOrderId;

  private CardType cardType;

  private CardInfo card;

  private Amount amount;

  private BillingInfo billingInfo;

  private DeviceInfo deviceInfo;

  public PaymentRequest() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PaymentRequest(String merchantOrderId, CardType cardType, CardInfo card, Amount amount, BillingInfo billingInfo, DeviceInfo deviceInfo) {
    this.merchantOrderId = merchantOrderId;
    this.cardType = cardType;
    this.card = card;
    this.amount = amount;
    this.billingInfo = billingInfo;
    this.deviceInfo = deviceInfo;
  }

  public PaymentRequest merchantOrderId(String merchantOrderId) {
    this.merchantOrderId = merchantOrderId;
    return this;
  }

  /**
   * Your order id (sent to N-Genius as merchantOrderReference)
   * @return merchantOrderId
   */
  @NotNull @Size(min = 1, max = 64) 
  @JsonProperty("merchantOrderId")
  public String getMerchantOrderId() {
    return merchantOrderId;
  }

  @JsonProperty("merchantOrderId")
  public void setMerchantOrderId(String merchantOrderId) {
    this.merchantOrderId = merchantOrderId;
  }

  public PaymentRequest cardType(CardType cardType) {
    this.cardType = cardType;
    return this;
  }

  /**
   * Get cardType
   * @return cardType
   */
  @NotNull @Valid 
  @JsonProperty("cardType")
  public CardType getCardType() {
    return cardType;
  }

  @JsonProperty("cardType")
  public void setCardType(CardType cardType) {
    this.cardType = cardType;
  }

  public PaymentRequest card(CardInfo card) {
    this.card = card;
    return this;
  }

  /**
   * Get card
   * @return card
   */
  @NotNull @Valid 
  @JsonProperty("card")
  public CardInfo getCard() {
    return card;
  }

  @JsonProperty("card")
  public void setCard(CardInfo card) {
    this.card = card;
  }

  public PaymentRequest amount(Amount amount) {
    this.amount = amount;
    return this;
  }

  /**
   * Get amount
   * @return amount
   */
  @NotNull @Valid 
  @JsonProperty("amount")
  public Amount getAmount() {
    return amount;
  }

  @JsonProperty("amount")
  public void setAmount(Amount amount) {
    this.amount = amount;
  }

  public PaymentRequest billingInfo(BillingInfo billingInfo) {
    this.billingInfo = billingInfo;
    return this;
  }

  /**
   * Get billingInfo
   * @return billingInfo
   */
  @NotNull @Valid 
  @JsonProperty("billingInfo")
  public BillingInfo getBillingInfo() {
    return billingInfo;
  }

  @JsonProperty("billingInfo")
  public void setBillingInfo(BillingInfo billingInfo) {
    this.billingInfo = billingInfo;
  }

  public PaymentRequest deviceInfo(DeviceInfo deviceInfo) {
    this.deviceInfo = deviceInfo;
    return this;
  }

  /**
   * Get deviceInfo
   * @return deviceInfo
   */
  @NotNull @Valid 
  @JsonProperty("deviceInfo")
  public DeviceInfo getDeviceInfo() {
    return deviceInfo;
  }

  @JsonProperty("deviceInfo")
  public void setDeviceInfo(DeviceInfo deviceInfo) {
    this.deviceInfo = deviceInfo;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaymentRequest paymentRequest = (PaymentRequest) o;
    return Objects.equals(this.merchantOrderId, paymentRequest.merchantOrderId) &&
        Objects.equals(this.cardType, paymentRequest.cardType) &&
        Objects.equals(this.card, paymentRequest.card) &&
        Objects.equals(this.amount, paymentRequest.amount) &&
        Objects.equals(this.billingInfo, paymentRequest.billingInfo) &&
        Objects.equals(this.deviceInfo, paymentRequest.deviceInfo);
  }

  @Override
  public int hashCode() {
    return Objects.hash(merchantOrderId, cardType, card, amount, billingInfo, deviceInfo);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PaymentRequest {\n");
    sb.append("    merchantOrderId: ").append(toIndentedString(merchantOrderId)).append("\n");
    sb.append("    cardType: ").append(toIndentedString(cardType)).append("\n");
    sb.append("    card: ").append(toIndentedString(card)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    billingInfo: ").append(toIndentedString(billingInfo)).append("\n");
    sb.append("    deviceInfo: ").append(toIndentedString(deviceInfo)).append("\n");
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

