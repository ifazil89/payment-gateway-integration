package com.company.payment.payment.controller;

import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.common.security.ApiKeyFilter;
import com.company.payment.payment.api.PaymentsApi;
import com.company.payment.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/**
 * Implements PaymentsApi (payment/api). Path, headers and Bean Validation come from the interface;
 * this class only hands over to PaymentService.
 */
@RestController
@RequiredArgsConstructor
public class PaymentController implements PaymentsApi {

    private final PaymentService paymentService;

    @Override
    public ResponseEntity<PaymentResponse> createPayment(String idempotencyKey, PaymentRequest paymentRequest) {
        return ResponseEntity.ok(
                paymentService.createPayment(ApiKeyFilter.currentClientId(), idempotencyKey, paymentRequest));
    }

    @Override
    public ResponseEntity<PaymentResponse> getPayment(String paymentId) {
        return ResponseEntity.ok(paymentService.getPayment(ApiKeyFilter.currentClientId(), paymentId));
    }
}
