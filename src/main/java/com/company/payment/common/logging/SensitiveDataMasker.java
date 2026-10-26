package com.company.payment.common.logging;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.StringJoiner;

/**
 * Makes payloads and headers safe to log (PCI DSS: no full card number, never a CVV).
 *
 *   "pan": "4111111111111111"  -> "pan": "411111******1111"   (first 6 + last 4, allowed by PCI DSS)
 *   "cvv" / "token" / "cardToken" / "access_token" / "refresh_token" / "password" -> "***"
 *   headers Authorization, Proxy-Authorization, X-Api-Key, Cookie, Set-Cookie   -> "***"
 *
 * Fields are found at any depth (objects, arrays). A secret field whose value is an object or an array
 * is masked as a whole: "token": {"value": "abc"} -> "token": "***". JSON carried inside a string
 * value ("body": "{\"pan\": ...}") is masked too.
 *
 * Works on raw JSON text in one pass, keeping its formatting. Also works on invalid or cut-off JSON
 * (a value running to the end of the text is still masked), so it never needs to parse the body.
 * Add a field name (lower case) to SECRET_FIELDS when a new API carries a new secret.
 */
@RequiredArgsConstructor
public class SensitiveDataMasker {

    static final String MASK = "***";

    private static final String PAN_FIELD = "pan";
    private static final Set<String> SECRET_FIELDS =
            Set.of("cvv", "token", "cardtoken", "access_token", "refresh_token", "password");
    private static final Set<String> SECRET_HEADERS =
            Set.of("authorization", "proxy-authorization", "x-api-key", "cookie", "set-cookie");
    /** JSON inside a string inside a string ...: deeper than this is left as it is. */
    private static final int MAX_EMBEDDED_JSON_DEPTH = 5;

    private final LoggingProperties properties;

    /** Body for the log: masked, cut to the max length; binary content is not logged. */
    public String body(byte[] content, String contentType) {
        if (!properties.logBodies()) {
            return "[not logged]";
        }
        if (content == null || content.length == 0) {
            return "";
        }
        if (!isText(contentType)) {
            return "[" + content.length + " bytes " + contentType + "]";
        }
        return truncate(maskJson(new String(content, StandardCharsets.UTF_8)));
    }

    public String maskJson(String json) {
        if (json == null || json.isEmpty()) {
            return json;
        }
        return new JsonMasking(json, 0).run();
    }

    public String headers(HttpHeaders headers) {
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        headers.forEach((name, values) -> joiner.add(name + "=" + headerValue(name, String.join(",", values))));
        return joiner.toString();
    }

    public String headerValue(String name, String value) {
        return SECRET_HEADERS.contains(name.toLowerCase(Locale.ROOT)) ? MASK : value;
    }

    /** 4111111111111111 -> 411111******1111; too short to be a card number -> *** */
    static String maskPan(String pan) {
        String digits = pan.replaceAll("\\D", "");
        if (digits.length() < 10) {
            return MASK;
        }
        return digits.substring(0, 6) + "*".repeat(digits.length() - 10) + digits.substring(digits.length() - 4);
    }

    private String truncate(String text) {
        int max = properties.maxBodyLength();
        return text.length() <= max ? text : text.substring(0, max) + "...[truncated, " + text.length() + " chars]";
    }

    private static String unquote(String value) {
        String result = value.startsWith("\"") ? value.substring(1) : value;
        return result.endsWith("\"") ? result.substring(0, result.length() - 1) : result;
    }

    private static boolean isSecret(String field) {
        String name = field.toLowerCase(Locale.ROOT);
        return name.equals(PAN_FIELD) || SECRET_FIELDS.contains(name);
    }

    /** pan (string or number) -> first 6 + last 4; anything else, also an object or array -> ***; null stays null. */
    private static String maskedValue(String field, String rawValue) {
        if (rawValue.equals("null")) {
            return rawValue;
        }
        boolean scalar = !rawValue.startsWith("{") && !rawValue.startsWith("[");
        boolean pan = field.equalsIgnoreCase(PAN_FIELD);
        return "\"" + (pan && scalar ? maskPan(unquote(rawValue)) : MASK) + "\"";
    }

    /**
     * One pass over the JSON text. Every quoted string is read whole; a string followed by ':' is a key.
     * A secret key's value (string, number, object or array) is replaced; any other value is copied,
     * so the scan continues into nested objects and arrays by itself.
     */
    private static final class JsonMasking {

