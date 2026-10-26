package com.company.payment.payment.validation;

import com.company.payment.payment.model.CardInfo;
import com.company.payment.payment.model.CardType;
import com.company.payment.payment.model.PaymentRequest;
import com.company.payment.common.exception.ApiException;
import com.company.payment.common.exception.ApiException.FieldViolation;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Rules the OpenAPI schema can't express (field formats, lengths and required fields are already
 * checked by Bean Validation on the generated models before this runs).
 *
 *   cardType CARD          -> card.pan required, card.token must be empty
 *   cardType CARD_ON_FILE  -> card.token required, card.pan must be empty
 *   card.expiry            -> not in the past
 *
 * Errors come back exactly like Bean Validation errors: 400 VALIDATION_ERROR + field list.
 */
@Component
public class PaymentRequestValidator {

    private final Clock clock;

    public PaymentRequestValidator() {
        this(Clock.systemUTC());
    }

    PaymentRequestValidator(Clock clock) {
        this.clock = clock;
    }

    public void validate(PaymentRequest request) {
        List<FieldViolation> violations = new ArrayList<>();
        CardInfo card = request.getCard();

        if (request.getCardType() == CardType.CARD) {
            if (isBlank(card.getPan())) {
                violations.add(new FieldViolation("card.pan", "is required for cardType CARD"));
            }
            if (!isBlank(card.getToken())) {
                violations.add(new FieldViolation("card.token", "must be empty for cardType CARD"));
            }
        } else if (request.getCardType() == CardType.CARD_ON_FILE) {
            if (isBlank(card.getToken())) {
                violations.add(new FieldViolation("card.token", "is required for cardType CARD_ON_FILE"));
            }
            if (!isBlank(card.getPan())) {
                violations.add(new FieldViolation("card.pan", "must be empty for cardType CARD_ON_FILE"));
            }
        }

        // format YYYY-MM is guaranteed by the schema pattern
        if (card.getExpiry() != null && YearMonth.parse(card.getExpiry()).isBefore(YearMonth.now(clock))) {
            violations.add(new FieldViolation("card.expiry", "card has expired"));
        }

        if (!violations.isEmpty()) {
            throw ApiException.validation(violations);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
