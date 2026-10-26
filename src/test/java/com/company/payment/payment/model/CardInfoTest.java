package com.company.payment.payment.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CardInfoTest {

    @Test
    void toStringNeverShowsCardData() {
        CardInfo card = new CardInfo().pan("4111111111111111").token("saved-token-123").cvv("123")
                .expiry("2030-12").cardholderName("Jane Doe");

        assertThat(card.toString())
                .doesNotContain("4111111111111111", "saved-token-123", "123\n")
                .contains("pan: ***", "token: ***", "cvv: ***", "expiry: 2030-12", "cardholderName: Jane Doe");
    }
}