        private final String text;
        private final int depth;
        private final StringBuilder out;
        private int pos;

        JsonMasking(String text, int depth) {
            this.text = text;
            this.depth = depth;
            this.out = new StringBuilder(text.length());
        }

        String run() {
            while (pos < text.length()) {
                if (text.charAt(pos) != '"') {
                    out.append(text.charAt(pos++));
                    continue;
                }
                int start = pos;
                int end = stringEnd(start);
                int colon = skipWhitespace(end);
                if (colon < text.length() && text.charAt(colon) == ':') {
                    int valueStart = skipWhitespace(colon + 1);
                    out.append(text, start, valueStart);              // "key" : (formatting kept)
                    pos = valueStart;
                    String field = unquote(text.substring(start, end));
                    if (isSecret(field) && pos < text.length()) {
                        int valueEnd = valueEnd(pos);
                        out.append(maskedValue(field, text.substring(pos, valueEnd)));
                        pos = valueEnd;
                    }
                } else {
                    out.append(embeddedJson(text.substring(start, end)));
                    pos = end;
                }
            }
            return out.toString();
        }

        /** A string value holding JSON ("{\"pan\":...}"): masked like any other JSON, then escaped again. */
        private String embeddedJson(String token) {
            if (depth >= MAX_EMBEDDED_JSON_DEPTH || token.length() < 2 || !token.endsWith("\"")) {
                return token;
            }
            String inner = token.substring(1, token.length() - 1).strip();
            if (!inner.startsWith("{") && !inner.startsWith("[")) {
                return token;
            }
            String json = unescape(token.substring(1, token.length() - 1));
            String masked = new JsonMasking(json, depth + 1).run();
            return masked.equals(json) ? token : "\"" + escape(masked) + "\"";
        }

        /** Index after the closing quote, or the end of a cut-off text. */
        private int stringEnd(int start) {
            int i = start + 1;
            while (i < text.length()) {
                char c = text.charAt(i);
                if (c == '\\') {
                    i += 2;
                } else if (c == '"') {
                    return i + 1;
                } else {
                    i++;
                }
            }
            return text.length();
        }

        /** Index after a value: string, object/array (balanced, strings skipped) or number/true/false/null. */
        private int valueEnd(int start) {
            char first = text.charAt(start);
            if (first == '"') {
                return stringEnd(start);
            }
            if (first == '{' || first == '[') {
                int nesting = 0;
                int i = start;
                while (i < text.length()) {
                    char c = text.charAt(i);
                    if (c == '"') {
                        i = stringEnd(i);
                        continue;
                    }
                    if (c == '{' || c == '[') {
                        nesting++;
                    } else if ((c == '}' || c == ']') && --nesting == 0) {
                        return i + 1;
                    }
                    i++;
                }
                return text.length();
            }
            int i = start;
            while (i < text.length() && ",}] \t\r\n".indexOf(text.charAt(i)) < 0) {
                i++;
            }
            return i;
        }

        private int skipWhitespace(int from) {
            int i = from;
            while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
                i++;
            }
            return i;
        }
    }

    private static String unescape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c != '\\' || i + 1 >= s.length()) {
                sb.append(c);
                continue;
            }
            char next = s.charAt(++i);
            switch (next) {
                case 'n' -> sb.append('\n');
                case 'r' -> sb.append('\r');
                case 't' -> sb.append('\t');
                case 'b' -> sb.append('\b');
                case 'f' -> sb.append('\f');
                case 'u' -> {
                    if (i + 4 < s.length()) {
                        try {
                            sb.append((char) Integer.parseInt(s.substring(i + 1, i + 5), 16));
                            i += 4;
                        } catch (NumberFormatException ex) {
                            sb.append("\\u");
                        }
                    } else {
                        sb.append("\\u");
                    }
                }
                default -> sb.append(next);                           // \" \\ \/
            }
        }
        return sb.toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.toString();
    }

    private static boolean isText(String contentType) {
        if (contentType == null) {
            return true;   // N-Genius sometimes omits it on errors; masking still applies
        }
        try {
            MediaType type = MediaType.parseMediaType(contentType);
            return "text".equals(type.getType())
                    || type.getSubtype().contains("json")          // application/json, application/vnd.ni-payment.v2+json
                    || type.getSubtype().contains("xml")
                    || type.isCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED);
        } catch (RuntimeException ex) {
            return false;
        }
    }
}
