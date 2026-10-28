package com.company.payment.order.controller;

import com.company.payment.common.security.ApiKeyFilter;
import com.company.payment.order.api.OrdersApi;
import com.company.payment.order.model.OrderStatusResponse;
import com.company.payment.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;

/** Implements OrdersApi (order/api): hands over to OrderService. */
@RestController
@RequiredArgsConstructor
public class OrderController implements OrdersApi {

    private final OrderService orderService;

    @Override
    public ResponseEntity<OrderStatusResponse> getOrder(String orderReference) {
        return ResponseEntity.ok(orderService.retrieveOrder(ApiKeyFilter.currentClientId(), orderReference));
    }
}
