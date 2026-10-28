package com.company.payment.order.service;

import com.company.payment.common.exception.ApiException;
import com.company.payment.common.logging.LogFields;
import com.company.payment.ni.client.NiClient;
import com.company.payment.ni.dto.NiOrderResponse;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.order.mapper.OrderMapper;
import com.company.payment.order.model.OrderStatusResponse;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.entity.PaymentState;
import com.company.payment.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;

/**
 * GET /api/v1/orders/{orderReference}: N-Genius "retrieve order", step by step.
 *
 *   1. Find our payment for this order, for this client only (another client's order is not found)
 *   2. N-Genius: GET the order (the source of truth; there is no order-level state, the payment carries it)
 *   3. Bring the stored payment up to date: a pending payment (e.g. after a timeout) takes N-Genius' final
 *      outcome (Payment.niStateRetrieved). Saved under the optimistic lock; if a 3DS result is saved at the
 *      same moment, that one wins and is returned.
 *   4. Answer with the order and our status
 *
 * N-Genius errors are the usual API errors: 4xx -> 422, 5xx -> 502, timeout -> 504 (nothing is changed).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OrderService {

    private final PaymentRepository repository;
    private final NiClient niClient;
    private final OrderMapper orderMapper;

    public OrderStatusResponse retrieveOrder(String clientId, String orderReference) {
        // 1. Find
        Payment payment = repository.findByNiOrderReferenceAndClientId(orderReference, clientId)
                .orElseThrow(() -> ApiException.notFound("ORDER_NOT_FOUND", "No order " + orderReference));

        try (MDC.MDCCloseable p = MDC.putCloseable(LogFields.PAYMENT_ID, payment.getId())) {
            // 2. N-Genius
            NiOrderResponse order = niClient.getOrder(orderReference);
            NiPaymentResponse niPayment = order.payment(payment.getNiPaymentReference());

            // 3. Update the stored payment
            PaymentState before = payment.getState();
            if (payment.niStateRetrieved(niPayment.state())) {
                try {
                    payment = repository.saveAndFlush(payment);
                } catch (ObjectOptimisticLockingFailureException ex) {
                    log.info("Payment changed while the order was retrieved; keeping the newer version");
                    payment = repository.findById(payment.getId()).orElseThrow();
                }
            }

            log.atInfo()
                    .setMessage("Order retrieved: N-Genius state {}, payment {} -> {}")
                    .addArgument(niPayment.state()).addArgument(before).addArgument(payment.getState())
                    .addKeyValue("payment.ni_order_reference", orderReference)
                    .addKeyValue("payment.ni_state", niPayment.state())
                    .addKeyValue("payment.state_before", before.name())
                    .addKeyValue("payment.state", payment.getState().name())
                    .log();

            // 4. Answer
            return orderMapper.toResponse(order, niPayment, payment);
        }
    }
}
