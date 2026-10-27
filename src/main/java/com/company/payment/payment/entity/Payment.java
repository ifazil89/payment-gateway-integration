package com.company.payment.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * A payment. No card number and no CVV are ever stored here: only the masked PAN.
 *
 * Fields copied from the request (merchantOrderId, cardType, amount...) have setters, used by
 * PaymentMapper. Everything that changes during the flow goes through the methods below.
 * The no-args constructor is for JPA and MapStruct.
 */
@Entity
@Table(name = "payment")
@Getter
@NoArgsConstructor
public class Payment {

    @Id
    private String id;
    @Column(name = "client_id", nullable = false)
    private String clientId;
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;
    /** Fingerprint of the request: the same Idempotency-Key with a different request is rejected. */
    @Column(name = "request_hash", nullable = false)
    private String requestHash;

    @Setter
    @Column(name = "merchant_order_id", nullable = false)
    private String merchantOrderId;
    @Setter
    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "card_type", nullable = false)
    private PaymentCardType cardType;
    @Setter
    @Column(name = "masked_pan")
    private String maskedPan;
    @Setter
    @Column(nullable = false, precision = 19, scale = 3)
    private BigDecimal amount;
    @Setter
    @Column(nullable = false, length = 3)
    private String currency;
    @Setter
    @Column(nullable = false)
    private String email;
    /** Where the shopper is sent after a 3DS challenge (checked against the client's allowed return URLs). */
    @Setter
    @Column(name = "return_url")
    private String returnUrl;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false)
    private PaymentState state;
    @Column(name = "failure_reason")
    private String failureReason;

    @Column(name = "ni_order_reference")
    private String niOrderReference;
    @Column(name = "ni_payment_reference")
    private String niPaymentReference;
    /** Last state reported by N-Genius (raw), for support. */
    @Column(name = "ni_state")
    private String niState;

    @Column(name = "threeds_trans_status")
    private String threeDsTransStatus;
    @Column(name = "threeds_acs_url")
    private String threeDsAcsUrl;
    /** Base64 challenge request, needed by the 3DS API when state = CHALLENGE_REQUIRED. */
    @Column(name = "threeds_creq")
    private String threeDsCreq;
    /** N-Genius "cnp:3ds2-challenge-response" link: where the challenge result (CRes) is sent. */
    @Column(name = "threeds_challenge_response_url")
    private String threeDsChallengeResponseUrl;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
    @Version
    private Long version;

    // ------------------------------------------------------------------ flow

    /** Before the first save. */
    public void start(String clientId, String idempotencyKey, String requestHash) {
        this.id = UUID.randomUUID().toString();
        this.clientId = clientId;
        this.idempotencyKey = idempotencyKey;
        this.requestHash = requestHash;
        this.state = PaymentState.CREATED;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void orderCreated(String niOrderReference, String niPaymentReference) {
        this.niOrderReference = niOrderReference;
        this.niPaymentReference = niPaymentReference;
        touch();
    }

    /** Applies what N-Genius answered. An unknown N-Genius state keeps our current state. */
    public void niAnswered(String niState) {
        this.niState = niState;
        PaymentState mapped = PaymentState.fromNi(niState);
        if (mapped != null) {
            this.state = mapped;
        }
        touch();
    }

    public void challengeRequired(String niState, String transStatus, String acsUrl, String creq,
                                  String challengeResponseUrl) {
        this.niState = niState;
        this.threeDsTransStatus = transStatus;
        this.threeDsAcsUrl = acsUrl;
        this.threeDsCreq = creq;
        this.threeDsChallengeResponseUrl = challengeResponseUrl;
        this.state = PaymentState.CHALLENGE_REQUIRED;
        touch();
    }

    /** The shopper finished the challenge: from now on N-Genius is finishing the authentication. */
    public void challengeResultReceived() {
        this.state = PaymentState.AWAITING_3DS;
        touch();
    }

    /** N-Genius answered the challenge result. */
    public void challengeCompleted(String niState, String transStatus) {
        if (transStatus != null) {
            this.threeDsTransStatus = transStatus;
        }
        niAnswered(niState);
    }

    public void threeDsAuthenticated(String niState, String transStatus) {
        this.threeDsTransStatus = transStatus;
        niAnswered(niState);
    }

    public void fail(String reason) {
        this.state = PaymentState.FAILED;
        this.failureReason = reason;
        touch();
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
