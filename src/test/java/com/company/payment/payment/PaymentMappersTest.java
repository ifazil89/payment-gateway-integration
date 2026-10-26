package com.company.payment.payment;

import com.company.payment.payment.model.Amount;
import com.company.payment.payment.model.BillingInfo;
import com.company.payment.payment.model.CardInfo;
import com.company.payment.payment.model.CardType;
import com.company.payment.payment.model.DeviceInfo;
import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.payment.model.PaymentStatus;
import com.company.payment.ni.dto.NiBrowserInfo;
import com.company.payment.ni.dto.NiOrderRequest;
import com.company.payment.payment.entity.Payment;
import com.company.payment.payment.entity.PaymentCardType;
import com.company.payment.payment.entity.PaymentState;
import com.company.payment.payment.mapper.PaymentMapper;
import com.company.payment.payment.mapper.PaymentNiMapper;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/** The MapStruct mappers against the models generated from payment-api.yaml, without Spring. */
class PaymentMappersTest {

    private final PaymentMapper paymentMapper = Mappers.getMapper(PaymentMapper.class);
    private final PaymentNiMapper niMapper = Mappers.getMapper(PaymentNiMapper.class);

    private static PaymentRequest request(String amount, String currency) {
        return new PaymentRequest()
                .merchantOrderId("SO-1")
                .cardType(CardType.CARD)
                .card(new CardInfo().pan("4111111111111111").expiry("2030-12").cvv("123").cardholderName("Jane Doe"))
                .amount(new Amount().value(new BigDecimal(amount)).currency(currency))
                .billingInfo(new BillingInfo().firstName("Jane").lastName("Doe").email("jane@example.com")
                        .address("Street 1").city("Dubai").countryCode("AE"))
                .deviceInfo(new DeviceInfo().ip("203.0.113.10").userAgent("UA").acceptHeader("text/html")
                        .language("en").timeZoneOffset("-240").screenWidth("1440").screenHeight("900")
                        .colorDepth("24").javascriptEnabled(true).javaEnabled(false));
    }

    @Test
    void orderRequestUsesMinorUnitsAndAuthAction() {
        NiOrderRequest order = niMapper.toOrderRequest(request("250.00", "AED"));

        assertThat(order.action()).isEqualTo("AUTH");
        assertThat(order.amount().value()).isEqualTo(25000L);
        assertThat(order.merchantOrderReference()).isEqualTo("SO-1");
        assertThat(order.billingAddress().address1()).isEqualTo("Street 1");

        assertThat(niMapper.toOrderRequest(request("1.25", "KWD")).amount().value()).isEqualTo(1250L);   // 3 decimals
    }

    @Test
    void browserInfoUsesFullScreenChallengeByDefault() {
        NiBrowserInfo browser = niMapper.toBrowserInfo(request("1", "AED").getDeviceInfo());

        assertThat(browser.challengeWindowSize()).isEqualTo("05");
        assertThat(browser.browserTz()).isEqualTo("-240");
        assertThat(browser.browserIp()).isEqualTo("203.0.113.10");
    }

    @Test
    void entityKeepsOnlyTheMaskedPan() {
        Payment payment = paymentMapper.toEntity(request("250.00", "AED"));

        assertThat(payment.getMaskedPan()).isEqualTo("411111******1111");
        assertThat(payment.getCardType()).isEqualTo(PaymentCardType.CARD);
        assertThat(payment.getEmail()).isEqualTo("jane@example.com");
    }

    @Test
    void responseHasCurrencyScaleAndUtcTime() {
        Payment payment = paymentMapper.toEntity(request("250", "AED"));
        payment.setAmount(new BigDecimal("250.000"));          // as read back from NUMERIC(19,3)
        payment.start("client", "key", "hash");

        var response = paymentMapper.toResponse(payment);

        assertThat(response.getAmount().getValue()).isEqualByComparingTo("250.00");
        assertThat(response.getAmount().getValue().scale()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(PaymentStatus.PENDING);
        assertThat(response.getCreatedAt().getOffset().getTotalSeconds()).isZero();
    }

    @Test
    void internalStatesShownAsPending() {
        assertThat(paymentMapper.toStatus(PaymentState.CREATED)).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentMapper.toStatus(PaymentState.AWAITING_3DS)).isEqualTo(PaymentStatus.PENDING);
        assertThat(paymentMapper.toStatus(PaymentState.CHALLENGE_REQUIRED)).isEqualTo(PaymentStatus.CHALLENGE_REQUIRED);
    }

    @Test
    void niStatesAreMapped() {
        assertThat(PaymentState.fromNi("AWAIT_3DS")).isEqualTo(PaymentState.AWAITING_3DS);
        assertThat(PaymentState.fromNi("SOMETHING_NEW")).isNull();
    }
}
