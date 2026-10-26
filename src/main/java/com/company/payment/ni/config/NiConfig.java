package com.company.payment.ni.config;

import com.company.payment.common.logging.RestClientLoggingInterceptor;
import com.company.payment.ni.exception.NiException;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.httpcomponents.hc5.PoolingHttpClientConnectionManagerMetricsBinder;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * The HTTP client used for every N-Genius call:
 *  - connection pool with connect/response timeouts
 *  - stale-connection check + idle eviction (avoids "works after restart, times out after days")
 *  - pool metrics: httpcomponents.httpclient.pool.* (tag httpclient=ni)
 *  - built from Boot's RestClient.Builder: tracing headers (traceparent) are added and every call is
 *    a span / http.client.requests metric
 *  - every request + response is logged by RestClientLoggingInterceptor (masked)
 *  - any 4xx/5xx from N-Genius becomes NiException
 */
@Configuration
public class NiConfig {

    @Bean(destroyMethod = "close")
    CloseableHttpClient niHttpClient(NiProperties properties, ObjectProvider<MeterRegistry> meterRegistry) {
        PoolingHttpClientConnectionManager pool = PoolingHttpClientConnectionManagerBuilder.create()
                .setDefaultConnectionConfig(ConnectionConfig.custom()
                        .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                        .setSocketTimeout(Timeout.of(properties.responseTimeout()))
                        .setTimeToLive(TimeValue.ofMinutes(5))
                        .setValidateAfterInactivity(TimeValue.ofSeconds(10))
                        .build())
                .setMaxConnTotal(properties.maxConnections())
                .setMaxConnPerRoute(properties.maxConnections())
                .build();

        meterRegistry.ifAvailable(registry ->
                new PoolingHttpClientConnectionManagerMetricsBinder(pool, "ni").bindTo(registry));

        return HttpClients.custom()
                .setConnectionManager(pool)
                .evictIdleConnections(TimeValue.ofSeconds(30))
                .evictExpiredConnections()
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectionRequestTimeout(Timeout.ofSeconds(5))
                        .setResponseTimeout(Timeout.of(properties.responseTimeout()))
                        .build())
                .build();
    }

    @Bean
    RestClient niRestClient(RestClient.Builder builder, CloseableHttpClient niHttpClient, NiProperties properties,
                            RestClientLoggingInterceptor loggingInterceptor) {
        return builder.clone()
                .baseUrl(properties.baseUrl())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(niHttpClient))
                .requestInterceptor(loggingInterceptor)
                .defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
                    throw NiException.from(request, response);
                })
                .build();
    }
}
