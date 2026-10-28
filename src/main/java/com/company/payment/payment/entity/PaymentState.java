package com.company.payment.payment.entity;

import java.util.Locale;

/**
 * Internal state of a payment (stored). Mapped to the API's PaymentStatus by PaymentMapper:
 * CREATED and AWAITING_3DS are shown to clients as PENDING.
 * (Named "State" so it is never confused with the API enum PaymentStatus.)
 */
public enum PaymentState {

    CREATED,              // saved, N-Genius not answered yet
    AWAITING_3DS,         // N-Genius wants 3DS, or is finishing it after the shopper's challenge
    CHALLENGE_REQUIRED,   // shopper must complete a 3DS challenge
    AUTHORISED,           // money reserved, ready to capture
    DECLINED,
    FAILED;

    /** The outcome is known: N-Genius will not change it any more (capture and refund are separate steps). */
    public boolean isFinal() {
        return this == AUTHORISED || this == DECLINED || this == FAILED;
    }

    /** N-Genius payment state -> our state. null = unknown state: keep the current one (logged). */
    public static PaymentState fromNi(String niState) {
        if (niState == null) {
            return null;
        }
        return switch (niState.toUpperCase(Locale.ROOT)) {
            case "STARTED" -> CREATED;
            case "AWAIT_3DS" -> AWAITING_3DS;
            case "AUTHORISED", "AUTHORIZED" -> AUTHORISED;   // the EMV 3DS guide spells it both ways
            case "DECLINED" -> DECLINED;
            case "FAILED" -> FAILED;
            default -> null;
        };
    }
}
