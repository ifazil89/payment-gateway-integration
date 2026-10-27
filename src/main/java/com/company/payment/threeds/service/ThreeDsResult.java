package com.company.payment.threeds.service;

import com.company.payment.payment.model.PaymentStatus;

/** Where the shopper's browser goes after the challenge. returnUrl null = no redirect. */
public record ThreeDsResult(String paymentId, PaymentStatus status, String returnUrl) {
}
