package com.company.payment.payment.mapper;

import com.company.payment.payment.model.Amount;
import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.payment.model.PaymentResponse;
import com.company.payment.payment.model.PaymentStatus;
import com.company.payment.common.util.Amounts;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.entity.PaymentState;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * API models (payment/model) <-> Payment entity.
 * MapStruct generates the implementation at compile time (target/generated-sources/annotations).
 */
@Mapper
public interface PaymentMapper {

    /**
     * New entity from the request. Only the request fields are copied here; ids, state and
     * N-Genius references are set by PaymentService (hence IGNORE for this method only).
     * Card number and CVV are NOT copied: only the masked PAN.
     */
    @BeanMapping(unmappedTargetPolicy = ReportingPolicy.IGNORE)
    @Mapping(target = "amount", source = "amount.value")
    @Mapping(target = "currency", source = "amount.currency")
    @Mapping(target = "email", source = "billingInfo.email")
    @Mapping(target = "maskedPan", source = "card.pan", qualifiedByName = "maskPan")
    Payment toEntity(PaymentRequest request);

    /**
     * status and amount are explicit expressions: MapStruct calls exactly these methods
     * (no type-based lookup, no auto-generated sub-mapping).
     */
    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "status", expression = "java(toStatus(payment.getState()))")
    @Mapping(target = "amount", expression = "java(toAmount(payment))")
    @Mapping(target = "orderReference", source = "niOrderReference")
    PaymentResponse toResponse(Payment payment);

    /**
     * Internal state -> API status. CREATED and AWAITING_3DS are shown as PENDING
     * (the client doesn't need to tell them apart).
     * A plain switch: a new PaymentState that is not mapped here is a compile error.
     */
    default PaymentStatus toStatus(PaymentState state) {
        if (state == null) {
            return null;
        }
        return switch (state) {
            case CREATED, AWAITING_3DS -> PaymentStatus.PENDING;
            case CHALLENGE_REQUIRED -> PaymentStatus.CHALLENGE_REQUIRED;
            case AUTHORISED -> PaymentStatus.AUTHORISED;
            case DECLINED -> PaymentStatus.DECLINED;
            case FAILED -> PaymentStatus.FAILED;
        };
    }

    /** Always with the currency's scale: 250 / 250.000 (from the DB) -> 250.00 for AED. */
    default Amount toAmount(Payment payment) {
        return new Amount(Amounts.normalize(payment.getAmount().stripTrailingZeros(), payment.getCurrency()),
                payment.getCurrency());
    }

    /** Entity times are Instants (UTC); the API uses date-time (OffsetDateTime). */
    default OffsetDateTime toOffsetDateTime(Instant instant) {
        return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
    }

    /** 4111111111111111 -> 411111******1111 (PCI DSS allows first 6 + last 4). null for CARD_ON_FILE. */
    @Named("maskPan")
    default String maskPan(String pan) {
        if (pan == null || pan.length() < 10) {
            return null;
        }
        return pan.substring(0, 6) + "*".repeat(pan.length() - 10) + pan.substring(pan.length() - 4);
    }
}
