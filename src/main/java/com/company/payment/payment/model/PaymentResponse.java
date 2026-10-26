package com.company.payment.payment.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.lang.Nullable;

import java.time.OffsetDateTime;
import java.util.Objects;

/**
 * PaymentResponse
 */

public class PaymentResponse {

  private String paymentId;

  private String merchantOrderId;

  private PaymentStatus status;

  private Amount amount;

  private @Nullable String orderReference;

  private @Nullable String failureReason;

  @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
  private OffsetDateTime createdAt;

  public PaymentResponse() {
    super();
  }

  /**
   * Constructor with only required parameters
   */
  public PaymentResponse(String paymentId, String merchantOrderId, PaymentStatus status, Amount amount, OffsetDateTime createdAt) {
    this.paymentId = paymentId;
    this.merchantOrderId = merchantOrderId;
    this.status = status;
    this.amount = amount;
    this.createdAt = createdAt;
  }

  public PaymentResponse paymentId(String paymentId) {
    this.paymentId = paymentId;
    return this;
  }

  /**
   * Our payment id - use it in the next API call
   * @return paymentId
   */
  @NotNull 
  @JsonProperty("paymentId")
  public String getPaymentId() {
    return paymentId;
  }

  @JsonProperty("paymentId")
  public void setPaymentId(String paymentId) {
    this.paymentId = paymentId;
  }

  public PaymentResponse merchantOrderId(String merchantOrderId) {
    this.merchantOrderId = merchantOrderId;
    return this;
  }

  /**
   * Get merchantOrderId
   * @return merchantOrderId
   */
  @NotNull 
  @JsonProperty("merchantOrderId")
  public String getMerchantOrderId() {
    return merchantOrderId;
  }

  @JsonProperty("merchantOrderId")
  public void setMerchantOrderId(String merchantOrderId) {
    this.merchantOrderId = merchantOrderId;
  }

  public PaymentResponse status(PaymentStatus status) {
    this.status = status;
    return this;
  }

  /**
   * Get status
   * @return status
   */
  @NotNull @Valid 
  @JsonProperty("status")
  public PaymentStatus getStatus() {
    return status;
  }

  @JsonProperty("status")
  public void setStatus(PaymentStatus status) {
    this.status = status;
  }

  public PaymentResponse amount(Amount amount) {
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

  public PaymentResponse orderReference(@Nullable String orderReference) {
    this.orderReference = orderReference;
    return this;
  }

  /**
   * N-Genius order reference (support / reconciliation)
   * @return orderReference
   */
  
  @JsonProperty("orderReference")
  public @Nullable String getOrderReference() {
    return orderReference;
  }

  @JsonProperty("orderReference")
  public void setOrderReference(@Nullable String orderReference) {
    this.orderReference = orderReference;
  }

  public PaymentResponse failureReason(@Nullable String failureReason) {
    this.failureReason = failureReason;
    return this;
  }

  /**
   * Why the payment was declined or failed
   * @return failureReason
   */
  
  @JsonProperty("failureReason")
  public @Nullable String getFailureReason() {
    return failureReason;
  }

  @JsonProperty("failureReason")
  public void setFailureReason(@Nullable String failureReason) {
    this.failureReason = failureReason;
  }

  public PaymentResponse createdAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
    return this;
  }

  /**
   * Get createdAt
   * @return createdAt
   */
  @NotNull @Valid 
  @JsonProperty("createdAt")
  public OffsetDateTime getCreatedAt() {
    return createdAt;
  }

  @JsonProperty("createdAt")
  public void setCreatedAt(OffsetDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    PaymentResponse paymentResponse = (PaymentResponse) o;
    return Objects.equals(this.paymentId, paymentResponse.paymentId) &&
        Objects.equals(this.merchantOrderId, paymentResponse.merchantOrderId) &&
        Objects.equals(this.status, paymentResponse.status) &&
        Objects.equals(this.amount, paymentResponse.amount) &&
        Objects.equals(this.orderReference, paymentResponse.orderReference) &&
        Objects.equals(this.failureReason, paymentResponse.failureReason) &&
        Objects.equals(this.createdAt, paymentResponse.createdAt);
  }

  @Override
  public int hashCode() {
    return Objects.hash(paymentId, merchantOrderId, status, amount, orderReference, failureReason, createdAt);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("class PaymentResponse {\n");
    sb.append("    paymentId: ").append(toIndentedString(paymentId)).append("\n");
    sb.append("    merchantOrderId: ").append(toIndentedString(merchantOrderId)).append("\n");
    sb.append("    status: ").append(toIndentedString(status)).append("\n");
    sb.append("    amount: ").append(toIndentedString(amount)).append("\n");
    sb.append("    orderReference: ").append(toIndentedString(orderReference)).append("\n");
    sb.append("    failureReason: ").append(toIndentedString(failureReason)).append("\n");
    sb.append("    createdAt: ").append(toIndentedString(createdAt)).append("\n");
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

