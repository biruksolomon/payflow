package com.payflow.backend.service;

import com.payflow.backend.config.StripeConfig;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.dto.request.CreateCheckoutSessionRequest;
import com.payflow.backend.dto.response.CreateCheckoutSessionResponse;
import com.payflow.backend.exception.AuthException;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StripeService.
 *
 * The Stripe SDK makes real HTTP calls via static factory methods (Session.create,
 * Webhook.constructEvent, Refund.create).  We use Mockito's MockedStatic to intercept
 * those calls so no network traffic ever leaves the test JVM.
 *
 * PaymentService is mocked; its behaviour is validated in PaymentServiceTest.
 */
@ExtendWith(MockitoExtension.class)
class StripeServiceTest {

    @Mock
    private StripeConfig stripeConfig;

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private StripeService stripeService;

    // ── shared fixtures ───────────────────────────────────────────

    private Payment pendingPayment;
    private CreateCheckoutSessionRequest createRequest;

    @BeforeEach
    void setUp() {
        pendingPayment = Payment.builder()
                .id(10L)
                .amount(new BigDecimal("150.00"))
                .currency(Currency.USD)
                .paymentStatus(PaymentStatus.PENDING)
                .transactionId("TXN-TEST")
                .retryCount(0)
                .build();

        createRequest = CreateCheckoutSessionRequest.builder()
                .orderId(5L)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE CHECKOUT SESSION
    // ─────────────────────────────────────────────────────────────

    @Test
    void createCheckoutSession_ShouldReturnResponseWithCheckoutUrl() throws Exception {
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(paymentService.updateStripeIntentId(10L, "cs_test_session123"))
                .thenReturn(pendingPayment);
        when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/payment/success");
        when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment/cancel");

        Session fakeSession = new Session();
        fakeSession.setId("cs_test_session123");
        fakeSession.setUrl("https://checkout.stripe.com/pay/cs_test_session123");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenReturn(fakeSession);

            CreateCheckoutSessionResponse response = stripeService.createCheckoutSession(createRequest, 1L);

            assertThat(response).isNotNull();
            assertThat(response.getCheckoutUrl()).isEqualTo("https://checkout.stripe.com/pay/cs_test_session123");
            assertThat(response.getSessionId()).isEqualTo("cs_test_session123");
            assertThat(response.getPaymentId()).isEqualTo(10L);
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getCurrency()).isEqualTo("USD");
        }

