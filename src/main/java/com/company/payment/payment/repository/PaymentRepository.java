package com.company.payment.payment.repository;

import com.company.payment.payment.entity.Payment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {

    /** Idempotency keys are unique per client. */
    Optional<Payment> findByClientIdAndIdempotencyKey(String clientId, String idempotencyKey);

    /** A client only ever sees its own payments. */
    Optional<Payment> findByIdAndClientId(String id, String clientId);
}
