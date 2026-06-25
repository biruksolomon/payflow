package com.payflow.backend.service;

import com.payflow.backend.config.StripeConfig;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.dto.request.CreatePaymentIntentRequest;
import com.payflow.backend.dto.response.CreatePaymentIntentResponse;
import com.payflow.backend.exception.AuthException;
import com.stripe.exception.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
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
 * The Stripe SDK makes real HTTP calls via static factory methods (PaymentIntent.create,
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
    private CreatePaymentIntentRequest createRequest;

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

        createRequest = CreatePaymentIntentRequest.builder()
                .orderId(5L)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE PAYMENT INTENT
    // ─────────────────────────────────────────────────────────────

    @Test
    void createPaymentIntent_ShouldReturnResponseWithClientSecret() throws Exception {
        // Arrange — stub PaymentService
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(paymentService.updateStripeIntentId(10L, "pi_test123"))
                .thenReturn(pendingPayment);

        // Build a fake PaymentIntent returned by Stripe SDK
        PaymentIntent fakeIntent = new PaymentIntent();
        fakeIntent.setId("pi_test123");
        fakeIntent.setClientSecret("pi_test123_secret_xyz");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(com.stripe.param.PaymentIntentCreateParams.class)))
                    .thenReturn(fakeIntent);

            // Act
            CreatePaymentIntentResponse response = stripeService.createPaymentIntent(createRequest, 1L);

            // Assert
            assertThat(response).isNotNull();
            assertThat(response.getClientSecret()).isEqualTo("pi_test123_secret_xyz");
            assertThat(response.getPaymentIntentId()).isEqualTo("pi_test123");
            assertThat(response.getPaymentId()).isEqualTo(10L);
            assertThat(response.getAmount()).isEqualByComparingTo(new BigDecimal("150.00"));
            assertThat(response.getCurrency()).isEqualTo("USD");
        }

        verify(paymentService).initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null);
        verify(paymentService).updateStripeIntentId(10L, "pi_test123");
    }

    @Test
    void createPaymentIntent_ShouldMarkPaymentFailed_WhenStripeThrows() throws Exception {
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);

        ApiException stripeEx = new ApiException("card_declined", null, "card_declined", 402, null);

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(any(com.stripe.param.PaymentIntentCreateParams.class)))
                    .thenThrow(stripeEx);

            assertThatThrownBy(() -> stripeService.createPaymentIntent(createRequest, 1L))
                    .isInstanceOf(AuthException.class)
                    .hasMessageContaining("Stripe payment intent creation failed");
        }

        // The pending payment must be marked FAILED so the order is not left in limbo
        verify(paymentService).recordFailedPayment(eq(10L), any(), any());
    }

    @Test
    void createPaymentIntent_AmountConvertedToCents() throws Exception {
        // Verify 150.00 USD → 15000 cents is passed to Stripe
        when(paymentService.initiatePayment(5L, 1L, PaymentMethod.STRIPE, null, null, null))
                .thenReturn(pendingPayment);
        when(paymentService.updateStripeIntentId(anyLong(), anyString()))
                .thenReturn(pendingPayment);

        PaymentIntent fakeIntent = new PaymentIntent();
        fakeIntent.setId("pi_cents");
        fakeIntent.setClientSecret("pi_cents_secret");

        try (MockedStatic<PaymentIntent> piStatic = mockStatic(PaymentIntent.class)) {
            piStatic.when(() -> PaymentIntent.create(
                            argThat((com.stripe.param.PaymentIntentCreateParams p) ->
                                    p.getAmount() == 15000L)))   // 150.00 * 100 = 15000
                    .thenReturn(fakeIntent);

            CreatePaymentIntentResponse response = stripeService.createPaymentIntent(createRequest, 1L);
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
    // HANDLE WEBHOOK — payment_intent.succeeded
    // ─────────────────────────────────────────────────────────────

    @Test
    void handleWebhookEvent_ShouldCallRecordSuccess_OnPaymentIntentSucceeded() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        // Build a fake Event with payment_intent.succeeded
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_succeeded");
        intent.setMetadata(Map.of("paymentId", "42"));

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
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
    void handleWebhookEvent_ShouldSkip_WhenSucceededEventMissingPaymentIdMeta() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        // If metadata has no paymentId, the handler should log a warning and return — not throw
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_no_meta");
        intent.setMetadata(Map.of()); // no paymentId

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.succeeded");
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
    // HANDLE WEBHOOK — payment_intent.payment_failed
    // ─────────────────────────────────────────────────────────────

   /* @Test
    void handleWebhookEvent_ShouldCallRecordFailed_OnPaymentIntentFailed() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        PaymentIntent.LastPaymentError lastError = new PaymentIntent.LastPaymentError();
        lastError.setCode("card_declined");
        lastError.setMessage("Your card was declined");

        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_failed");
        intent.setMetadata(Map.of("paymentId", "77"));
        intent.setLastPaymentError(lastError);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.payment_failed");
        when(event.getId()).thenReturn("evt_003");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");
        }

        verify(paymentService).recordFailedPayment(77L, "card_declined", "Your card was declined");
    }*/

    @Test
    void handleWebhookEvent_ShouldUseFallbackErrorCode_WhenLastPaymentErrorNull() throws Exception {
        when(stripeConfig.getWebhookSecret()).thenReturn("whsec_test");
        PaymentIntent intent = new PaymentIntent();
        intent.setId("pi_failed_no_err");
        intent.setMetadata(Map.of("paymentId", "88"));
        intent.setLastPaymentError(null);

        EventDataObjectDeserializer deserializer = mock(EventDataObjectDeserializer.class);
        when(deserializer.getObject()).thenReturn(Optional.of(intent));

        Event event = mock(Event.class);
        when(event.getType()).thenReturn("payment_intent.payment_failed");
        when(event.getId()).thenReturn("evt_004");
        when(event.getDataObjectDeserializer()).thenReturn(deserializer);

        try (MockedStatic<Webhook> webhookStatic = mockStatic(Webhook.class)) {
            webhookStatic.when(() -> Webhook.constructEvent(anyString(), anyString(), anyString()))
                    .thenReturn(event);

            stripeService.handleWebhookEvent("payload", "sig");
        }

        verify(paymentService).recordFailedPayment(88L, "PAYMENT_FAILED", "Payment failed via Stripe");
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

            // Should not throw; just silently ignore
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
        // When amount is null, no amount field should be set in RefundCreateParams
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
