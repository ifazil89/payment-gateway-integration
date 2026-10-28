package com.company.payment.common.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Sends every log line to Grafana over OTLP (Loki 3, Alloy, an OpenTelemetry collector or Grafana Cloud),
 * next to the console and the rolling file. Only where an endpoint is configured: the sandbox has one,
 * production has none (no Grafana there yet).
 *
 * Boot builds the OTLP log exporter from management.opentelemetry.logging.export.otlp.*; this adds the Logback
 * appender that feeds it. Boot sets up Logback in code (no logback-spring.xml), so the appender is attached to
 * the root logger here. Lines are already masked where they are written (SensitiveDataMasker), as on the console.
 * Each record carries the MDC (traceId, correlationId, clientId, paymentId) and the fluent key-values
 * (payment.*, ni.*, http.*) as attributes.
 */
@Configuration
@ConditionalOnProperty("management.opentelemetry.logging.export.otlp.endpoint")
@ConditionalOnBooleanProperty(name = "management.logging.export.otlp.enabled", matchIfMissing = true)
@Slf4j
class GrafanaLogExportConfig {

    static final String APPENDER_NAME = "GRAFANA_OTLP";

    @Bean
    InitializingBean grafanaLogAppender(OpenTelemetry openTelemetry) {
        return () -> {
            LoggerContext context = (LoggerContext) LoggerFactory.getILoggerFactory();
            Logger root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            if (root.getAppender(APPENDER_NAME) == null) {
                OpenTelemetryAppender appender = new OpenTelemetryAppender();
                appender.setName(APPENDER_NAME);
                appender.setContext(context);
                appender.setCaptureMdcAttributes("*");
                appender.setCaptureKeyValuePairAttributes(true);
                appender.setCaptureExperimentalAttributes(true);    // thread name
                appender.start();
                root.addAppender(appender);
            }
            OpenTelemetryAppender.install(openTelemetry);
            log.info("Log export to Grafana (OTLP) is on");
        };
    }
}
