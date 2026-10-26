package com.company.payment.ni.dto;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Link relations in N-Genius "_links" blocks, and a helper to read them.
 * If a call fails with "has no link", compare these names with a real sandbox response.
 */
public final class NiLinks {

    public static final String CARD = "payment:card";
    public static final String SAVED_CARD = "payment:saved-card";
    public static final String THREEDS_AUTHENTICATION = "cnp:3ds2-authentication";
    public static final String THREEDS_CHALLENGE_RESPONSE = "cnp:3ds2-challenge-response";
    public static final String CAPTURE = "cnp:capture";
    public static final String CANCEL = "cnp:cancel";

    private NiLinks() {
    }

    /** Values are usually {"href": "..."}; some (e.g. "curies") are arrays. */
    static Optional<String> href(Map<String, Object> links, String relation) {
        if (links == null) {
            return Optional.empty();
        }
        Object value = links.get(relation);
        if (value instanceof Map<?, ?> link && link.get("href") instanceof String href) {
            return Optional.of(href);
        }
        if (value instanceof List<?> list && !list.isEmpty()
                && list.getFirst() instanceof Map<?, ?> first && first.get("href") instanceof String href) {
            return Optional.of(href);
        }
        return Optional.empty();
    }
}
