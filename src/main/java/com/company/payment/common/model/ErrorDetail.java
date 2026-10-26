package com.company.payment.common.model;

/** One invalid field, e.g. ("card.pan", "is required for cardType CARD"). */
public record ErrorDetail(String field, String message) {
}
