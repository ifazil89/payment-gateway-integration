package com.company.payment.ni.client;

import com.company.payment.ni.config.NiProperties;
import com.company.payment.ni.dto.NiAccessTokenResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Map;

/**
 * N-Genius access token (not to be confused with card tokens).
 *
 *   POST /identity/auth/access-token
 *   Authorization: Basic {service account API key}
 *   Content-Type:  application/vnd.ni-identity.v1+json
 *
 * Valid 5 minutes. Cached and refreshed 30 seconds early; one thread refreshes at a time.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class NiAccessTokenProvider {

    private static final MediaType IDENTITY_V1 = MediaType.parseMediaType("application/vnd.ni-identity.v1+json");
    private static final long DEFAULT_EXPIRES_IN_SECONDS = 300;

    private final RestClient niRestClient;
    private final NiProperties properties;

    private String accessToken;
    private Instant refreshAt = Instant.EPOCH;

    public synchronized String getAccessToken() {
        if (accessToken == null || Instant.now().isAfter(refreshAt)) {
            RestClient.RequestBodySpec request = niRestClient.post()
                    .uri("/identity/auth/access-token")
                    .header(HttpHeaders.AUTHORIZATION, "Basic " + properties.apiKey())
                    .contentType(IDENTITY_V1)
                    .accept(IDENTITY_V1);
            if (StringUtils.hasText(properties.realmName())) {
                request = request.body(Map.of("realmName", properties.realmName()));
            }
            NiAccessTokenResponse response = request.retrieve().body(NiAccessTokenResponse.class);
            if (response == null || !StringUtils.hasText(response.accessToken())) {
                throw new IllegalStateException("N-Genius returned no access token");
            }
            long expiresIn = response.expiresIn() != null ? response.expiresIn() : DEFAULT_EXPIRES_IN_SECONDS;
            accessToken = response.accessToken();
            refreshAt = Instant.now().plusSeconds(expiresIn - 30);
            log.debug("New N-Genius access token, valid {} s", expiresIn);
        }
        return accessToken;
    }

    /** After a 401: forget the token, the next call fetches a new one. */
    public synchronized void invalidate() {
        accessToken = null;
    }
}
