package com.payflow.backend.config;

import com.stripe.Stripe;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that StripeConfig:
 *  - binds all three properties from application.yml / test overrides
 *  - sets Stripe.apiKey globally via @PostConstruct
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.stripe.api-key=sk_test_testApiKey",
        "app.stripe.public-key=pk_test_testPublicKey",
        "app.stripe.webhook-secret=whsec_testWebhookSecret"
})
class StripeConfigTest {

    @Autowired
    private StripeConfig stripeConfig;

    // ─────────────────────────────────────────────────────────────
    // PROPERTY BINDING
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldBindApiKey() {
        assertThat(stripeConfig.getApiKey()).isEqualTo("sk_test_testApiKey");
    }

    @Test
    void shouldBindPublicKey() {
        assertThat(stripeConfig.getPublicKey()).isEqualTo("pk_test_testPublicKey");
    }

    @Test
    void shouldBindWebhookSecret() {
        assertThat(stripeConfig.getWebhookSecret()).isEqualTo("whsec_testWebhookSecret");
    }

    // ─────────────────────────────────────────────────────────────
    // POSTCONSTRUCT — global SDK initialisation
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldSetStripeGlobalApiKeyOnStartup() {
        // @PostConstruct runs before any test; Stripe.apiKey must equal the injected key
        assertThat(Stripe.apiKey).isEqualTo("sk_test_testApiKey");
    }
}
