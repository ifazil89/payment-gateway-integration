package com.company.payment.ni.dto;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/** The "retrieve order" example of the N-Genius docs (reference/retrieve-order-status), shortened, binds as expected. */
class NiOrderResponseTest {

    private static final String DOC_EXAMPLE = """
            {
              "_id": "urn:order:cac88045-bec0-432a-8fdd-06902da874d2",
              "_links": { "self": { "href": "https://api-gateway.sandbox.ngenius-payments.com/transactions/outlets/670bfc06/orders/cac88045" } },
              "type": "SINGLE",
              "action": "AUTH",
              "amount": { "currencyCode": "AED", "value": 12345 },
              "reference": "cac88045-bec0-432a-8fdd-06902da874d2",
              "outletId": "670bfc06-63d6-472b-af75-ad0207ac44f5",
              "createDateTime": "2025-04-15T08:25:08.171027365Z",
              "formattedAmount": "د.إ. 123.45",
              "_embedded": {
                "payment": [
                  {
                    "_id": "urn:payment:b0d21130-f683-442a-b7b3-397461374cdf",
                    "_links": {
                      "cnp:3ds2-challenge-response": { "href": "https://api-gateway.sandbox.ngenius-payments.com/.../card/3ds2/challenge-response" },
                      "curies": [ { "name": "cnp", "href": "https://api-gateway.sandbox.ngenius-payments.com/docs/rels/{rel}", "templated": true } ]
                    },
                    "reference": "b0d21130-f683-442a-b7b3-397461374cdf",
                    "paymentMethod": { "expiry": "2029-04", "cardholderName": "John Brown", "name": "VISA", "pan": "411111******1111" },
                    "state": "AWAIT_3DS",
                    "amount": { "currencyCode": "AED", "value": 12345 },
                    "updateDateTime": "2025-04-15T08:25:10.757199051Z",
                    "orderReference": "cac88045-bec0-432a-8fdd-06902da874d2",
                    "3ds2": { "messageVersion": "2.1.0", "threeDSMethodURL": "https://paypage-uat.ngenius-payments.com/fake/3ds2/method" },
                    "mid": "10089865858"
                  }
                ]
              }
            }
            """;

    @Test
    void docExampleBinds() {
        NiOrderResponse order = JsonMapper.builder().build().readValue(DOC_EXAMPLE, NiOrderResponse.class);

        assertThat(order.reference()).isEqualTo("cac88045-bec0-432a-8fdd-06902da874d2");
        assertThat(order.action()).isEqualTo("AUTH");
        assertThat(order.amount()).isEqualTo(new NiAmount("AED", 12345L));
        assertThat(order.createDateTime()).isEqualTo(OffsetDateTime.parse("2025-04-15T08:25:08.171027365Z"));

        NiPaymentResponse payment = order.payment("b0d21130-f683-442a-b7b3-397461374cdf");
        assertThat(payment.state()).isEqualTo("AWAIT_3DS");
        assertThat(payment.paymentMethod()).isEqualTo(new NiPaymentMethod("VISA", "411111******1111"));
        assertThat(payment.updateDateTime()).isEqualTo(OffsetDateTime.parse("2025-04-15T08:25:10.757199051Z"));
        assertThat(payment.threeDs().threeDsMethodUrl()).isEqualTo("https://paypage-uat.ngenius-payments.com/fake/3ds2/method");
        assertThat(payment.link(NiLinks.THREEDS_CHALLENGE_RESPONSE)).isPresent();
    }

    @Test
    void unknownPaymentReferenceFallsBackToTheFirstPayment() {
        NiOrderResponse order = JsonMapper.builder().build().readValue(DOC_EXAMPLE, NiOrderResponse.class);

        assertThat(order.payment("other").reference()).isEqualTo("b0d21130-f683-442a-b7b3-397461374cdf");
        assertThat(order.payment(null).reference()).isEqualTo("b0d21130-f683-442a-b7b3-397461374cdf");
    }
}
