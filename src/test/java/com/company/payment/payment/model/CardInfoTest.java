package com.company.payment.payment.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardInfoTest {

    @Test
    void toStringNeverShowsCardData() {
        CardInfo card = new CardInfo("4111111111111111", "saved-token-123", "2030-12", "123", "Jane Doe");

        assertThat(card.toString())
                .doesNotContain("4111111111111111", "saved-token-123", "=123")
                .contains("pan=***", "token=***", "cvv=***", "expiry=2030-12", "cardholderName=Jane Doe");
        assertThat(new PaymentRequest("SO-1", CardType.CARD, card, null, null, null).toString())
                .doesNotContain("4111111111111111", "=123");
    }
}
