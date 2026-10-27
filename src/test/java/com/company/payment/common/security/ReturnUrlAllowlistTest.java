package com.company.payment.common.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** returnUrl must never turn this service into an open redirect. */
class ReturnUrlAllowlistTest {

    private final ReturnUrlAllowlist allowlist = ReturnUrlAllowlist.parse(
            "shop:https://shop.example.com/checkout/, shop:https://m.shop.example.com, other:http://localhost:3000/");

    @Test
    void allowsUrlsUnderTheClientsEntries() {
        assertThat(allowlist.isAllowed("shop", "https://shop.example.com/checkout/done?order=1")).isTrue();
        assertThat(allowlist.isAllowed("shop", "https://SHOP.example.com:443/checkout/")).isTrue();
        assertThat(allowlist.isAllowed("shop", "https://m.shop.example.com/any/path")).isTrue();
        assertThat(allowlist.isAllowed("other", "http://localhost:3000/result")).isTrue();
    }

    @Test
    void rejectsEverythingElse() {
        assertThat(allowlist.isAllowed("shop", "https://shop.example.com/admin")).isFalse();              // other path
        assertThat(allowlist.isAllowed("shop", "https://shop.example.com/checkout/../admin")).isFalse(); // normalized
        assertThat(allowlist.isAllowed("shop", "https://shop.example.com.evil.com/checkout/")).isFalse(); // other host
        assertThat(allowlist.isAllowed("shop", "http://shop.example.com/checkout/")).isFalse();          // other scheme
        assertThat(allowlist.isAllowed("shop", "https://shop.example.com:8443/checkout/")).isFalse();    // other port
        assertThat(allowlist.isAllowed("shop", "https://evil@shop.example.com/checkout/")).isFalse();    // user info
        assertThat(allowlist.isAllowed("shop", "javascript:alert(1)")).isFalse();
        assertThat(allowlist.isAllowed("shop", "/checkout/")).isFalse();                                  // relative
        assertThat(allowlist.isAllowed("other", "https://shop.example.com/checkout/")).isFalse();        // other client
        assertThat(allowlist.isAllowed("unknown", "http://localhost:3000/")).isFalse();
    }

    @Test
    void badConfigurationFailsAtStartup() {
        assertThatThrownBy(() -> ReturnUrlAllowlist.parse("shop:not-a-url")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ReturnUrlAllowlist.parse("shop:https://x.example.com/?a=1"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(ReturnUrlAllowlist.parse("").isAllowed("shop", "https://shop.example.com/")).isFalse();
    }
}
