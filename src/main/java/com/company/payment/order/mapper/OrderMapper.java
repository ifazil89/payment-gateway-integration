package com.company.payment.order.mapper;

import com.company.payment.common.util.Amounts;
import com.company.payment.ni.dto.NiAmount;
import com.company.payment.ni.dto.NiOrderResponse;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.order.model.OrderStatusResponse;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.mapper.PaymentMapper;
import com.company.payment.payment.model.Amount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** N-Genius order + its payment + our Payment -> OrderStatusResponse. status uses PaymentMapper.toStatus. */
@Mapper(uses = PaymentMapper.class)
public interface OrderMapper {

    @Mapping(target = "orderReference", source = "order.reference")
    @Mapping(target = "paymentId", source = "payment.id")
    @Mapping(target = "merchantOrderId", source = "payment.merchantOrderId")
    @Mapping(target = "action", source = "order.action")
    @Mapping(target = "amount", source = "order.amount")
    @Mapping(target = "status", source = "payment.state")
    @Mapping(target = "gatewayState", source = "niPayment.state")
    @Mapping(target = "cardBrand", source = "niPayment.paymentMethod.name")
    @Mapping(target = "maskedPan", source = "niPayment.paymentMethod.pan")
    @Mapping(target = "orderCreatedAt", source = "order.createDateTime")
    @Mapping(target = "updatedAt", source = "niPayment.updateDateTime")
    OrderStatusResponse toResponse(NiOrderResponse order, NiPaymentResponse niPayment, Payment payment);

    /** Minor units -> major units: AED 25000 -> 250.00. */
    default Amount toAmount(NiAmount amount) {
        if (amount == null || amount.value() == null) {
            return null;
        }
        return new Amount(Amounts.fromMinorUnits(amount.value(), amount.currencyCode()), amount.currencyCode());
    }
}
