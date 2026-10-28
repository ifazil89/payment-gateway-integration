package com.company.payment.payment;

import com.company.payment.ni.client.NiClient;
import com.company.payment.ni.exception.NiException;
import com.company.payment.ni.dto.NiCardRequest;
import com.company.payment.ni.dto.NiChallengeResponseRequest;
import com.company.payment.ni.dto.NiOrderRequest;
import com.company.payment.ni.dto.NiOrderResponse;
import com.company.payment.ni.dto.NiPaymentResponse;
import com.company.payment.ni.dto.NiThreeDs;
import com.company.payment.ni.dto.NiThreeDsRequest;
import com.company.payment.ni.dto.NiTokenCardRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * POST /api/v1/payments, GET /api/v1/payments/{id} and the 3DS challenge notification end to end
 * (controllers, validation, services, MapStruct, H2, Flyway) with only NiClient mocked. Runs without N-Genius credentials.
 * Also checks the logging promises: correlation id round-trip, and no card number / CVV in any log line.
 */
@SpringBootTest(properties = {"ni.api-key=test", "ni.outlet-id=outlet-1",
        "app.security.api-keys=local-client:sandbox-key,other-client:other-client-key",
        "management.logging.export.otlp.enabled=false", "management.tracing.export.otlp.enabled=false",
        "logging.file.name=target/logs/test.log"})
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class PaymentApiTest {

    private static final String API_KEY = "sandbox-key";     // application-sandbox.yml: local-client:sandbox-key
    private static final String RETURN_URL = "http://localhost:3000/checkout/done?order=SO-1001";   // sandbox return-urls
    private static final String NI = "https://api-gateway.sandbox.ngenius-payments.com/transactions/outlets/outlet-1/orders/";
    private static final String METHOD_URL = "https://paypage.sandbox.ngenius-payments.com/3ds2/method";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    NiClient niClient;

    // ------------------------------------------------------------------ happy paths

    @Test
    void cardIsAuthorised() throws Exception {
        givenOrder("order-1");
        when(niClient.payWithCard(eq(NI + "order-1/payments/pay-1/card"), any()))
                .thenReturn(niPayment("AUTHORISED", Map.of(), null));

        mvc.perform(payment("key-1", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORISED"))
                .andExpect(jsonPath("$.orderReference").value("order-1"))
                .andExpect(jsonPath("$.amount.value").value(250.00))
                .andExpect(jsonPath("$.paymentId").isNotEmpty());

        // two-stage AUTH order, amount in minor units
        ArgumentCaptor<NiOrderRequest> order = ArgumentCaptor.forClass(NiOrderRequest.class);
        verify(niClient).createOrder(order.capture());
        assertThat(order.getValue().action()).isEqualTo("AUTH");
        assertThat(order.getValue().amount().value()).isEqualTo(25000L);
        assertThat(order.getValue().merchantOrderReference()).isEqualTo("SO-1001");
        assertThat(order.getValue().emailAddress()).isEqualTo("jane@example.com");

        ArgumentCaptor<NiCardRequest> card = ArgumentCaptor.forClass(NiCardRequest.class);
        verify(niClient).payWithCard(any(), card.capture());
        assertThat(card.getValue().pan()).isEqualTo("4111111111111111");
    }

    @Test
    void cardOnFileUsesTheSavedToken() throws Exception {
        givenOrder("order-2");
        when(niClient.payWithToken(eq(NI + "order-2/payments/pay-1/saved-card"), any()))
                .thenReturn(niPayment("AUTHORISED", Map.of(), null));

        mvc.perform(payment("key-2", cardOnFileRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORISED"));

        ArgumentCaptor<NiTokenCardRequest> token = ArgumentCaptor.forClass(NiTokenCardRequest.class);
        verify(niClient).payWithToken(any(), token.capture());
        assertThat(token.getValue().cardToken()).isEqualTo("saved-ni-token");
        assertThat(token.getValue().cvv()).isEqualTo("123");
        verify(niClient, never()).payWithCard(any(), any());
    }

    @Test
    void threeDsChallengeRequired() throws Exception {
        givenOrder("order-3");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-authentication", Map.of("href", NI + "order-3/payments/pay-1/card/3ds2/authentications")),
                null));
        when(niClient.authenticateThreeDs(any(), any())).thenReturn(challenge("order-3"));

        String body = mvc.perform(payment("key-3", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHALLENGE_REQUIRED"))
                .andExpect(jsonPath("$.threeDs.acsUrl").value("https://acs.bank.example/challenge"))
                .andExpect(jsonPath("$.threeDs.creq").value("Y3JlcQ=="))
                .andReturn().getResponse().getContentAsString();

        // 3DS was started with the device info and our notification URL
        ArgumentCaptor<NiThreeDsRequest> threeDs = ArgumentCaptor.forClass(NiThreeDsRequest.class);
        verify(niClient).authenticateThreeDs(eq(NI + "order-3/payments/pay-1/card/3ds2/authentications"),
                threeDs.capture());
        assertThat(threeDs.getValue().deviceChannel()).isEqualTo("BRW");
        assertThat(threeDs.getValue().browserInfo().browserIp()).isEqualTo("203.0.113.10");
        assertThat(threeDs.getValue().notificationUrl())
                .contains(com.jayway.jsonpath.JsonPath.<String>read(body, "$.paymentId"));
    }

    @Test
    void threeDsFrictionlessIsAuthorised() throws Exception {
        givenOrder("order-4");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-authentication", Map.of("href", NI + "order-4/payments/pay-1/card/3ds2/authentications")),
                null));
        when(niClient.authenticateThreeDs(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(),
                new NiThreeDs("Y", null, null, "05", "2.2.0", null)));

        mvc.perform(payment("key-4", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORISED"))
                .andExpect(jsonPath("$.threeDs").doesNotExist());
    }

    @Test
    void threeDsFrictionlessWithUsSpellingIsAuthorised() throws Exception {
        givenOrder("order-40");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-authentication", Map.of("href", NI + "order-40/payments/pay-1/card/3ds2/authentications")),
                null));
        when(niClient.authenticateThreeDs(any(), any())).thenReturn(niPayment("AUTHORIZED", Map.of(),
                new NiThreeDs("Y", null, null, "05", "2.2.0", null)));

        mvc.perform(payment("key-40", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORISED"));
    }

    @Test
    void stillAwaiting3dsWithChallengeDataNeedsTheChallenge() throws Exception {
        // N-Genius guide: transStatus other than C, but the payment is still AWAIT_3DS -> challenge step required
        givenOrder("order-41");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-authentication", Map.of("href", NI + "order-41/payments/pay-1/card/3ds2/authentications")),
                null));
        when(niClient.authenticateThreeDs(any(), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-challenge-response",
                        Map.of("href", NI + "order-41/payments/pay-1/card/3ds2/challenge-response")),
                new NiThreeDs("U", "https://acs.bank.example/challenge", "Y3JlcQ==", null, "2.2.0", null)));

        mvc.perform(payment("key-41", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CHALLENGE_REQUIRED"))
                .andExpect(jsonPath("$.threeDs.acsUrl").value("https://acs.bank.example/challenge"))
                .andExpect(jsonPath("$.threeDs.creq").value("Y3JlcQ=="));
    }

    // ------------------------------------------------------------------ 3DS challenge page + CRes from the client

    @Test
    void returnUrlIsTheNotificationUrl() throws Exception {
        challengedPayment("key-50", "order-50", RETURN_URL);

        // the ACS posts the CRes to the client, not to us
        ArgumentCaptor<NiThreeDsRequest> threeDs = ArgumentCaptor.forClass(NiThreeDsRequest.class);
        verify(niClient).authenticateThreeDs(eq(NI + "order-50/payments/pay-1/card/3ds2/authentications"),
                threeDs.capture());
        assertThat(threeDs.getValue().notificationUrl()).isEqualTo(RETURN_URL);
    }

    @Test
    void challengePageIsLoadedFromThreeDsMethodUrl() throws Exception {
        String paymentId = challengedPayment("key-51", "order-51", RETURN_URL);
        when(niClient.challengePage(any(), any()))
                .thenReturn("<html><head><title>OTP</title></head><body><form action=\"submit\"></form></body></html>");

        mvc.perform(get("/api/v1/threeds/{id}/challenge", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/html")))
                // relative links of the page still point to the 3DS server when shown from our response
                .andExpect(content().string(org.hamcrest.Matchers.containsString(
                        "<head><base href=\"" + METHOD_URL + "\"><title>OTP</title>")));

        verify(niClient).challengePage(METHOD_URL, "Y3JlcQ==");
    }

    @Test
    void challengePageOnlyWhileChallengeRequired() throws Exception {
        String paymentId = challengedPayment("key-52", "order-52", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(), null));
        mvc.perform(challengeResponse(paymentId)).andExpect(status().isOk());

        mvc.perform(get("/api/v1/threeds/{id}/challenge", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CHALLENGE_NOT_REQUIRED"));
        verify(niClient, never()).challengePage(any(), any());
    }

    @Test
    void challengePageTimeoutIs504() throws Exception {
        String paymentId = challengedPayment("key-53", "order-53", RETURN_URL);
        when(niClient.challengePage(any(), any())).thenThrow(new ResourceAccessException("Read timed out"));

        mvc.perform(get("/api/v1/threeds/{id}/challenge", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("GATEWAY_TIMEOUT"));
    }

    @Test
    void clientSendsTheCresAndGetsTheOutcome() throws Exception {
        String paymentId = challengedPayment("key-54", "order-54", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(),
                new NiThreeDs("Y", null, null, "05", "2.2.0", null)));

        mvc.perform(challengeResponse(paymentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentId))
                .andExpect(jsonPath("$.status").value("AUTHORISED"))
                .andExpect(jsonPath("$.threeDs").doesNotExist());

        ArgumentCaptor<NiChallengeResponseRequest> cres = ArgumentCaptor.forClass(NiChallengeResponseRequest.class);
        verify(niClient).completeChallenge(eq(NI + "order-54/payments/pay-1/card/3ds2/challenge-response"), cres.capture());
        assertThat(cres.getValue().base64EncodedCRes()).isEqualTo("Y3Jlcw==");

        // a repeated call does not send the CRes again
        mvc.perform(challengeResponse(paymentId)).andExpect(jsonPath("$.status").value("AUTHORISED"));
        verify(niClient, times(1)).completeChallenge(any(), any());
    }

    @Test
    void challengeEndpointsNeedTheApiKeyAndTheOwningClient() throws Exception {
        String paymentId = challengedPayment("key-55", "order-55", RETURN_URL);

        mvc.perform(get("/api/v1/threeds/{id}/challenge", paymentId))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/v1/threeds/{id}/challenge-response", paymentId)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cres\": \"Y3Jlcw==\"}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/threeds/{id}/challenge", paymentId).header("X-Api-Key", "other-client-key"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/v1/threeds/{id}/challenge-response", paymentId).header("X-Api-Key", "other-client-key")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cres\": \"Y3Jlcw==\"}"))
                .andExpect(status().isNotFound());
        verify(niClient, never()).challengePage(any(), any());
        verify(niClient, never()).completeChallenge(any(), any());
    }

    @Test
    void challengeResponseWithoutCresIsRejected() throws Exception {
        String paymentId = challengedPayment("key-56", "order-56", RETURN_URL);

        mvc.perform(post("/api/v1/threeds/{id}/challenge-response", paymentId).header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"cres\": \"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    // ------------------------------------------------------------------ 3DS challenge result and status

    @Test
    void challengeResultAuthorisesAndSendsTheShopperBack() throws Exception {
        String paymentId = challengedPayment("key-20", "order-20", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(),
                new NiThreeDs("Y", null, null, "05", "2.2.0", null)));

        // posted by the shopper's browser: no API key
        mvc.perform(notification(paymentId))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", RETURN_URL + "&paymentId=" + paymentId + "&status=AUTHORISED"));

        ArgumentCaptor<NiChallengeResponseRequest> cres = ArgumentCaptor.forClass(NiChallengeResponseRequest.class);
        verify(niClient).completeChallenge(eq(NI + "order-20/payments/pay-1/card/3ds2/challenge-response"), cres.capture());
        assertThat(cres.getValue().base64EncodedCRes()).isEqualTo("Y3Jlcw==");

        // the client reads the trusted final status
        mvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("AUTHORISED"))
                .andExpect(jsonPath("$.threeDs").doesNotExist());
    }

    @Test
    void repeatedNotificationDoesNotCallNgeniusAgain() throws Exception {
        String paymentId = challengedPayment("key-21", "order-21", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(), null));

        mvc.perform(notification(paymentId)).andExpect(status().isSeeOther());
        mvc.perform(notification(paymentId))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("&status=AUTHORISED")));
        verify(niClient, times(1)).completeChallenge(any(), any());
    }

    @Test
    void withoutReturnUrlTheShopperSeesAResultPage() throws Exception {
        String paymentId = challengedPayment("key-22", "order-22", null);
        when(niClient.completeChallenge(any(), any())).thenReturn(niPayment("DECLINED", Map.of(), null));

        mvc.perform(notification(paymentId))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/html")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Payment declined.")));
    }

    @Test
    void ngeniusTimeoutOnChallengeResultIsPending() throws Exception {
        String paymentId = challengedPayment("key-23", "order-23", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenThrow(new ResourceAccessException("Read timed out"));

        mvc.perform(notification(paymentId))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("&status=PENDING")));
        mvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(jsonPath("$.status").value("PENDING"));
        // outcome unknown: a repeated notification must not send the CRes again
        mvc.perform(notification(paymentId)).andExpect(status().isSeeOther());
        verify(niClient, times(1)).completeChallenge(any(), any());
    }

    @Test
    void challengeResultRefusedByNgeniusIsFailed() throws Exception {
        String paymentId = challengedPayment("key-24", "order-24", RETURN_URL);
        when(niClient.completeChallenge(any(), any())).thenThrow(new NiException(400, "/challenge-response", "{}"));

        mvc.perform(notification(paymentId))
                .andExpect(header().string("Location", org.hamcrest.Matchers.endsWith("&status=FAILED")));
        mvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-Api-Key", API_KEY))
                .andExpect(jsonPath("$.failureReason").value("3DS challenge rejected by N-Genius (HTTP 400)"));
    }

    @Test
    void notificationForUnknownPaymentIsNotFound() throws Exception {
        mvc.perform(notification("no-such-payment"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
    }

    @Test
    void returnUrlMustBeAllowedForTheClient() throws Exception {
        mvc.perform(payment("key-25", withReturnUrl(cardRequest("250.00"), "https://evil.example.com/")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("returnUrl"));
        verify(niClient, never()).createOrder(any());
    }

    @Test
    void anotherClientsPaymentIsNotFound() throws Exception {
        givenOrder("order-26");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(), null));
        String body = mvc.perform(payment("key-26", cardRequest("250.00")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String paymentId = com.jayway.jsonpath.JsonPath.read(body, "$.paymentId");

        mvc.perform(get("/api/v1/payments/{id}", paymentId).header("X-Api-Key", "other-client-key"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("PAYMENT_NOT_FOUND"));
        mvc.perform(get("/api/v1/payments/{id}", paymentId))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ N-Genius problems

    @Test
    void refusedByNgeniusIsFailed() throws Exception {
        givenOrder("order-5");
        when(niClient.payWithCard(any(), any())).thenThrow(new NiException(400, "/card", "{\"message\":\"declined\"}"));

        mvc.perform(payment("key-5", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("FAILED"))
                .andExpect(jsonPath("$.failureReason").value("Rejected by N-Genius (HTTP 400)"));
    }

    @Test
    void timeoutIsUnknownAndRetryWithSameKeyIsSafe() throws Exception {
        givenOrder("order-6");
        when(niClient.payWithCard(any(), any())).thenThrow(new ResourceAccessException("Read timed out"));

        mvc.perform(payment("key-6", cardRequest("250.00")))
                .andExpect(status().isGatewayTimeout())
                .andExpect(jsonPath("$.code").value("GATEWAY_TIMEOUT"));

        // retry with the SAME key: no second order, the payment is reported as PENDING
        mvc.perform(payment("key-6", cardRequest("250.00")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"));
        verify(niClient, times(1)).createOrder(any());
    }

    // ------------------------------------------------------------------ idempotency and validation

    @Test
    void sameKeyDifferentRequestIsRejected() throws Exception {
        givenOrder("order-7");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(), null));
        mvc.perform(payment("key-7", cardRequest("250.00"))).andExpect(status().isOk());

        mvc.perform(payment("key-7", cardRequest("99.00")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("IDEMPOTENCY_KEY_REUSED"));
    }

    @Test
    void cardFieldsMustMatchCardType() throws Exception {
        String body = cardOnFileRequest().replace("\"token\": \"saved-ni-token\"", "\"pan\": \"4111111111111111\"");

        mvc.perform(payment("key-8", body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("card.token", "card.pan")));
        verify(niClient, never()).createOrder(any());
    }

    @Test
    void tooManyDecimalsIsRejected() throws Exception {
        mvc.perform(payment("key-9", cardRequest("10.001")))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("INVALID_AMOUNT"));
        verify(niClient, never()).createOrder(any());
    }

    @Test
    void apiKeyIsRequired() throws Exception {
        mvc.perform(post("/api/v1/payments").contentType(MediaType.APPLICATION_JSON).content(cardRequest("1.00")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void idempotencyKeyIsRequired() throws Exception {
        mvc.perform(post("/api/v1/payments").header("X-Api-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON).content(cardRequest("1.00")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MISSING_HEADER"));
    }

    @Test
    void expiredCardIsRejected() throws Exception {
        mvc.perform(payment("key-10", cardRequest("1.00").replace("2030-12", "2020-01")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("card.expiry"));
    }

    // ------------------------------------------------------------------ logging and tracing

    @Test
    void correlationIdIsReturnedAndInErrors() throws Exception {
        mvc.perform(payment("key-11", cardRequest("10.001")).header("X-Correlation-Id", "my-corr-123"))
                .andExpect(status().is(422))
                .andExpect(header().string("X-Correlation-Id", "my-corr-123"))
                .andExpect(jsonPath("$.correlationId").value("my-corr-123"));
    }

    @Test
    void unsafeCorrelationIdIsReplaced() throws Exception {
        mvc.perform(payment("key-12", cardRequest("10.001")).header("X-Correlation-Id", "bad\nvalue"))
                .andExpect(header().string("X-Correlation-Id", org.hamcrest.Matchers.not("bad\nvalue")));
    }

    @Test
    void requestAndResponseAreLoggedWithoutCardData(CapturedOutput output) throws Exception {
        givenOrder("order-13");
        when(niClient.payWithCard(any(), any())).thenReturn(niPayment("AUTHORISED", Map.of(), null));

        mvc.perform(payment("key-13", cardRequest("250.00")).header("X-Correlation-Id", "log-test-13"))
                .andExpect(status().isOk());

        assertThat(output.getOut())
                .contains("HTTP IN  --> POST /api/v1/payments")
                .contains("HTTP IN  <-- 200 POST /api/v1/payments")
                .contains("log-test-13")                       // correlationId on the lines
                .contains("411111******1111")                  // masked PAN in the logged body
                .doesNotContain("4111111111111111")            // never the full card number
                .doesNotContainPattern("cvv\\W{1,8}123")       // CVV masked (JSON in JSON is escaped)
                .doesNotContain(API_KEY);                      // X-Api-Key header masked
    }

    // ------------------------------------------------------------------ helpers

    private void givenOrder(String orderRef) {
        NiPaymentResponse started = new NiPaymentResponse("pay-1", orderRef, "STARTED", null, null, Map.of(
                "payment:card", Map.of("href", NI + orderRef + "/payments/pay-1/card"),
                "payment:saved-card", Map.of("href", NI + orderRef + "/payments/pay-1/saved-card")));
        when(niClient.createOrder(any()))
                .thenReturn(new NiOrderResponse(orderRef, null, new NiOrderResponse.Embedded(List.of(started))));
    }

    /** A payment that needed a 3DS challenge; returns its paymentId. */
    private String challengedPayment(String idempotencyKey, String orderRef, String returnUrl) throws Exception {
        givenOrder(orderRef);
        when(niClient.payWithCard(eq(NI + orderRef + "/payments/pay-1/card"), any())).thenReturn(niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-authentication", Map.of("href", NI + orderRef + "/payments/pay-1/card/3ds2/authentications")),
                null));
        when(niClient.authenticateThreeDs(eq(NI + orderRef + "/payments/pay-1/card/3ds2/authentications"), any()))
                .thenReturn(challenge(orderRef));
        String request = returnUrl == null ? cardRequest("250.00") : withReturnUrl(cardRequest("250.00"), returnUrl);
        String body = mvc.perform(payment(idempotencyKey, request))
                .andExpect(jsonPath("$.status").value("CHALLENGE_REQUIRED"))
                .andReturn().getResponse().getContentAsString();
        return com.jayway.jsonpath.JsonPath.read(body, "$.paymentId");
    }

    private static NiPaymentResponse challenge(String orderRef) {
        return niPayment("AWAIT_3DS",
                Map.of("cnp:3ds2-challenge-response",
                        Map.of("href", NI + orderRef + "/payments/pay-1/card/3ds2/challenge-response")),
                new NiThreeDs("C", "https://acs.bank.example/challenge", "Y3JlcQ==", null, "2.2.0", METHOD_URL));
    }

    /** What the card issuer's ACS makes the shopper's browser post (form, no API key). */
    private static MockHttpServletRequestBuilder notification(String paymentId) {
        return post("/api/v1/threeds/{paymentId}/notification", paymentId)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("cres", "Y3Jlcw==");
    }

    /** The client passes on the CRes the ACS posted to its returnUrl. */
    private static MockHttpServletRequestBuilder challengeResponse(String paymentId) {
        return post("/api/v1/threeds/{paymentId}/challenge-response", paymentId)
                .header("X-Api-Key", API_KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"cres\": \"Y3Jlcw==\"}");
    }

    private static String withReturnUrl(String request, String returnUrl) {
        return request.replaceFirst("\"merchantOrderId\"", "\"returnUrl\": \"" + returnUrl + "\", \"merchantOrderId\"");
    }

    private static NiPaymentResponse niPayment(String state, Map<String, Object> links, NiThreeDs threeDs) {
        return new NiPaymentResponse("pay-1", null, state, null, threeDs, links);
    }

    private static MockHttpServletRequestBuilder payment(String idempotencyKey, String body) {
        return post("/api/v1/payments")
                .header("X-Api-Key", API_KEY)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private static String cardRequest(String amount) {
        return request("CARD", "\"pan\": \"4111111111111111\"", amount);
    }

    private static String cardOnFileRequest() {
        return request("CARD_ON_FILE", "\"token\": \"saved-ni-token\"", "250.00");
    }

    private static String request(String cardType, String panOrToken, String amount) {
        return """
                {
                  "merchantOrderId": "SO-1001",
                  "cardType": "%s",
                  "card": { %s, "expiry": "2030-12", "cvv": "123", "cardholderName": "Jane Doe" },
                  "amount": { "value": %s, "currency": "AED" },
                  "billingInfo": { "firstName": "Jane", "lastName": "Doe", "email": "jane@example.com",
                                   "address": "Sheikh Zayed Road 1", "city": "Dubai", "countryCode": "AE" },
                  "deviceInfo": { "ip": "203.0.113.10", "userAgent": "Mozilla/5.0", "acceptHeader": "text/html",
                                  "language": "en", "timeZoneOffset": "-240", "screenWidth": "1440",
                                  "screenHeight": "900", "colorDepth": "24", "javascriptEnabled": true,
                                  "javaEnabled": false }
                }
                """.formatted(cardType, panOrToken, amount);
    }
}