        verify(paymentService).initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null);
        verify(paymentService).updateStripeIntentId(10L, "cs_test_session123");
    }

    @Test
    void createCheckoutSession_ShouldMarkPaymentFailed_WhenStripeThrows() throws Exception {
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/payment/success");
        when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment/cancel");

        ApiException stripeEx = new ApiException("session_creation_failed", null, "session_creation_failed", 402, null);

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.create(any(com.stripe.param.checkout.SessionCreateParams.class)))
                    .thenThrow(stripeEx);

            assertThatThrownBy(() -> stripeService.createCheckoutSession(createRequest, 1L))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Stripe checkout session creation failed");
        }

        // The pending payment must be marked FAILED so the order is not left in limbo
        verify(paymentService).recordFailedPayment(eq(10L), any(), any());
    }

    @Test
    void createCheckoutSession_AmountConvertedToCents() throws Exception {
        // Verify 150.00 USD → 15000 cents is passed to Stripe
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(paymentService.updateStripeIntentId(anyLong(), anyString()))
                .thenReturn(pendingPayment);
        when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/payment/success");
        when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment/cancel");

        Session fakeSession = new Session();
        fakeSession.setId("cs_cents");
        fakeSession.setUrl("https://checkout.stripe.com/pay/cs_cents");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            // Verify the line item unit amount is 15000 cents
            sessionStatic.when(() -> Session.create(
                            argThat((com.stripe.param.checkout.SessionCreateParams p) -> {
                                long unitAmount = p.getLineItems().get(0).getPriceData().getUnitAmount();
                                return unitAmount == 15000L; // 150.00 * 100
                            })))
                    .thenReturn(fakeSession);

            CreateCheckoutSessionResponse response = stripeService.createCheckoutSession(createRequest, 1L);
            assertThat(response).isNotNull();
        }
    }

    @Test
    void createCheckoutSession_SuccessUrlContainsPaymentId() throws Exception {
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(paymentService.updateStripeIntentId(anyLong(), anyString()))
                .thenReturn(pendingPayment);
        when(stripeConfig.getSuccessUrl()).thenReturn("http://localhost:3000/payment/success");
        when(stripeConfig.getCancelUrl()).thenReturn("http://localhost:3000/payment/cancel");

        Session fakeSession = new Session();
        fakeSession.setId("cs_url_test");
        fakeSession.setUrl("https://checkout.stripe.com/pay/cs_url_test");

        try (MockedStatic<Session> sessionStatic = mockStatic(Session.class)) {
            sessionStatic.when(() -> Session.create(
                            argThat((com.stripe.param.checkout.SessionCreateParams p) ->
                                    // success_url must include payment_id=10 for correlation
                                    p.getSuccessUrl().contains("payment_id=10"))))
                    .thenReturn(fakeSession);

            CreateCheckoutSessionResponse response = stripeService.createCheckoutSession(createRequest, 1L);
            assertThat(response).isNotNull();
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HANDLE WEBHOOK — SIGNATURE FAILURE
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldThrow_WhenSignatureInvalid() {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenThrow(new SignatureVerificationException("bad sig", "sig_header"));

            assertThatThrownBy(() ->
                    stripeService.handleWebhookEvent("{}", "bad-signature"))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Invalid Stripe webhook signature");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // HANDLE WEBHOOK — checkout.session.completed
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldCallRecordSuccess_OnCheckoutSessionCompleted() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Session session = new Session();
        session.setId("cs_completed");
        session.setMetadata(Map.of("paymentId", "42"));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getId()).thenReturn("evt_001");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent("payload", "sig", "whsec_test"))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");
        }

        verify(paymentService).recordSuccessfulPayment(42L);
    }

    @Test
    void handleWebhookEvent_ShouldSkip_WhenCompletedEventMissingPaymentIdMeta() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Session session = new Session();
        session.setId("cs_no_meta");
        session.setMetadata(Map.of()); // no paymentId

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.completed");
        when(event.getId()).thenReturn("evt_002");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            assertThatNoException().isThrownBy(
                    () -> stripeService.handleWebhookEvent("payload", "sig"));
        }

        verify(paymentService, never()).recordSuccessfulPayment(any());
    }

    // ─────────────────────────────────────────────────────────────
    // HANDLE WEBHOOK — checkout.session.expired
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldCallRecordFailed_OnCheckoutSessionExpired() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Session session = new Session();
        session.setId("cs_expired");
        session.setMetadata(Map.of("paymentId", "77"));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.expired");
        when(event.getId()).thenReturn("evt_003");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");
        }

        verify(paymentService).recordFailedPayment(77L, "SESSION_EXPIRED",
                "Stripe Checkout Session expired before payment was completed");
    }

    @Test
    void handleWebhookEvent_ShouldSkip_WhenExpiredEventMissingPaymentIdMeta() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Session session = new Session();
        session.setId("cs_expired_no_meta");
        session.setMetadata(Map.of());

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(session));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("checkout.session.expired");
        when(event.getId()).thenReturn("evt_004");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            assertThatNoException().isThrownBy(
                    () -> stripeService.handleWebhookEvent("payload", "sig"));
        }

        verify(paymentService, never()).recordFailedPayment(any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // HANDLE WEBHOOK — charge.refunded
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldCallCompleteRefundByIntentId_OnChargeRefunded() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        com.stripe.model.Charge charge = new com.stripe.model.Charge();
        charge.setId("ch_refunded");
        charge.setPaymentIntent("pi_original");

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(charge));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("charge.refunded");
        when(event.getId()).thenReturn("evt_005");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");
        }

        verify(paymentService).completeRefundByIntentId("pi_original");
    }

    // ─────────────────────────────────────────────────────────────
    // HANDLE WEBHOOK — unhandled event type
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldIgnoreUnknownEventTypes() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("customer.created");
        when(event.getId()).thenReturn("evt_unhandled");

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            assertThatNoException().isThrownBy(
                    () -> stripeService.handleWebhookEvent("payload", "sig"));
        }

        verifyNoInteractions(paymentService);
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE REFUND
    // ─────────────────────────────────────────────────────────────

    @Test
    void createRefund_ShouldReturnRefundObject_OnSuccess() throws Exception {
        com.stripe.model.Refund fakeRefund = new com.stripe.model.Refund();
        fakeRefund.setId("re_test123");

        try (MockedStatic<com.stripe.model.Refund> refundStatic =
                     mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                            any(com.stripe.param.RefundCreateParams.class)))
                    .thenReturn(fakeRefund);

            com.stripe.model.Refund result =
                    stripeService.createRefund("pi_intent", new BigDecimal("50.00"));

            assertThat(result.getId()).isEqualTo("re_test123");
        }
    }

    @Test
    void createRefund_ShouldConvertAmountToCents() throws Exception {
        com.stripe.model.Refund fakeRefund = new com.stripe.model.Refund();
        fakeRefund.setId("re_cents");

        try (MockedStatic<com.stripe.model.Refund> refundStatic =
                     mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                            argThat((com.stripe.param.RefundCreateParams p) ->
                                    p.getAmount() == 5000L))) // 50.00 * 100
                    .thenReturn(fakeRefund);

            com.stripe.model.Refund result =
                    stripeService.createRefund("pi_intent", new BigDecimal("50.00"));

            assertThat(result).isNotNull();
        }
    }

    @Test
    void createRefund_ShouldThrowAuthException_WhenStripeRefundFails() throws Exception {
        ApiException stripeEx = new ApiException("charge_already_refunded", null,
                "charge_already_refunded", 400, null);

        try (MockedStatic<com.stripe.model.Refund> refundStatic =
                     mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                            any(com.stripe.param.RefundCreateParams.class)))
                    .thenThrow(stripeEx);

            assertThatThrownBy(() ->
                    stripeService.createRefund("pi_intent", new BigDecimal("50.00")))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Stripe refund failed");
        }
    }

    @Test
    void createRefund_ShouldIssueFullRefund_WhenAmountIsNull() throws Exception {
        com.stripe.model.Refund fakeRefund = new com.stripe.model.Refund();
        fakeRefund.setId("re_full");

        try (MockedStatic<com.stripe.model.Refund> refundStatic =
                     mockStatic(com.stripe.model.Refund.class)) {
            refundStatic.when(() -> com.stripe.model.Refund.create(
                            argThat((com.stripe.param.RefundCreateParams p) ->
                                    p.getAmount() == null)))   // full refund — no amount
                    .thenReturn(fakeRefund);

            com.stripe.model.Refund result = stripeService.createRefund("pi_intent", null);
            assertThat(result.getId()).isEqualTo("re_full");
        }
    }
}
