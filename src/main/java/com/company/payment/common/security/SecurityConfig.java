package com.company.payment.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Registers ApiKeyFilter for /api/** (after CorrelationIdFilter and HttpLoggingFilter). */
@Configuration
public class SecurityConfig {

    @Bean
    FilterRegistrationBean<ApiKeyFilter> apiKeyFilter(@Value("${app.security.api-keys}") String apiKeys) {
        FilterRegistrationBean<ApiKeyFilter> bean = new FilterRegistrationBean<>(new ApiKeyFilter(parse(apiKeys)));
        bean.addUrlPatterns("/api/*");
        bean.setOrder(ApiKeyFilter.ORDER);
        return bean;
    }

    /** "client1:key1,client2:key2" -> {client1=key1, client2=key2}. Fails at startup on a bad value. */
    static Map<String, byte[]> parse(String apiKeys) {
        Map<String, byte[]> result = new LinkedHashMap<>();
        for (String entry : apiKeys.split(",")) {
            String[] parts = entry.trim().split(":", 2);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].trim().length() < 8) {
                throw new IllegalStateException(
                        "app.security.api-keys must be 'client:key' pairs (key >= 8 chars), separated by commas");
            }
            result.put(parts[0].trim(), parts[1].trim().getBytes(StandardCharsets.UTF_8));
        }
        return result;
    }
}
