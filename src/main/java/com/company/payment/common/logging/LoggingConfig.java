package com.company.payment.common.logging;

import io.micrometer.tracing.Tracer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the logging pieces. Filter order for /api/** (outermost first):
 *
 *   Boot tracing filter (traceId, spanId)  ->  CorrelationIdFilter  ->  HttpLoggingFilter  ->  ApiKeyFilter  ->  controller
 *
 * HttpLoggingFilter runs before ApiKeyFilter, so rejected calls (401) are logged too.
 */
@Configuration
public class LoggingConfig {

    @Bean
    SensitiveDataMasker sensitiveDataMasker(LoggingProperties properties) {
        return new SensitiveDataMasker(properties);
    }

    @Bean
    RestClientLoggingInterceptor restClientLoggingInterceptor(SensitiveDataMasker masker) {
        return new RestClientLoggingInterceptor(masker);
    }

    @Bean
    FilterRegistrationBean<CorrelationIdFilter> correlationIdFilter(ObjectProvider<Tracer> tracer) {
        FilterRegistrationBean<CorrelationIdFilter> bean = new FilterRegistrationBean<>(new CorrelationIdFilter(tracer));
        bean.addUrlPatterns("/*");
        bean.setOrder(CorrelationIdFilter.ORDER);
        return bean;
    }

    @Bean
    FilterRegistrationBean<HttpLoggingFilter> httpLoggingFilter(SensitiveDataMasker masker) {
        FilterRegistrationBean<HttpLoggingFilter> bean = new FilterRegistrationBean<>(new HttpLoggingFilter(masker));
        bean.addUrlPatterns("/api/*");            // not for actuator, swagger, static files
        bean.setOrder(HttpLoggingFilter.ORDER);
        return bean;
    }
}
