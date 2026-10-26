package com.company.payment.common.logging;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * HTTP payload logging ("app.logging.http.*").
 *
 * @param logBodies     log request/response bodies (always masked). false = only method, url, status, time
 * @param maxBodyLength longer bodies are cut after masking
 */
@ConfigurationProperties("app.logging.http")
public record LoggingProperties(
        @DefaultValue("true") boolean logBodies,
        @DefaultValue("10000") int maxBodyLength) {
}
