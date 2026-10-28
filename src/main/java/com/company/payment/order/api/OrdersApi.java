package com.company.payment.order.api;

import com.company.payment.order.model.OrderStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

/**
 * HTTP contract of the order feature. OrderController implements it; payment-api.yaml documents it
 * (keep it in step).
 */
public interface OrdersApi {

    String PATH_GET_ORDER = "/api/v1/orders/{orderReference}";

    /**
     * GET /api/v1/orders/{orderReference}: the order's live status at N-Genius ("retrieve order"), for one of
     * the client's own payments (orderReference from the payment response). A pending payment whose outcome
     * N-Genius already knows (e.g. after a timeout) is updated, so GET /api/v1/payments/{id} shows it too.
     *
     * @return 200 OrderStatusResponse; 401 UNAUTHORIZED; 404 ORDER_NOT_FOUND (unknown, or another client's);
     *         422 GATEWAY_REJECTED, 502 GATEWAY_ERROR, 504 GATEWAY_TIMEOUT (nothing changed: safe to call again)
     */
    @RequestMapping(
            method = RequestMethod.GET,
            value = PATH_GET_ORDER,
            produces = {"application/json"})
    ResponseEntity<OrderStatusResponse> getOrder(@PathVariable("orderReference") String orderReference);
}
