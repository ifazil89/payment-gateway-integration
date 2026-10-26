package com.company.payment.common.logging;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** PCI DSS: no full card number, CVV, token or credential may ever reach a log line. */
class SensitiveDataMaskerTest {

    private final SensitiveDataMasker masker = new SensitiveDataMasker(new LoggingProperties(true, 10_000));

    @Test
    void masksCardDataAndTokensButKeepsTheRest() {
        String json = """
                {"card":{"pan":"4111111111111111","expiry":"2030-12","cvv":"123","cardholderName":"Jane"},\
                "cardToken" : "tok-abc","access_token":"eyJx","email":"jane@example.com"}""";

        String masked = masker.maskJson(json);

        assertThat(masked)
                .contains("\"pan\":\"411111******1111\"", "\"cvv\":\"***\"", "\"cardToken\" : \"***\"",
                        "\"access_token\":\"***\"", "\"expiry\":\"2030-12\"", "jane@example.com")
                .doesNotContain("4111111111111111", "tok-abc", "eyJx");
    }

    @Test
    void masksNumericCutOffAndEscapedValues() {
        assertThat(masker.maskJson("{\"pan\": 4111111111111111}")).isEqualTo("{\"pan\": \"411111******1111\"}");
        assertThat(masker.maskJson("{\"pan\":\"41111111111")).doesNotContain("41111111111");     // body cut mid-value
        assertThat(masker.maskJson("{\"token\":\"a\\\"b\",\"x\":1}")).isEqualTo("{\"token\":\"***\",\"x\":1}");
    }

    @Test
    void masksFieldsAtAnyDepthInObjectsAndArrays() {
        String json = """
                {"order":{"payments":[{"card":{"pan":"5555555555554444","cvv":"999"}},\
                {"savedCard":{"cardToken":"tok-1","maskedPan":"411111******1111"}}]}}""";

        assertThat(masker.maskJson(json))
                .contains("\"pan\":\"555555******4444\"", "\"cvv\":\"***\"", "\"cardToken\":\"***\"",
                        "\"maskedPan\":\"411111******1111\"")
                .doesNotContain("5555555555554444", "999", "tok-1");
    }

    @Test
    void secretFieldWithAnObjectOrArrayValueIsMaskedAsAWhole() {
        assertThat(masker.maskJson("{\"token\":{\"value\":\"abc\",\"type\":\"x\"},\"next\":1}"))
                .isEqualTo("{\"token\":\"***\",\"next\":1}");
        assertThat(masker.maskJson("{\"password\": [\"a\", {\"b\": \"c\"}], \"user\": \"jane\"}"))
                .isEqualTo("{\"password\": \"***\", \"user\": \"jane\"}");
        assertThat(masker.maskJson("{\"pan\":{\"number\":\"4111111111111111\"}}"))
                .isEqualTo("{\"pan\":\"***\"}");
        // cut off inside the child: everything after the key is masked
        assertThat(masker.maskJson("{\"token\":{\"value\":\"abc")).isEqualTo("{\"token\":\"***\"");
    }

    @Test
    void masksJsonCarriedInsideAStringValue() {
        String json = "{\"responseBody\":\"{\\\"pan\\\":\\\"4111111111111111\\\",\\\"cvv\\\":\\\"123\\\",\\\"state\\\":\\\"FAILED\\\"}\"}";

        assertThat(masker.maskJson(json))
                .isEqualTo("{\"responseBody\":\"{\\\"pan\\\":\\\"411111******1111\\\",\\\"cvv\\\":\\\"***\\\",\\\"state\\\":\\\"FAILED\\\"}\"}");
        // a plain string is left untouched, even if it mentions a field name
        assertThat(masker.maskJson("{\"message\":\"pan is required\"}")).isEqualTo("{\"message\":\"pan is required\"}");
    }

    @Test
    void keepsFormattingAndNullValues() {
        String pretty = """
                {
                  "card" : {
                    "pan" : "4111111111111111",
                    "token" : null
                  }
                }""";

        assertThat(masker.maskJson(pretty)).isEqualTo("""
                {
                  "card" : {
                    "pan" : "411111******1111",
                    "token" : null
                  }
                }""");
    }

    @Test
    void masksSecretHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer secret");
        headers.add("X-Api-Key", "sandbox-key");
        headers.add("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        assertThat(masker.headers(headers))
                .contains("Authorization=***", "X-Api-Key=***", "traceparent=00-4bf92f")
                .doesNotContain("secret", "sandbox-key");
    }

    @Test
    void bodyIsTruncatedAndBinaryIsNotLogged() {
        byte[] json = "{\"pan\":\"4111111111111111\",\"long\":\"xxxxxxxxxxxxxxxxxxxxxxxxxxxxxx\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(new SensitiveDataMasker(new LoggingProperties(true, 30)).body(json, "application/json"))
                .contains("411111******1111").contains("[truncated");
        assertThat(masker.body(new byte[]{1, 2}, "image/png")).isEqualTo("[2 bytes image/png]");
        assertThat(new SensitiveDataMasker(new LoggingProperties(false, 30)).body(json, "application/json"))
                .isEqualTo("[not logged]");
    }
}
